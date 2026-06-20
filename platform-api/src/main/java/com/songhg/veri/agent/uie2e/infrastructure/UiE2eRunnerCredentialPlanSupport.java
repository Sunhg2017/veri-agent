package com.songhg.veri.agent.uie2e.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.TestDataRunnerCredentialResolver;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Shared runner-only credential planning helper used by preview and real browser execution adapters.
 */
final class UiE2eRunnerCredentialPlanSupport {

    static final String UNSUPPORTED_CREDENTIAL_FORMAT = "UI_E2E_CREDENTIAL_FORMAT_UNSUPPORTED";
    static final String ACCOUNT_PASSWORD_SCHEMA = "wp7-account-password-v1";
    static final String LOGIN_FORM_SCHEMA = "wp7-login-form-v1";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    UiE2eRunnerCredentialPlanSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    RunnerCredentialInjectionPlan buildCredentialPlan(
            UiE2eRunnerPort.RunnerRunRequest request,
            TestDataRunnerCredentialResolver.RunnerCredentialResolution resolution
    ) {
        String secretValue = resolution == null ? null : resolution.secretValue();
        if (!StringUtils.hasText(secretValue)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        String trimmedSecret = secretValue.trim();
        if (trimmedSecret.startsWith("{")) {
            return structuredCredentialPlan(trimmedSecret);
        }
        String accountKey = safeText(request == null || request.accountSummary() == null
                ? null
                : request.accountSummary().get("accountKey"));
        if (!StringUtils.hasText(accountKey)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        return new RunnerCredentialInjectionPlan(
                "FORM_LOGIN",
                "ACCOUNT_PASSWORD",
                ACCOUNT_PASSWORD_SCHEMA,
                "ACCOUNT_SUMMARY",
                true,
                2,
                accountKey,
                trimmedSecret
        );
    }

    private RunnerCredentialInjectionPlan structuredCredentialPlan(String secretValue) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(secretValue, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        String schemaId = safeText(payload.get("schema"));
        String normalizedSchema = StringUtils.hasText(schemaId) ? schemaId.trim() : LOGIN_FORM_SCHEMA;
        if (!LOGIN_FORM_SCHEMA.equalsIgnoreCase(normalizedSchema)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        String principal = firstPresent(payload, "username", "principal", "accountKey");
        String credential = firstPresent(payload, "password", "credential", "value");
        if (!StringUtils.hasText(principal) || !StringUtils.hasText(credential)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UNSUPPORTED_CREDENTIAL_FORMAT);
        }
        return new RunnerCredentialInjectionPlan(
                "FORM_LOGIN",
                "STRUCTURED_LOGIN_FORM",
                LOGIN_FORM_SCHEMA,
                "SECRET_PAYLOAD",
                true,
                2,
                principal,
                credential
        );
    }

    private String firstPresent(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String text = safeText(payload.get(key));
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String safeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || text.contains("\r") || text.contains("\n")) {
            return null;
        }
        return SensitiveTextSanitizer.boundedText(text, 512);
    }

    record RunnerCredentialInjectionPlan(
            String planType,
            String format,
            String schemaId,
            String principalSource,
            boolean principalIdentifierPresent,
            int componentCount,
            String principalIdentifier,
            String credentialValue
    ) {
        @Override
        public String toString() {
            return "RunnerCredentialInjectionPlan[planType=%s, format=%s, schemaId=%s, principalSource=%s, componentCount=%s, principalIdentifier=****, credentialValue=****]"
                    .formatted(planType, format, schemaId, principalSource, componentCount);
        }
    }
}
