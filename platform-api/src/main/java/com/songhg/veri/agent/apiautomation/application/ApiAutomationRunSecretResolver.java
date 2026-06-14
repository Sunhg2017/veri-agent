package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Keeps runner secret references as runtime-only data and exposes only digests or bounded headers to callers.
 */
final class ApiAutomationRunSecretResolver {

    static final String RUNNER_SECRET_HEADER_PREFIX = "X-VA-WP6-Secret-";

    private static final int RUNNER_SECRET_REF_MAX_COUNT = 10;
    private static final int RUNNER_SECRET_REF_MAX_CHARS = 256;
    private static final int RUNNER_SECRET_VALUE_MAX_CHARS = 8_192;
    private static final String RUNNER_SECRET_PURPOSE = "API_AUTOMATION_RUNNER";
    private static final String RUNNER_SECRET_CALLER_SERVICE = "wp6-api-automation-runner";
    private static final String RUNNER_SECRET_SCOPE_TYPE = "PROJECT";
    private static final Pattern SECRET_REF_PATTERN =
            Pattern.compile("^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$");

    private final List<SecretProvider> secretProviders;

    ApiAutomationRunSecretResolver(List<SecretProvider> secretProviders) {
        this.secretProviders = secretProviders == null ? List.of() : List.copyOf(secretProviders);
    }

    /**
     * Full secretRef values stay in memory only; audit and persistence receive deterministic digests instead.
     */
    RunSecretRefs validateRunSecretRefs(List<String> rawSecretRefs) {
        if (rawSecretRefs == null || rawSecretRefs.isEmpty()) {
            return new RunSecretRefs(0, List.of(), List.of());
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawSecretRef : rawSecretRefs) {
            String secretRef = rawSecretRef == null ? null : rawSecretRef.trim();
            if (!StringUtils.hasText(secretRef)) {
                continue;
            }
            if (secretRef.length() > RUNNER_SECRET_REF_MAX_CHARS) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "secretRefs 单个引用最多 " + RUNNER_SECRET_REF_MAX_CHARS + " 字符"
                );
            }
            if (!SECRET_REF_PATTERN.matcher(secretRef).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "secretRefs 必须使用 secret:// 引用");
            }
            normalized.add(secretRef);
        }
        if (normalized.size() > RUNNER_SECRET_REF_MAX_COUNT) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "secretRefs 单次运行最多 " + RUNNER_SECRET_REF_MAX_COUNT + " 个"
            );
        }
        return new RunSecretRefs(
                normalized.size(),
                List.copyOf(normalized),
                normalized.stream().map(secretRef -> "sha256:" + SensitiveTextSanitizer.sha256Hex(secretRef)).toList()
        );
    }

    /**
     * Resolves plaintext only after runner admission succeeds, then maps values to controlled per-run headers.
     */
    List<ApiAutomationRunnerPort.RunnerSecret> resolveRunSecrets(RunSecretRefs secretRefs, String projectId) {
        if (secretRefs == null || secretRefs.refs().isEmpty()) {
            return List.of();
        }
        if (secretProviders.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.SECRET_PROVIDER_ERROR,
                    "runner secretRef 未解析: " + String.join(",", secretRefs.digests())
            );
        }
        List<ApiAutomationRunnerPort.RunnerSecret> secrets = new ArrayList<>();
        SecretResolveContext context = new SecretResolveContext(
                RUNNER_SECRET_PURPOSE,
                RUNNER_SECRET_CALLER_SERVICE,
                RUNNER_SECRET_SCOPE_TYPE,
                projectId
        );
        for (int index = 0; index < secretRefs.refs().size(); index++) {
            String secretRef = secretRefs.refs().get(index);
            String digest = secretRefs.digests().get(index);
            ResolvedSecret resolved = resolveRunSecret(secretRef, digest, context);
            validateRunnerSecretValue(resolved.value(), digest);
            secrets.add(new ApiAutomationRunnerPort.RunnerSecret(
                    RUNNER_SECRET_HEADER_PREFIX + (index + 1),
                    digest,
                    resolved.value()
            ));
        }
        return secrets;
    }

    private ResolvedSecret resolveRunSecret(String secretRef, String digest, SecretResolveContext context) {
        for (SecretProvider provider : secretProviders) {
            try {
                Optional<ResolvedSecret> resolved = provider.resolve(secretRef, context);
                if (resolved.isPresent()) {
                    return resolved.get();
                }
            } catch (BusinessException exception) {
                throw new BusinessException(
                        ErrorCode.SECRET_PROVIDER_ERROR,
                        "runner secretRef 解析失败: " + digest
                );
            }
        }
        throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 未解析: " + digest);
    }

    private void validateRunnerSecretValue(String value, String digest) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 解析为空: " + digest);
        }
        if (value.length() > RUNNER_SECRET_VALUE_MAX_CHARS || value.contains("\r") || value.contains("\n")) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 值不适合注入: " + digest);
        }
    }

    record RunSecretRefs(
            int count,
            List<String> refs,
            List<String> digests
    ) {
    }
}
