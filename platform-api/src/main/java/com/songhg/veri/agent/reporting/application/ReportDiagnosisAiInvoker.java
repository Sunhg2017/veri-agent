package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * Calls WP2 for failure diagnosis and converts all outcomes to WP10-safe digest-only metadata.
 */
final class ReportDiagnosisAiInvoker {

    static final String MODEL_CALLER_SERVICE = "wp10-reporting";
    static final String MODEL_CAPABILITY_JSON = "JSON";
    static final String ERROR_POLICY_BLOCKED = "REPORT_DIAGNOSIS_POLICY_BLOCKED";

    private final ModelInvocationService modelInvocationService;
    private final ReportingActorResolver actorResolver;

    ReportDiagnosisAiInvoker(
            ObjectProvider<ModelInvocationService> modelInvocationServices,
            ReportingActorResolver actorResolver
    ) {
        this.modelInvocationService = modelInvocationServices.getIfAvailable();
        this.actorResolver = actorResolver;
    }

    DiagnosisInvocationOutcome invoke(
            ReportExecutionReport report,
            ReportDiagnosisContextBuilder.DiagnosisContext context
    ) {
        if (modelInvocationService == null) {
            return failed(
                    null,
                    "MODEL_INVOCATION_SERVICE_UNAVAILABLE",
                    "WP2 model invocation service is unavailable; rule classification remains available."
            );
        }
        try {
            ModelInvocationResult response = modelInvocationService.invoke(command(report, context), principal());
            if (response == null || response.invocationId() == null) {
                return failed(null, "MODEL_INVOCATION_EMPTY", "WP2 diagnosis invocation returned no traceable result.");
            }
            return ready(response);
        } catch (BusinessException exception) {
            return failed(null, exception.getErrorCode().name(), "WP2 policy, budget, sensitivity or provider gate blocked diagnosis.");
        } catch (RuntimeException exception) {
            return failed(null, "MODEL_INVOCATION_FAILED", "WP2 diagnosis invocation failed; rule classification remains available.");
        }
    }

    private ModelInvocationCommand command(
            ReportExecutionReport report,
            ReportDiagnosisContextBuilder.DiagnosisContext context
    ) {
        return new ModelInvocationCommand(
                report.projectId(),
                null,
                null,
                null,
                Map.of(),
                List.of(new ChatMessage("user", context.boundedContext())),
                null,
                null,
                false,
                "INTERNAL",
                MODEL_CAPABILITY_JSON
        );
    }

    private ServicePrincipal principal() {
        return new ServicePrincipal(MODEL_CALLER_SERVICE, actorResolver.currentActor());
    }

    private DiagnosisInvocationOutcome ready(ModelInvocationResult response) {
        String content = response == null ? "" : response.content();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerName", response == null ? null : safeText(response.providerName(), 96));
        metadata.put("modelName", response == null ? null : safeText(response.modelName(), 96));
        metadata.put("fallbackUsed", response != null && response.fallbackUsed());
        metadata.put("inputTokens", response == null ? 0 : response.inputTokens());
        metadata.put("outputTokens", response == null ? 0 : response.outputTokens());
        metadata.put("outputDigest", SensitiveTextSanitizer.sha256Hex(content));
        metadata.put("outputStored", false);
        metadata.put("rawResponseStored", false);
        metadata.put("rawPromptStored", false);
        return new DiagnosisInvocationOutcome(
                true,
                response == null ? null : SensitiveTextSanitizer.sha256Hex(response.invocationId()),
                metadata,
                null,
                null,
                null
        );
    }

    private DiagnosisInvocationOutcome failed(String invocationDigest, String wp2ErrorCode, String reason) {
        return new DiagnosisInvocationOutcome(
                false,
                invocationDigest,
                Map.of(),
                ERROR_POLICY_BLOCKED,
                safeText(wp2ErrorCode, 64),
                reason
        );
    }

    private String safeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return SensitiveTextSanitizer.sanitizedEvidenceText(value, maxLength);
    }

    record DiagnosisInvocationOutcome(
            boolean ready,
            String modelInvocationDigest,
            Map<String, Object> modelMetadata,
            String errorCode,
            String wp2ErrorCode,
            String failureReason
    ) {
    }
}
