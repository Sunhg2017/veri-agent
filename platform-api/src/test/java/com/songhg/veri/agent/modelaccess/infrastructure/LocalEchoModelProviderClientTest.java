package com.songhg.veri.agent.modelaccess.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.modelaccess.application.command.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEchoModelProviderClientTest {

    private final LocalEchoModelProviderClient client = new LocalEchoModelProviderClient(new ObjectMapper());

    @Test
    void returnsStructuredWp10FailureDiagnosisForMarker() {
        ProviderCallResult result = client.call(localEchoProvider(), new ProviderCallRequest(
                "local-echo",
                "",
                """
                        user: {
                          "schemaMarker": "WP10_FAILURE_DIAGNOSIS_V1",
                          "classification": {"primaryCategory": "ASSERTION_FAILED"}
                        }
                        """
        ));

        assertThat(result.content()).contains("\"schemaVersion\":\"wp10-diagnosis-result-v1\"");
        assertThat(result.content()).contains("ASSERTION_FAILED");
        assertThat(result.inputTokens()).isPositive();
        assertThat(result.outputTokens()).isPositive();
    }

    private ModelProviderConfig localEchoProvider() {
        Instant now = Instant.now();
        return new ModelProviderConfig(
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "local-echo-primary",
                ProviderType.LOCAL_ECHO,
                "default",
                "CHAT,TEXT,JSON,REQUIREMENT_PARSE",
                null,
                "local://echo",
                ProviderStatus.ENABLED,
                10,
                3000,
                new BigDecimal("0.0001"),
                new BigDecimal("0.0002"),
                now,
                now
        );
    }
}
