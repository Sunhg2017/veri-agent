package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.application.view.ReportingWebhookDeliveryHealthResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReportingWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReportingWebhookDispatcher.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_ALGORITHM = "HMAC-SHA256";
    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;

    private final ReportingRepository repository;
    private final ReportingPlatformContextClient contextClient;
    private final ReportingProperties properties;
    private final ReportingJsonSupport jsonSupport;
    private final List<SecretProvider> secretProviders;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ReportingWebhookDispatcher(
            ReportingRepository repository,
            ReportingPlatformContextClient contextClient,
            ReportingProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<SecretProvider> secretProviders
    ) {
        this(
                repository,
                contextClient,
                properties,
                objectMapper,
                secretProviders.orderedStream().toList(),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.effectiveWebhookDeliveryTimeoutMs()))
                        .build()
        );
    }

    ReportingWebhookDispatcher(
            ReportingRepository repository,
            ReportingPlatformContextClient contextClient,
            ReportingProperties properties,
            ObjectMapper objectMapper,
            List<SecretProvider> secretProviders,
            HttpClient httpClient
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jsonSupport = new ReportingJsonSupport(objectMapper);
        this.secretProviders = secretProviders == null ? List.of() : List.copyOf(secretProviders);
        this.httpClient = httpClient;
    }

    /**
     * Delivers one aggregate-only report completion callback without affecting the report transaction outcome.
     */
    public void dispatch(UUID reportId, String terminalStatus) {
        if (!properties.webhookDeliveryEnabled()) {
            return;
        }
        ReportExecutionReport report = repository.report(reportId).orElse(null);
        if (report == null) {
            return;
        }
        String callbackUrl = properties.effectiveWebhookDeliveryUrl();
        URI callbackUri = validCallbackUri(callbackUrl);
        if (callbackUri == null) {
            recordDeliveryFailure(report, terminalStatus, "report webhook callback url invalid");
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(callbackPayload(report, terminalStatus));
            HttpRequest.Builder builder = HttpRequest.newBuilder(callbackUri)
                    .timeout(Duration.ofMillis(properties.effectiveWebhookDeliveryTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Trace-Id", TraceContext.getOrCreateTraceId())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            applySignatureHeaders(builder, payload, report.projectId(), report.id());
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "REPORT_WEBHOOK_DELIVERY_HTTP_" + statusCode
                );
            }
            contextClient.writeAuditEvent(
                    "report.webhook.delivered",
                    "REPORT_EXECUTION_REPORT",
                    report.id().toString(),
                    report.projectId(),
                    "SUCCESS",
                    Map.of(
                            "status", report.status(),
                            "terminalStatus", boundedStatus(terminalStatus),
                            "callbackUrlDigest", SensitiveTextSanitizer.sha256Hex(callbackUrl),
                            "signatureEnabled", properties.webhookDeliverySignatureEnabled()
                    )
            );
        } catch (BusinessException exception) {
            recordDeliveryFailure(report, terminalStatus, exception.getMessage());
        } catch (IOException exception) {
            recordDeliveryFailure(report, terminalStatus, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordDeliveryFailure(report, terminalStatus, "webhook delivery interrupted");
        } catch (RuntimeException exception) {
            recordDeliveryFailure(report, terminalStatus, exception.getMessage());
        }
    }

    public ReportingWebhookDeliveryHealthResponse health() {
        String url = properties.effectiveWebhookDeliveryUrl();
        return new ReportingWebhookDeliveryHealthResponse(
                properties.webhookDeliveryEnabled(),
                StringUtils.hasText(url),
                safeDisplayUrl(url),
                properties.webhookDeliverySignatureEnabled(),
                StringUtils.hasText(properties.effectiveWebhookDeliverySecretRef()),
                secretRefDigest(properties.effectiveWebhookDeliverySecretRef()),
                properties.effectiveWebhookDeliveryTimeoutMs()
        );
    }

    private Map<String, Object> callbackPayload(ReportExecutionReport report, String terminalStatus) {
        Map<String, Object> summary = new LinkedHashMap<>(jsonSupport.readMap(report.reportSummaryJson()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "REPORT_GENERATION_COMPLETED");
        payload.put("occurredAt", Instant.now());
        payload.put("traceId", TraceContext.getOrCreateTraceId());
        payload.put("reportId", report.id());
        payload.put("projectId", report.projectId());
        payload.put("executionRunId", report.executionRunId());
        payload.put("status", report.status());
        payload.put("terminalStatus", boundedStatus(terminalStatus));
        payload.put("schemaVersion", report.schemaVersion());
        payload.put("sourceRunDigest", report.sourceRunDigest());
        payload.put("summary", summary);
        payload.put("generatedAt", report.generatedAt());
        payload.put("redactionPolicy", Map.of(
                "aggregateOnly", true,
                "rawEvidenceIncluded", false,
                "secretPlaintextIncluded", false,
                "requestResponseBodyIncluded", false
        ));
        repository.latestFailureDiagnosis(report.id())
                .ifPresent(diagnosis -> payload.put("latestDiagnosis", diagnosisPayload(diagnosis)));
        return payload;
    }

    private Map<String, Object> diagnosisPayload(ReportFailureDiagnosis diagnosis) {
        Map<String, Object> summary = jsonSupport.readMap(diagnosis.diagnosisSummaryJson());
        return Map.of(
                "status", diagnosis.status(),
                "confidence", diagnosis.confidence(),
                "manualReviewRequired", diagnosis.manualReviewRequired(),
                "primaryCategory", jsonSupport.readMap(diagnosis.classificationJson())
                        .getOrDefault("primaryCategory", "UNKNOWN"),
                "modelInvocationDigest", diagnosis.modelInvocationDigest() == null ? "" : diagnosis.modelInvocationDigest(),
                "aiDiagnosisReady", summary.getOrDefault("aiDiagnosisReady", false)
        );
    }

    private void applySignatureHeaders(
            HttpRequest.Builder builder,
            String payload,
            String projectId,
            UUID reportId
    ) {
        if (!properties.webhookDeliverySignatureEnabled()) {
            return;
        }
        String secretRef = properties.effectiveWebhookDeliverySecretRef();
        if (!StringUtils.hasText(secretRef)) {
            throw new BusinessException(ErrorCode.SECRET_REQUIRED, "REPORT_WEBHOOK_SECRET_REQUIRED");
        }
        String secret = resolveSecret(secretRef, projectId, reportId);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = hmacSha256(String.join(".", timestamp, reportId.toString(), payload), secret);
        builder.header("X-VA-Timestamp", timestamp)
                .header("X-VA-Event-Id", reportId.toString())
                .header("X-VA-Signature-Algorithm", SIGNATURE_ALGORITHM)
                .header("X-VA-Signature", signature);
    }

    private String resolveSecret(String secretRef, String projectId, UUID reportId) {
        SecretResolveContext context = new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp10-reporting-service",
                "PROJECT",
                projectId
        );
        for (SecretProvider provider : secretProviders) {
            Optional<ResolvedSecret> resolved = provider.resolve(secretRef, context);
            if (resolved.isPresent() && StringUtils.hasText(resolved.get().value())) {
                return resolved.get().value();
            }
        }
        throw new BusinessException(
                ErrorCode.SECRET_PROVIDER_ERROR,
                "REPORT_WEBHOOK_SECRET_UNRESOLVED:" + reportId
        );
    }

    private void recordDeliveryFailure(ReportExecutionReport report, String terminalStatus, String reason) {
        String sanitized = SensitiveTextSanitizer.sanitizedErrorSummary(
                reason,
                "report webhook delivery failed",
                MAX_ERROR_SUMMARY_LENGTH
        );
        log.warn(
                "WP10 report webhook delivery failed, reportId={}, projectId={}, terminalStatus={}, error={}",
                report.id(),
                report.projectId(),
                boundedStatus(terminalStatus),
                sanitized
        );
        contextClient.writeAuditEvent(
                "report.webhook.delivered",
                "REPORT_EXECUTION_REPORT",
                report.id().toString(),
                report.projectId(),
                "FAILED",
                Map.of(
                        "status", report.status(),
                        "terminalStatus", boundedStatus(terminalStatus),
                        "callbackUrlDigest", SensitiveTextSanitizer.sha256Hex(properties.effectiveWebhookDeliveryUrl()),
                        "signatureEnabled", properties.webhookDeliverySignatureEnabled(),
                        "failureReason", sanitized
                )
        );
    }

    private String boundedStatus(String status) {
        return SensitiveTextSanitizer.boundedNullableText(status, 32);
    }

    private String safeDisplayUrl(String rawUrl) {
        URI uri = validCallbackUri(rawUrl);
        if (uri == null) {
            return null;
        }
        StringBuilder display = new StringBuilder(uri.getScheme().toLowerCase())
                .append("://")
                .append(uri.getHost());
        if (uri.getPort() >= 0) {
            display.append(':').append(uri.getPort());
        }
        if (StringUtils.hasText(uri.getRawPath())) {
            display.append(uri.getRawPath());
        }
        return display.toString();
    }

    private String secretRefDigest(String secretRef) {
        return StringUtils.hasText(secretRef) ? "sha256:" + SensitiveTextSanitizer.sha256Hex(secretRef.trim()) : null;
    }

    private URI validCallbackUri(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            String normalizedScheme = scheme.trim().toLowerCase();
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "REPORT_WEBHOOK_SIGNATURE_FAILED");
        }
    }
}
