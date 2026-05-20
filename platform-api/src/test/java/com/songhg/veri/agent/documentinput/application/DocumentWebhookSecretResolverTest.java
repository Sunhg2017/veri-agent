package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    void resolvesConfiguredSecretRefBeforeDefaultFallback() {
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of(
                "secret://wp4/source-a", "source-a-secret"
        )));

        assertThat(resolver.resolve(source("secret://wp4/source-a"))).isEqualTo("source-a-secret");
    }

    @Test
    void resolvesWp4SecretUriToDefaultSecretForLocalMvp() {
        DocumentWebhookSecretResolver resolver = new DocumentWebhookSecretResolver(properties(Map.of()));

        assertThat(resolver.resolve(source("secret://wp4/source-b"))).isEqualTo("default-secret");
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
