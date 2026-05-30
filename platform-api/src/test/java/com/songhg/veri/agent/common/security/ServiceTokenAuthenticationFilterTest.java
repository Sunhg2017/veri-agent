package com.songhg.veri.agent.common.security;

import com.songhg.veri.agent.asset.config.AssetProperties;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsPublicHealthEndpointsWithoutCreatingServiceAuthentication() throws Exception {
        MockHttpServletRequest request = request("/api/v1/asset/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticatesTrustedAssetCallerWhenServiceTokenMatches() throws Exception {
        MockHttpServletRequest request = request("/api/v1/asset/apis");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer asset-token");
        request.addHeader("X-Caller-Service", "Model-Access");
        request.addHeader("X-Delegated-User-Id", "user-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new ServicePrincipal("Model-Access", "user-123"));
        assertThat(authentication.getAuthorities())
                .anySatisfy(authority -> assertThat(authority.getAuthority()).isEqualTo("ROLE_SERVICE"));
    }

    @Test
    void authenticatesTrustedTestDesignCallerWhenServiceTokenMatches() throws Exception {
        MockHttpServletRequest request = request("/api/v1/test-design/tasks");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-design-token");
        request.addHeader("X-Caller-Service", "wp5-test-design");
        request.addHeader("X-Delegated-User-Id", "user-wp5-smoke");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new ServicePrincipal("wp5-test-design", "user-wp5-smoke"));
        assertThat(authentication.getAuthorities())
                .anySatisfy(authority -> assertThat(authority.getAuthority()).isEqualTo("ROLE_SERVICE"));
    }

    @Test
    void rejectsMatchingServiceTokenWhenCallerIsNotTrusted() throws Exception {
        MockHttpServletRequest request = request("/api/v1/asset/apis");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer asset-token");
        request.addHeader("X-Caller-Service", "unknown-service");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("服务调用方不可信");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static ServiceTokenAuthenticationFilter newFilter() {
        return new ServiceTokenAuthenticationFilter(
                modelAccessProperties(),
                new AssetProperties("asset-token"),
                documentInputProperties(),
                testDesignProperties(),
                new ServiceCallerProperties(
                        List.of("document-input"),
                        List.of("model-access"),
                        List.of("asset-service"),
                        List.of("wp5-test-design")
                )
        );
    }

    private static TestDesignProperties testDesignProperties() {
        return new TestDesignProperties(
                "test-design-token",
                true,
                "RULE_TEMPLATE",
                "wp5",
                "1.0",
                false,
                20,
                3,
                5,
                5,
                5,
                240,
                240,
                240,
                100,
                true,
                true,
                100,
                600,
                120,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                false,
                0.86D,
                0.90D,
                true,
                50,
                180,
                false,
                true
        );
    }

    private static ModelAccessProperties modelAccessProperties() {
        return new ModelAccessProperties(
                "model-token",
                null,
                0,
                null,
                null,
                0,
                null,
                0,
                0,
                0,
                0,
                0,
                null,
                0,
                0,
                0,
                0,
                0,
                3_600_000,
                null,
                null,
                List.of()
        );
    }

    private static DocumentInputProperties documentInputProperties() {
        return new DocumentInputProperties(
                "document-token",
                null,
                0,
                false,
                false,
                false,
                null,
                null,
                false,
                0,
                0,
                0,
                null,
                0,
                0,
                0,
                false,
                0,
                0,
                0,
                false,
                0,
                0,
                0,
                Map.of(),
                null,
                Map.of(),
                null,
                0,
                0,
                true,
                0,
                0,
                "LOCAL_COMMAND",
                null,
                null,
                true,
                null,
                0,
                0,
                0,
                false,
                0,
                0
        );
    }
}
