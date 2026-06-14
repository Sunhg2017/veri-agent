package com.songhg.veri.agent.apiautomation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.common.error.BusinessException;
import org.junit.jupiter.api.Test;

class ApiAutomationRunTargetGuardTest {

    @Test
    void normalizesAllowedUrlAndStoresOnlySafeTargetMetadata() {
        ApiAutomationRunTargetGuard guard = guard("api.example.test,*.trusted.example");

        ApiAutomationRunTargetGuard.RunTarget target = guard.validateRunTarget(
                "HTTPS://API.EXAMPLE.TEST:8443/service/"
        );

        assertThat(target.normalizedBaseUrl()).isEqualTo("https://api.example.test:8443/service");
        assertThat(target.host()).isEqualTo("api.example.test");
        assertThat(target.digest()).matches("[0-9a-f]{64}");
        assertThat(target.allowed()).isTrue();
        assertThat(target.blocked()).isFalse();
        assertThat(guard.allowedBaseUrlConfigured()).isTrue();
    }

    @Test
    void rejectsCredentialsQueryAndFragmentBeforeRunnerDispatch() {
        ApiAutomationRunTargetGuard guard = guard("api.example.test");

        assertThatThrownBy(() -> guard.validateRunTarget("https://user:pass@api.example.test/service"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("baseUrl 不允许携带 userInfo/query/fragment");
        assertThatThrownBy(() -> guard.validateRunTarget("https://api.example.test/service?token=secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("baseUrl 不允许携带 userInfo/query/fragment");
        assertThatThrownBy(() -> guard.validateRunTarget("https://api.example.test/service#token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("baseUrl 不允许携带 userInfo/query/fragment");
    }

    @Test
    void marksPrivateAndLocalTargetsBlocked() {
        ApiAutomationRunTargetGuard guard = guard("localhost,127.0.0.1,service.local");

        assertBlocked(guard, "http://localhost");
        assertBlocked(guard, "http://127.0.0.1");
        assertBlocked(guard, "http://169.254.169.254");
        assertBlocked(guard, "http://192.168.1.10");
        assertBlocked(guard, "http://service.local");
    }

    @Test
    void wildcardAllowlistRequiresSubdomain() {
        ApiAutomationRunTargetGuard guard = guard("*.trusted.example");

        assertThat(guard.validateRunTarget("https://api.trusted.example").allowed()).isTrue();
        assertThat(guard.validateRunTarget("https://trusted.example").allowed()).isFalse();
    }

    @Test
    void emptyAllowlistIsReportedAsUnconfigured() {
        ApiAutomationRunTargetGuard guard = guard(" , ");

        assertThat(guard.allowedBaseUrlConfigured()).isFalse();
        assertThat(guard.validateRunTarget("https://api.example.test").allowed()).isFalse();
    }

    private void assertBlocked(ApiAutomationRunTargetGuard guard, String rawBaseUrl) {
        ApiAutomationRunTargetGuard.RunTarget target = guard.validateRunTarget(rawBaseUrl);

        assertThat(target.blocked()).as(rawBaseUrl).isTrue();
    }

    private ApiAutomationRunTargetGuard guard(String allowedBaseUrlPatterns) {
        return new ApiAutomationRunTargetGuard(new ApiAutomationProperties(
                65_536,
                50,
                true,
                120,
                100,
                allowedBaseUrlPatterns,
                1_048_576,
                "wp6-api-automation-v1",
                true
        ));
    }
}
