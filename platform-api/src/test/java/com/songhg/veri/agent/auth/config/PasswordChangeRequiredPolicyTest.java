package com.songhg.veri.agent.auth.config;

import com.songhg.veri.agent.auth.application.AuthProperties.PasswordChangeAllowedRequest;
import com.songhg.veri.agent.auth.application.AuthProperties.PasswordChangePathMatch;
import com.songhg.veri.agent.auth.application.AuthProperties.PasswordChangeRequired;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangeRequiredPolicyTest {

    @Test
    void allowsDefaultRecoveryAndHealthEndpointsBeforePasswordChange() {
        PasswordChangeRequiredPolicy policy = new PasswordChangeRequiredPolicy(PasswordChangeRequired.defaults());

        assertThat(policy.allows(request("OPTIONS", "/api/v1/management/departments"))).isTrue();
        assertThat(policy.allows(request("GET", "/api/v1/auth/me"))).isTrue();
        assertThat(policy.allows(request("POST", "/api/v1/auth/change-password"))).isTrue();
        assertThat(policy.allows(request("GET", "/swagger-ui/index.html"))).isTrue();
        assertThat(policy.allows(request("GET", "/api/v1/management/departments"))).isFalse();
    }

    @Test
    void appliesConfiguredMethodAndPathMatchRules() {
        PasswordChangeRequired properties = new PasswordChangeRequired(List.of(
                new PasswordChangeAllowedRequest("GET", "/internal/status/", PasswordChangePathMatch.PREFIX),
                new PasswordChangeAllowedRequest("POST", "/api/v1/auth/custom-reset", PasswordChangePathMatch.EXACT)
        ));
        PasswordChangeRequiredPolicy policy = new PasswordChangeRequiredPolicy(properties);

        assertThat(policy.allows(request("GET", "/internal/status/ready"))).isTrue();
        assertThat(policy.allows(request("POST", "/internal/status/ready"))).isFalse();
        assertThat(policy.allows(request("POST", "/api/v1/auth/custom-reset"))).isTrue();
        assertThat(policy.allows(request("POST", "/api/v1/auth/custom-reset/extra"))).isFalse();
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
