package com.songhg.veri.agent.common.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("db")
@Order(20)
public class ExternalSecretProvider implements SecretProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_ALGORITHM_HEADER = "X-VA-Secret-Signature-Algorithm";
    private static final String SIGNATURE_KEY_ID_HEADER = "X-VA-Secret-Key-Id";
    private static final String SIGNATURE_TIMESTAMP_HEADER = "X-VA-Secret-Timestamp";
    private static final String SIGNATURE_NONCE_HEADER = "X-VA-Secret-Nonce";
    private static final String SIGNATURE_HEADER = "X-VA-Secret-Signature";
    private static final String DEFAULT_SIGNING_KEY_ID = "external-vault-kms";

    private final JdbcTemplate jdbcTemplate;
    private final SecretProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SecretProviderAuditRecorder auditRecorder;

    @Autowired
    public ExternalSecretProvider(
            JdbcTemplate jdbcTemplate,
            SecretProviderProperties properties,
            ObjectMapper objectMapper,
            SecretProviderAuditRecorder auditRecorder
    ) {
        this(jdbcTemplate, properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(resolveTimeout(properties))
                .build(), auditRecorder);
    }

    ExternalSecretProvider(
            JdbcTemplate jdbcTemplate,
            SecretProviderProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this(jdbcTemplate, properties, objectMapper, httpClient, SecretProviderAuditRecorder.noop());
    }

    ExternalSecretProvider(
            JdbcTemplate jdbcTemplate,
            SecretProviderProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            SecretProviderAuditRecorder auditRecorder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.auditRecorder = auditRecorder == null ? SecretProviderAuditRecorder.noop() : auditRecorder;
    }

    @Override
    public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
        if (!StringUtils.hasText(secretRef)) {
            return Optional.empty();
        }
        ExternalSecretRow row = findExternalSecret(secretRef.trim()).orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        SecretProviderAuditRecorder.Target auditTarget = auditTarget(row);
        try {
            validateContext(row, context);
            if (!StringUtils.hasText(properties.externalResolveUrl())) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "外部 Vault/KMS resolve endpoint 未配置");
            }
            String payload = objectMapper.writeValueAsString(new ResolveRequest(
                    row.secretRef(),
                    row.providerCode(),
                    row.providerType(),
                    row.secretVersion(),
                    context == null ? null : context.purpose(),
                    context == null ? null : context.callerService(),
                    context == null ? null : context.scopeType(),
                    context == null ? null : context.scopeId()
            ));
            URI resolveUri = URI.create(properties.externalResolveUrl().trim());
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolveUri)
                    .timeout(resolveTimeout(properties))
                    .header("Content-Type", "application/json");
            applyAuthenticationHeaders(builder, "POST", resolveUri, payload);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = sendWithRetries(request, "resolve");
            if (response.statusCode() == 404) {
                auditRecorder.recordFailure(auditTarget, context, "外部 Vault/KMS 返回异常状态: 404");
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                        "外部 Vault/KMS 返回异常状态: " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.has("data") && root.path("data").isObject() ? root.path("data") : root;
            String value = text(data, "value", "secret", "plaintext");
            if (!StringUtils.hasText(value)) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "外部 Vault/KMS 响应缺少密钥值");
            }
            ResolvedSecret resolvedSecret = new ResolvedSecret(
                    row.secretRef(),
                    value,
                    firstText(text(data, "provider"), row.providerCode()),
                    firstText(text(data, "version"), row.secretVersion())
            );
            auditRecorder.recordSuccess(auditTarget, context);
            return Optional.of(resolvedSecret);
        } catch (BusinessException exception) {
            auditRecorder.recordFailure(auditTarget, context, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            String sanitized = sanitizeError(exception, row.secretRef());
            auditRecorder.recordFailure(auditTarget, context, "外部 Vault/KMS 密钥解析失败: " + sanitized);
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "外部 Vault/KMS 密钥解析失败: " + sanitized);
        }
    }

    @Override
    public SecretProviderHealth health() {
        if (!StringUtils.hasText(properties.externalResolveUrl())) {
            return SecretProviderHealth.externalDisabled();
        }
        int timeoutSeconds = timeoutSeconds(properties);
        int maxAttempts = maxAttempts(properties);
        Instant checkedAt = Instant.now();
        if (!StringUtils.hasText(properties.externalHealthUrl())) {
            return SecretProviderHealth.externalHealthEndpointNotConfigured(timeoutSeconds, maxAttempts);
        }
        try {
            URI healthUri = URI.create(properties.externalHealthUrl().trim());
            HttpRequest.Builder builder = HttpRequest.newBuilder(healthUri)
                    .timeout(resolveTimeout(properties))
                    .GET();
            applyAuthenticationHeaders(builder, "GET", healthUri, "");
            HttpResponse<String> response = sendWithRetries(builder.build(), "health");
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new SecretProviderHealth(
                        "external-vault-kms",
                        "VAULT_KMS",
                        true,
                        "UP",
                        timeoutSeconds,
                        maxAttempts,
                        checkedAt,
                        null
                );
            }
            return new SecretProviderHealth(
                    "external-vault-kms",
                    "VAULT_KMS",
                    true,
                    "DOWN",
                    timeoutSeconds,
                    maxAttempts,
                    checkedAt,
                    "外部 Vault/KMS 健康检查返回异常状态: " + response.statusCode()
            );
        } catch (Exception exception) {
            return new SecretProviderHealth(
                    "external-vault-kms",
                    "VAULT_KMS",
                    true,
                    "DOWN",
                    timeoutSeconds,
                    maxAttempts,
                    checkedAt,
                    sanitizeError(exception)
            );
        }
    }

    private Optional<ExternalSecretRow> findExternalSecret(String secretRef) {
        return jdbcTemplate.query("""
                select sr.secret_ref,
                       sr.purpose,
                       sr.scope_type,
                       sr.scope_id::text as scope_id,
                       sr.secret_version,
                       sp.provider_code,
                       sp.provider_type
                from secret_reference sr
                join secret_provider sp on sp.id = sr.provider_id
                where sr.secret_ref = ?
                  and sr.status = 'ACTIVE'
                  and (sr.expires_at is null or sr.expires_at > now())
                  and sr.deleted_at is null
                  and sp.status = 'ENABLED'
                  and sp.deleted_at is null
                  and sp.provider_type in ('VAULT','KMS')
                """, (rs, rowNum) -> new ExternalSecretRow(
                rs.getString("secret_ref"),
                rs.getString("purpose"),
                rs.getString("scope_type"),
                rs.getString("scope_id"),
                rs.getString("secret_version"),
                rs.getString("provider_code"),
                rs.getString("provider_type")
        ), secretRef).stream().findFirst();
    }

    private void validateContext(ExternalSecretRow row, SecretResolveContext context) {
        if (context == null) {
            return;
        }
        if (StringUtils.hasText(context.purpose()) && !context.purpose().trim().equalsIgnoreCase(row.purpose())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥用途不匹配");
        }
        if (StringUtils.hasText(context.scopeType()) && !context.scopeType().trim().equalsIgnoreCase(row.scopeType())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域类型不匹配");
        }
        if (StringUtils.hasText(context.scopeId()) && !context.scopeId().trim().equalsIgnoreCase(row.scopeId())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域不匹配");
        }
    }

    private SecretProviderAuditRecorder.Target auditTarget(ExternalSecretRow row) {
        return new SecretProviderAuditRecorder.Target(
                row.secretRef(),
                row.providerCode(),
                row.providerType(),
                row.secretVersion(),
                row.purpose(),
                row.scopeType(),
                row.scopeId()
        );
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static Duration resolveTimeout(SecretProviderProperties properties) {
        return Duration.ofSeconds(timeoutSeconds(properties));
    }

    private HttpResponse<String> sendWithRetries(HttpRequest request, String operation) throws Exception {
        int attempts = maxAttempts(properties);
        Exception lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (isRetryableStatus(response.statusCode()) && attempt < attempts) {
                    continue;
                }
                return response;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (Exception exception) {
                lastException = exception;
                if (attempt >= attempts) {
                    throw exception;
                }
            }
        }
        throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                "外部 Vault/KMS " + operation + " 请求失败: " + sanitizeError(lastException));
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void applyAuthenticationHeaders(HttpRequest.Builder builder, String method, URI uri, String body) throws Exception {
        if (StringUtils.hasText(properties.externalAuthToken())) {
            builder.header("Authorization", "Bearer " + properties.externalAuthToken().trim());
        }
        if (!StringUtils.hasText(properties.externalSigningSecret())) {
            return;
        }
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String keyId = StringUtils.hasText(properties.externalSigningKeyId())
                ? properties.externalSigningKeyId().trim()
                : DEFAULT_SIGNING_KEY_ID;
        String canonical = String.join("\n",
                method.toUpperCase(Locale.ROOT),
                requestTarget(uri),
                timestamp,
                nonce,
                sha256Hex(body == null ? "" : body)
        );
        builder.header(SIGNATURE_ALGORITHM_HEADER, "HMAC-SHA256")
                .header(SIGNATURE_KEY_ID_HEADER, keyId)
                .header(SIGNATURE_TIMESTAMP_HEADER, timestamp)
                .header(SIGNATURE_NONCE_HEADER, nonce)
                .header(SIGNATURE_HEADER, hmacSha256(properties.externalSigningSecret().trim(), canonical));
    }

    private String requestTarget(URI uri) {
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmacSha256(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static int timeoutSeconds(SecretProviderProperties properties) {
        return properties.externalTimeoutSeconds() <= 0 ? 3 : properties.externalTimeoutSeconds();
    }

    private static int maxAttempts(SecretProviderProperties properties) {
        int retries = Math.max(0, properties.externalMaxRetries());
        return retries + 1;
    }

    private String sanitizeError(Exception exception) {
        return sanitizeError(exception, null);
    }

    private String sanitizeError(Exception exception, String secretRef) {
        if (exception == null || !StringUtils.hasText(exception.getMessage())) {
            return "外部 Vault/KMS 请求失败";
        }
        String message = exception.getMessage();
        if (StringUtils.hasText(secretRef)) {
            message = message.replace(secretRef.trim(), "<secret-ref>");
        }
        if (StringUtils.hasText(properties.externalAuthToken())) {
            message = message.replace(properties.externalAuthToken().trim(), "***");
        }
        if (StringUtils.hasText(properties.externalSigningSecret())) {
            message = message.replace(properties.externalSigningSecret().trim(), "***");
        }
        if (StringUtils.hasText(properties.externalResolveUrl())) {
            message = message.replace(properties.externalResolveUrl().trim(), "<external-secret-endpoint>");
        }
        if (StringUtils.hasText(properties.externalHealthUrl())) {
            message = message.replace(properties.externalHealthUrl().trim(), "<external-secret-health>");
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private record ResolveRequest(
            String secretRef,
            String providerCode,
            String providerType,
            String secretVersion,
            String purpose,
            String callerService,
            String scopeType,
            String scopeId
    ) {
    }

    private record ExternalSecretRow(
            String secretRef,
            String purpose,
            String scopeType,
            String scopeId,
            String secretVersion,
            String providerCode,
            String providerType
    ) {
    }
}
