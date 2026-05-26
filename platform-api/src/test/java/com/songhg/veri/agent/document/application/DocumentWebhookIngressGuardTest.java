package com.songhg.veri.agent.document.application;

import com.songhg.veri.agent.document.config.DocumentInputProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentWebhookIngressGuardTest {

    @Test
    void resolvesForwardedClientIpOnlyFromTrustedProxyAndChecksCidrs() {
        DocumentWebhookIngressGuard guard = new DocumentWebhookIngressGuard(properties(
                "203.0.113.0/24",
                Map.of("custom-reqs", "198.51.100.7/32"),
                "10.0.0.0/8",
                0,
                60
        ));

        String trustedForwardedIp = guard.resolveClientIp("10.1.2.3", "203.0.113.9, 198.51.100.10", null);
        String untrustedForwardedIp = guard.resolveClientIp("192.0.2.8", "203.0.113.9", null);

        assertThat(trustedForwardedIp).isEqualTo("203.0.113.9");
        assertThat(untrustedForwardedIp).isEqualTo("192.0.2.8");
        assertThat(guard.isIpAllowed("custom-reqs", trustedForwardedIp)).isTrue();
        assertThat(guard.isIpAllowed("custom-reqs", "198.51.100.7")).isTrue();
        assertThat(guard.isIpAllowed("custom-reqs", untrustedForwardedIp)).isFalse();
    }

    @Test
    void limitsBySourceIpAndIdempotencyKeyWithinWindow() {
        DocumentWebhookIngressGuard guard = new DocumentWebhookIngressGuard(properties(
                "",
                Map.of(),
                "",
                2,
                60
        ));

        assertThat(guard.checkRateLimit("custom-reqs", "203.0.113.9", "idem-1").allowed()).isTrue();
        assertThat(guard.checkRateLimit("custom-reqs", "203.0.113.9", "idem-2").allowed()).isTrue();

        DocumentWebhookIngressGuard.RateLimitDecision rejected =
                guard.checkRateLimit("custom-reqs", "203.0.113.9", "idem-3");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.dimension()).isEqualTo("sourceCode");
        assertThat(rejected.limit()).isEqualTo(2);
        assertThat(rejected.windowSeconds()).isEqualTo(60);
    }

    private static DocumentInputProperties properties(
            String allowedCidrs,
            Map<String, String> sourceAllowedCidrs,
            String trustedProxyCidrs,
            int rateLimit,
            long rateLimitWindowSeconds
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
                true,
                262144,
                100,
                3,
                false,
                20,
                60,
                300,
                Map.of(),
                allowedCidrs,
                sourceAllowedCidrs,
                trustedProxyCidrs,
                rateLimit,
                rateLimitWindowSeconds,
                true,
                0,
                0,
                "LOCAL_COMMAND",
                "",
                "",
                true,
                "",
                15,
                2,
                2000,
                false,
                90,
                90
        );
    }
}
