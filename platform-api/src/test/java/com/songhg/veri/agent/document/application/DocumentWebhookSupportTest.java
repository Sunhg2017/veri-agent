package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentWebhookSupportTest {

    private final DocumentWebhookSupport support = new DocumentWebhookSupport(
            new ObjectMapper(),
            properties(),
            new DocumentWebhookSecretResolver(properties())
    );

    @Test
    void validatesWebhookSignatureWithConfiguredSecret() throws Exception {
        String payload = "{\"projectId\":\"project-wp4\",\"eventType\":\"requirement.created\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String eventId = "evt-support-valid";
        String idempotencyKey = "idem-support-valid";
        String signature = hmacSha256("default-secret", String.join(".",
                timestamp,
                eventId,
                idempotencyKey,
                payload
        ));

        assertThat(support.validateSignature(
                source("1.0"),
                payload,
                timestamp,
                signature,
                eventId,
                idempotencyKey
        )).isEqualTo(WebhookSignatureStatus.VALID);
    }

    @Test
    void rejectsExpiredWebhookSignatureTimestamp() {
        String timestamp = String.valueOf(Instant.now().minusSeconds(301).getEpochSecond());

        assertThat(support.validateSignature(
                source("1.0"),
                "{}",
                timestamp,
                "invalid",
                "evt-support-expired",
                "idem-support-expired"
        )).isEqualTo(WebhookSignatureStatus.EXPIRED);
    }

    @Test
    void rejectsInvalidJsonWhenWorkerParsesPayload() {
        assertThatThrownBy(() -> support.parsePayload("{invalid-json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(DocumentInputMessages.WEBHOOK_PAYLOAD_INVALID_JSON);
    }

    @Test
    void rejectsNullPayloadWhenWorkerParsesPayload() {
        assertThatThrownBy(() -> support.parsePayload(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(DocumentInputMessages.WEBHOOK_PAYLOAD_INVALID_JSON);
    }

    @Test
    void defaultsIngressEventTypeForInvalidJsonEnvelope() {
        assertThat(support.eventTypeOrDefault("{invalid-json")).isEqualTo("requirement.created");
    }

    @Test
    void acceptsMatchingCustomApiEventVersion() {
        assertThatCode(() -> support.ensureSourceWebhookEventVersion(source("1.0"), "1.0"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedConfiguredEventVersion() {
        assertThatThrownBy(() -> support.ensureSourceWebhookEventVersion(source("2.0"), "1.0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的 webhook eventVersion");
    }

    private String hmacSha256(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private DocumentInputProperties properties() {
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

    private DocumentSourceConfig source(String eventVersion) {
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
                "secret://wp4/source-a",
                eventVersion,
                "default",
                null,
                now,
                now
        );
    }
}
