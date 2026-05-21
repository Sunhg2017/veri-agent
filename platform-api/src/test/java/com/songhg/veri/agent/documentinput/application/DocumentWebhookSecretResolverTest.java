package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentWebhookSecretResolverTest {

    @Test
    void resolvesSecretProviderBeforePropertyFallback() {
        SecretProvider provider = (secretRef, context) -> "secret://wp4/source-a".equals(secretRef)
                ? Optional.of(new ResolvedSecret(secretRef, "provider-secret", "local", "v1"))
                : Optional.empty();
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of(
                "secret://wp4/source-a", "property-secret"
        )), List.of(provider));

        assertThat(resolver.resolve(source("secret://wp4/source-a"))).isEqualTo("provider-secret");
    }

    @Test
    void resolvesSecretProviderWithSourceScopeContext() {
        AtomicReference<SecretResolveContext> captured = new AtomicReference<>();
        SecretProvider provider = (secretRef, context) -> {
            captured.set(context);
            return Optional.of(new ResolvedSecret(secretRef, "provider-secret", "local", "v1"));
        };
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of()), List.of(provider));
        DocumentSourceConfig source = source("secret://wp4/source-a");

        assertThat(resolver.resolve(source)).isEqualTo("provider-secret");
        assertThat(captured.get()).isEqualTo(new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                source.id().toString()
        ));
    }

    @Test
    void cachesSuccessfulSecretProviderResolutionUntilTtl() {
        CountingSecretProvider provider = new CountingSecretProvider("provider-secret");
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of()), List.of(provider));
        DocumentSourceConfig source = source("secret://wp4/source-a");

        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-1");
        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-1");

        assertThat(provider.resolveCount()).isEqualTo(1);
        assertThat(resolver.cacheSize()).isEqualTo(1);
    }

    @Test
    void expiresCachedProviderSecretAfterTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-22T00:00:00Z"));
        CountingSecretProvider provider = new CountingSecretProvider("provider-secret");
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(
                properties(Map.of(), true, 1, 300),
                List.of(provider),
                clock
        );
        DocumentSourceConfig source = source("secret://wp4/source-a");

        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-1");
        clock.advance(Duration.ofSeconds(2));

        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-2");
        assertThat(provider.resolveCount()).isEqualTo(2);
        assertThat(resolver.cacheSize()).isEqualTo(1);
    }

    @Test
    void disablesCacheWhenTtlIsZero() {
        CountingSecretProvider provider = new CountingSecretProvider("provider-secret");
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(
                properties(Map.of(), true, 0, 300),
                List.of(provider)
        );
        DocumentSourceConfig source = source("secret://wp4/source-a");

        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-1");
        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-2");

        assertThat(provider.resolveCount()).isEqualTo(2);
        assertThat(resolver.cacheEnabled()).isFalse();
        assertThat(resolver.cacheSize()).isZero();
    }

    @Test
    void resolvesConfiguredSecretRefBeforeDefaultFallback() {
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of(
                "secret://wp4/source-a", "source-a-secret"
        )));

        assertThat(resolver.resolve(source("secret://wp4/source-a"))).isEqualTo("source-a-secret");
        assertThat(resolver.cacheSize()).isZero();
    }

    @Test
    void doesNotCacheConfiguredFallbackSecret() {
        Map<String, String> secrets = new HashMap<>();
        secrets.put("secret://wp4/source-a", "source-a-secret-v1");
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(secrets));
        DocumentSourceConfig source = source("secret://wp4/source-a");

        assertThat(resolver.resolve(source)).isEqualTo("source-a-secret-v1");
        secrets.put("secret://wp4/source-a", "source-a-secret-v2");

        assertThat(resolver.resolve(source)).isEqualTo("source-a-secret-v2");
        assertThat(resolver.cacheSize()).isZero();
    }

    @Test
    void resolvesWp4SecretUriToDefaultSecretForLocalMvp() {
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of()));

        assertThat(resolver.resolve(source("secret://wp4/source-b"))).isEqualTo("default-secret");
        assertThat(resolver.cacheSize()).isZero();
    }

    @Test
    void invalidateClearsCachedProviderSecret() {
        CountingSecretProvider provider = new CountingSecretProvider("provider-secret");
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of()), List.of(provider));
        DocumentSourceConfig source = source("secret://wp4/source-a");

        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-1");
        resolver.invalidate(source);

        assertThat(resolver.resolve(source)).isEqualTo("provider-secret-2");
        assertThat(provider.resolveCount()).isEqualTo(2);
        assertThat(resolver.cacheSize()).isEqualTo(1);
    }

    @Test
    void exposesConfiguredRotationOverlapWindow() {
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of(), true, 60, 600));

        assertThat(resolver.cacheEnabled()).isTrue();
        assertThat(resolver.cacheTtlSeconds()).isEqualTo(60);
        assertThat(resolver.rotationOverlapSeconds()).isEqualTo(600);
    }

    @Test
    void rejectsUnknownSecretRefs() {
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of()));

        assertThatThrownBy(() -> resolver.resolve(source("secret://external/source-c")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("webhook 密钥引用未解析");
    }

    @Test
    void rejectsLocalFallbackWhenDisabled() {
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of(), false));

        assertThatThrownBy(() -> resolver.resolve(source("secret://wp4/source-b")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("webhook 密钥引用未解析");
    }

    private DocumentInputProperties properties(Map<String, String> secrets) {
        return properties(secrets, true);
    }

    private DocumentInputProperties properties(Map<String, String> secrets, boolean localFallbackEnabled) {
        return properties(secrets, localFallbackEnabled, 60, 300);
    }

    private DocumentInputProperties properties(
            Map<String, String> secrets,
            boolean localFallbackEnabled,
            long cacheTtlSeconds,
            long rotationOverlapSeconds
    ) {
        return new DocumentInputProperties(
                "service-token",
                "default-secret",
                300,
                true,
                true,
                false,
                "wp4-document-requirement-parse",
                "INTERNAL",
                false,
                8000,
                16777216,
                10485760,
                "",
                30,
                20000,
                2,
                localFallbackEnabled,
                262144,
                100,
                3,
                false,
                20,
                cacheTtlSeconds,
                rotationOverlapSeconds,
                secrets,
                "",
                Map.of(),
                "",
                0,
                60,
                true,
                0,
                0,
                "LOCAL_COMMAND",
                "",
                15,
                2,
                2000,
                false,
                90,
                90
        );
    }

    private static final class CountingSecretProvider implements SecretProvider {

        private final String prefix;
        private final AtomicInteger resolveCount = new AtomicInteger();

        private CountingSecretProvider(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            int count = resolveCount.incrementAndGet();
            return Optional.of(new ResolvedSecret(secretRef, prefix + "-" + count, "local", "v" + count));
        }

        private int resolveCount() {
            return resolveCount.get();
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }
    }

    private DocumentSourceConfig source(String secretRef) {
        Instant now = Instant.now();
        return new DocumentSourceConfig(
                UUID.randomUUID(),
                "custom-reqs",
                "Custom Reqs",
                DocumentSourceType.CUSTOM_API,
                DocumentSourceStatus.ENABLED,
                "https://example.test",
                "project-wp4",
                UUID.randomUUID(),
                secretRef,
                "1.0",
                "default",
                null,
                now,
                now
        );
    }
}
