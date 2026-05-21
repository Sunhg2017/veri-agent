package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.management.config.ManagementProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnvironmentConnectivityCheckerTest {

    @Test
    void returnsSanitizedFailureForInvalidEndpoint() {
        ManagementProperties properties = new ManagementProperties();
        EnvironmentConnectivityChecker checker = new EnvironmentConnectivityChecker(properties);
        TraceContext.setTraceId("trc_connectivity_test");
        try {
            var result = checker.check("dev", "", "api.dev.local");

            assertThat(result.status()).isEqualTo("DOWN");
            assertThat(result.traceId()).isEqualTo("trc_connectivity_test");
            assertThat(result.endpoints()).hasSize(1);
            assertThat(result.endpoints().get(0).message()).isEqualTo("环境地址需使用 http 或 https");
            assertThat(result.endpoints().get(0).message()).doesNotContain("Exception");
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void honorsDisabledConnectivityProbeConfiguration() {
        ManagementProperties properties = new ManagementProperties();
        properties.setEnvironmentConnectivityCheckEnabled(false);
        EnvironmentConnectivityChecker checker = new EnvironmentConnectivityChecker(properties);

        var result = checker.check("staging", "https://web.example.test", "https://api.example.test");

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.endpoints()).hasSize(2);
        assertThat(result.endpoints()).allMatch(endpoint -> "SKIPPED".equals(endpoint.status()));
    }

    @Test
    void stripsSensitiveUrlPartsFromProbeResult() throws Exception {
        ManagementProperties properties = new ManagementProperties();
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        EnvironmentConnectivityChecker checker = new EnvironmentConnectivityChecker(properties, client);

        var result = checker.check("prod", "", "https://api.example.test/v1?token=secret#fragment");

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.endpoints()).hasSize(1);
        assertThat(result.endpoints().get(0).url()).isEqualTo("https://api.example.test/v1");
        assertThat(result.endpoints().get(0).url()).doesNotContain("secret");
    }

    @Test
    void rejectsUrlCredentialsWithoutSendingProbe() {
        ManagementProperties properties = new ManagementProperties();
        HttpClient client = mock(HttpClient.class);
        EnvironmentConnectivityChecker checker = new EnvironmentConnectivityChecker(properties, client);

        var result = checker.check("prod", "", "https://user:secret@api.example.test/v1?token=hidden");

        assertThat(result.status()).isEqualTo("DOWN");
        assertThat(result.endpoints()).hasSize(1);
        assertThat(result.endpoints().get(0).url()).isEqualTo("https://api.example.test/v1");
        assertThat(result.endpoints().get(0).url()).doesNotContain("secret", "hidden", "user");
        assertThat(result.endpoints().get(0).message()).isEqualTo("环境地址不允许包含认证信息");
        verifyNoInteractions(client);
    }
}
