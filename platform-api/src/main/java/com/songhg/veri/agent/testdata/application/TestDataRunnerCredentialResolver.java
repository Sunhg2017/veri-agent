package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.LocalSecretCipher;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.domain.TestAccountLease;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDataRunnerCredentialResolver {

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };
    private static final String RUNNER_SECRET_PURPOSE = "UI_E2E_RUNNER";
    private static final String RUNNER_SECRET_CALLER_SERVICE = "wp7-ui-e2e-runner";
    private static final String RUNNER_SECRET_SCOPE_TYPE = "PROJECT";
    private static final int RUNNER_SECRET_VALUE_MAX_CHARS = 8_192;

    private final TestDataRepository repository;
    private final List<SecretProvider> secretProviders;
    private final SecretProviderProperties secretProviderProperties;
    private final ObjectMapper objectMapper;

    public TestDataRunnerCredentialResolver(
            TestDataRepository repository,
            List<SecretProvider> secretProviders,
            SecretProviderProperties secretProviderProperties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.secretProviders = secretProviders == null ? List.of() : List.copyOf(secretProviders);
        this.secretProviderProperties = secretProviderProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves runner credentials only inside the trusted WP8/WP7 backend path. Control-plane callers still receive
     * digest-only account contracts; plaintext never leaves this service boundary.
     */
    @Transactional(readOnly = true)
    public RunnerCredentialResolution resolveForUiE2e(UUID accountLeaseRef, String projectId) {
        TestAccountLease lease = repository.accountLease(accountLeaseRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID"));
        if (!projectId.equals(lease.projectId())
                || !"ACTIVE".equals(lease.status())
                || lease.expiresAt() == null
                || !lease.expiresAt().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID");
        }
        TestPooledAccount account = repository.pooledAccount(lease.accountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID"));
        String cipherPayload = repository.pooledAccountSecretRefCipher(account.id())
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                        "runner secretRef 未解析: sha256:" + account.secretRefDigest()));
        String secretRef = decryptSecretRef(cipherPayload, account.secretRefDigest());
        ResolvedSecret resolvedSecret = resolveSecretValue(secretRef, account.secretRefDigest(), projectId);
        validateRunnerSecretValue(resolvedSecret.value(), account.secretRefDigest());
        return new RunnerCredentialResolution(
                lease.id(),
                account.id(),
                account.secretRefDigest(),
                resolvedSecret.provider(),
                resolvedSecret.version(),
                resolvedSecret.value()
        );
    }

    @Transactional(readOnly = true)
    public boolean credentialInjectionReady() {
        return !secretProviders.isEmpty() && StringUtils.hasText(secretProviderProperties.localMasterKey());
    }

    private String decryptSecretRef(String cipherPayload, String digest) {
        Map<String, String> payload;
        try {
            payload = objectMapper.readValue(cipherPayload, STRING_MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 解析失败: sha256:" + digest);
        }
        try {
            String secretRef = LocalSecretCipher.decrypt(
                    payload.get("cipherText"),
                    payload.get("iv"),
                    payload.get("authTag"),
                    payload.get("algorithm"),
                    payload.get("masterKeyVersion"),
                    secretProviderProperties,
                    "<runner-secret-ref>"
            );
            if (!StringUtils.hasText(secretRef)) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 解析为空: sha256:" + digest);
            }
            return secretRef.trim();
        } catch (BusinessException exception) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 解析失败: sha256:" + digest);
        }
    }

    private ResolvedSecret resolveSecretValue(String secretRef, String digest, String projectId) {
        if (secretProviders.isEmpty()) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 未解析: sha256:" + digest);
        }
        SecretResolveContext context = new SecretResolveContext(
                RUNNER_SECRET_PURPOSE,
                RUNNER_SECRET_CALLER_SERVICE,
                RUNNER_SECRET_SCOPE_TYPE,
                projectId
        );
        for (SecretProvider provider : secretProviders) {
            try {
                Optional<ResolvedSecret> resolved = provider.resolve(secretRef, context);
                if (resolved.isPresent()) {
                    return resolved.get();
                }
            } catch (BusinessException exception) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 解析失败: sha256:" + digest);
            }
        }
        throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 未解析: sha256:" + digest);
    }

    private void validateRunnerSecretValue(String value, String digest) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 解析为空: sha256:" + digest);
        }
        if (value.length() > RUNNER_SECRET_VALUE_MAX_CHARS || value.contains("\r") || value.contains("\n")) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 值不适合注入: sha256:" + digest);
        }
    }

    public record RunnerCredentialResolution(
            UUID accountLeaseRef,
            UUID accountRef,
            String secretRefDigest,
            String provider,
            String version,
            String secretValue
    ) {
        @Override
        public String toString() {
            return "RunnerCredentialResolution[accountLeaseRef=%s, accountRef=%s, secretRefDigest=%s, provider=%s, version=%s, secretValue=****]"
                    .formatted(
                            accountLeaseRef,
                            accountRef,
                            SensitiveTextSanitizer.boundedNullableText(secretRefDigest, 80),
                            provider,
                            version
                    );
        }
    }
}
