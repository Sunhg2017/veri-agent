package com.songhg.veri.agent.apiautomation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiAutomationRunSecretResolverTest {

    @Test
    void trimsDeduplicatesAndDigestsSecretRefs() {
        ApiAutomationRunSecretResolver resolver = new ApiAutomationRunSecretResolver(List.of());

        ApiAutomationRunSecretResolver.RunSecretRefs refs = resolver.validateRunSecretRefs(List.of(
                " secret://wp6/payment-token ",
                "",
                "secret://wp6/payment-token",
                "secret://wp6/report-token"
        ));

        assertThat(refs.count()).isEqualTo(2);
        assertThat(refs.refs()).containsExactly("secret://wp6/payment-token", "secret://wp6/report-token");
        assertThat(refs.digests()).hasSize(2).allSatisfy(digest -> assertThat(digest).startsWith("sha256:")
                .hasSize(71));
    }

    @Test
    void rejectsNonSecretSchemeBeforeResolvingProvider() {
        ApiAutomationRunSecretResolver resolver = new ApiAutomationRunSecretResolver(List.of());

        assertThatThrownBy(() -> resolver.validateRunSecretRefs(List.of("env:WP6_TOKEN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("secretRefs 必须使用 secret:// 引用");
    }

    @Test
    void resolvesSecretsWithProjectScopedContextAndControlledHeaders() {
        CapturingSecretProvider provider = new CapturingSecretProvider("secret://wp6/payment-token", "resolved-secret");
        ApiAutomationRunSecretResolver resolver = new ApiAutomationRunSecretResolver(List.of(provider));
        ApiAutomationRunSecretResolver.RunSecretRefs refs =
                resolver.validateRunSecretRefs(List.of("secret://wp6/payment-token"));

        List<ApiAutomationRunnerPort.RunnerSecret> secrets = resolver.resolveRunSecrets(refs, "project-alpha");

        assertThat(provider.lastSecretRef).isEqualTo("secret://wp6/payment-token");
        assertThat(provider.lastContext).isEqualTo(new SecretResolveContext(
                "API_AUTOMATION_RUNNER",
                "wp6-api-automation-runner",
                "PROJECT",
                "project-alpha"
        ));
        assertThat(secrets).singleElement().satisfies(secret -> {
            assertThat(secret.headerName()).isEqualTo("X-VA-WP6-Secret-1");
            assertThat(secret.secretRefDigest()).isEqualTo(refs.digests().getFirst());
            assertThat(secret.value()).isEqualTo("resolved-secret");
            assertThat(secret.toString()).doesNotContain("resolved-secret");
        });
    }

    @Test
    void reportsOnlyDigestWhenProviderCannotResolveSecretRef() {
        ApiAutomationRunSecretResolver resolver = new ApiAutomationRunSecretResolver(List.of(
                new CapturingSecretProvider("secret://wp6/other-token", "resolved-secret")
        ));
        ApiAutomationRunSecretResolver.RunSecretRefs refs =
                resolver.validateRunSecretRefs(List.of("secret://wp6/payment-token"));

        assertThatThrownBy(() -> resolver.resolveRunSecrets(refs, "project-alpha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("runner secretRef 未解析: sha256:")
                .hasMessageNotContaining("secret://wp6/payment-token");
    }

    @Test
    void rejectsPlaintextHeaderValuesThatCannotBeSafelyInjected() {
        ApiAutomationRunSecretResolver resolver = new ApiAutomationRunSecretResolver(List.of(
                new CapturingSecretProvider("secret://wp6/payment-token", "line1\nline2")
        ));
        ApiAutomationRunSecretResolver.RunSecretRefs refs =
                resolver.validateRunSecretRefs(List.of("secret://wp6/payment-token"));

        assertThatThrownBy(() -> resolver.resolveRunSecrets(refs, "project-alpha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("runner secretRef 值不适合注入: sha256:")
                .hasMessageNotContaining("line1");
    }

    private static final class CapturingSecretProvider implements SecretProvider {

        private final String acceptedSecretRef;
        private final String value;
        private String lastSecretRef;
        private SecretResolveContext lastContext;

        private CapturingSecretProvider(String acceptedSecretRef, String value) {
            this.acceptedSecretRef = acceptedSecretRef;
            this.value = value;
        }

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            lastSecretRef = secretRef;
            lastContext = context;
            if (!acceptedSecretRef.equals(secretRef)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSecret(secretRef, value, "unit-test-provider", "v1"));
        }
    }
}
