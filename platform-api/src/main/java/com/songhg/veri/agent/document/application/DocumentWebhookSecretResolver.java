package com.songhg.veri.agent.document.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretProviderHealth;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentWebhookSecretResolver {

    public static final String DEFAULT_WEBHOOK_SECRET_REF = "wp4-webhook-default";
    private static final String LOCAL_SECRET_URI_PREFIX = "secret://wp4/";

    private final DocumentInputProperties properties;
    private final List<SecretProvider> secretProviders;
    private final Clock clock;
    private final Map<CacheKey, CachedSecret> cache = new ConcurrentHashMap<>();

    @Autowired
    public DocumentWebhookSecretResolver(DocumentInputProperties properties, ObjectProvider<SecretProvider> secretProviders) {
        this(properties, secretProviders.orderedStream().toList(), Clock.systemUTC());
    }

    DocumentWebhookSecretResolver(DocumentInputProperties properties) {
        this(properties, List.of());
    }

    DocumentWebhookSecretResolver(DocumentInputProperties properties, List<SecretProvider> secretProviders) {
        this(properties, secretProviders, Clock.systemUTC());
    }

    DocumentWebhookSecretResolver(DocumentInputProperties properties, List<SecretProvider> secretProviders, Clock clock) {
        this.properties = properties;
        this.secretProviders = secretProviders == null ? List.of() : List.copyOf(secretProviders);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String resolve(DocumentSourceConfig source) {
        String secretRef = trimToNull(source.secretRef());
        if (source.sourceType() == DocumentSourceType.CUSTOM_API && secretRef == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 密钥引用未配置");
        }
        CacheKey cacheKey = cacheKey(source, secretRef);
        CachedSecret cached = cachedSecret(cacheKey);
        if (cached != null) {
            return cached.value();
        }
        for (SecretProvider provider : secretProviders) {
            Optional<ResolvedSecret> resolved = provider.resolve(secretRef, resolveContext(source));
            if (resolved.isPresent()) {
                cacheSecret(cacheKey, resolved.get());
                return resolved.get().value();
            }
        }
        if (!properties.localWebhookSecretFallbackEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 密钥引用未解析: " + secretRef);
        }
        Map<String, String> configured = properties.webhookSecrets();
        if (configured != null && secretRef != null) {
            String resolved = trimToNull(configured.get(secretRef));
            if (resolved != null) {
                return resolved;
            }
        }
        if (DEFAULT_WEBHOOK_SECRET_REF.equals(secretRef) || secretRef != null && secretRef.startsWith(LOCAL_SECRET_URI_PREFIX)) {
            return defaultWebhookSecret();
        }
        throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 密钥引用未解析: " + secretRef);
    }

    public void invalidate(DocumentSourceConfig source) {
        if (source == null) {
            return;
        }
        invalidate(source.id(), trimToNull(source.secretRef()));
    }

    public void invalidate(UUID sourceId, String secretRef) {
        CacheKey cacheKey = cacheKey(sourceId, trimToNull(secretRef));
        if (cacheKey != null) {
            cache.remove(cacheKey);
        }
    }

    public void invalidateAll() {
        cache.clear();
    }

    public boolean cacheEnabled() {
        return cacheTtlSeconds() > 0;
    }

    public long cacheTtlSeconds() {
        return Math.max(0, properties.webhookSecretCacheTtlSeconds());
    }

    public long rotationOverlapSeconds() {
        return Math.max(0, properties.webhookSecretRotationOverlapSeconds());
    }

    public int cacheSize() {
        if (!cacheEnabled()) {
            cache.clear();
            return 0;
        }
        Instant now = Instant.now(clock);
        cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        return cache.size();
    }

    public SecretProviderHealth externalProviderHealth() {
        return secretProviders.stream()
                .map(SecretProvider::health)
                .filter(health -> "VAULT_KMS".equalsIgnoreCase(health.providerType()))
                .findFirst()
                .orElse(SecretProviderHealth.externalDisabled());
    }

    private String defaultWebhookSecret() {
        if (!StringUtils.hasText(properties.webhookSecret())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 签名密钥未配置");
        }
        return properties.webhookSecret();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private SecretResolveContext resolveContext(DocumentSourceConfig source) {
        return new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                source.id() == null ? null : source.id().toString()
        );
    }

    private CachedSecret cachedSecret(CacheKey cacheKey) {
        if (cacheKey == null || !cacheEnabled()) {
            return null;
        }
        CachedSecret cached = cache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (Instant.now(clock).isBefore(cached.expiresAt())) {
            return cached;
        }
        cache.remove(cacheKey, cached);
        return null;
    }

    private void cacheSecret(CacheKey cacheKey, ResolvedSecret resolvedSecret) {
        if (cacheKey == null || !cacheEnabled() || resolvedSecret == null || !StringUtils.hasText(resolvedSecret.value())) {
            return;
        }
        cache.put(cacheKey, new CachedSecret(
                resolvedSecret.value(),
                resolvedSecret.provider(),
                resolvedSecret.version(),
                Instant.now(clock).plusSeconds(cacheTtlSeconds())
        ));
    }

    private CacheKey cacheKey(DocumentSourceConfig source, String secretRef) {
        return source == null ? null : cacheKey(source.id(), secretRef);
    }

    private CacheKey cacheKey(UUID sourceId, String secretRef) {
        if (sourceId == null || !StringUtils.hasText(secretRef)) {
            return null;
        }
        return new CacheKey(sourceId, secretRef.trim());
    }

    private record CacheKey(UUID sourceId, String secretRef) {
    }

    private record CachedSecret(String value, String provider, String version, Instant expiresAt) {
    }
}
