package com.songhg.veri.agent.apiautomation.infrastructure.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.parser.OpenApiParseResult;
import com.songhg.veri.agent.apiautomation.application.parser.ParsedOpenApiEndpoint;
import com.songhg.veri.agent.common.error.BusinessException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiFixtureSmokeTest {

    private static final String FIXTURE_DIR = "wp6-openapi-fixtures/";

    private final OpenApiSpecParser parser = new OpenApiSpecParser(new ObjectMapper().findAndRegisterModules());

    @Test
    void parsesJsonFixtureIntoSanitizedEndpointSnapshot() {
        OpenApiParseResult result = parser.parse(fixture("openapi-minimal.json"), 20);

        assertThat(result.endpoints())
                .extracting(ParsedOpenApiEndpoint::httpMethod)
                .containsExactly("GET", "POST");
        assertThat(result.endpoints())
                .extracting(ParsedOpenApiEndpoint::path)
                .containsExactly("/v1/customers/{customerId}", "/v1/payments");
        assertThat(result.summary()).containsEntry("endpointCount", 2)
                .containsEntry("requestBodyStored", false)
                .containsEntry("aggregateOnly", true);
        assertThat(result.sanitizedSpecJson())
                .contains("***MASKED***")
                .doesNotContain("wp6-fixture-token-value", "fixture-clear-password");
    }

    @Test
    void parsesYamlFixtureWithPathQueryHeaderAndCookieParameters() {
        OpenApiParseResult result = parser.parse(fixture("openapi-path-query.yaml"), 20);

        assertThat(result.endpoints()).hasSize(2);
        assertThat(result.endpoints().getFirst().parameterCount()).isEqualTo(4);
        assertThat(result.endpoints().getFirst().responseStatuses()).containsExactly("200", "429");
        assertThat(result.endpoints().get(1).requestBodyPresent()).isTrue();
        assertThat(result.sanitizedSpecJson()).doesNotContain("fixture-cookie-value");
    }

    @Test
    void masksSensitiveExamplesFromFixtureCorpus() {
        OpenApiParseResult result = parser.parse(fixture("openapi-secret-examples.json"), 20);

        assertThat(result.sanitizedSpecJson())
                .contains("***MASKED***")
                .doesNotContain(
                        "sk-wp6-fixture-secret-value-000000",
                        "wp6-fixture-bearer-secret",
                        "fixture-client-secret",
                        "d3A2LWZpeHR1cmU6c2VjcmV0"
                );
    }

    @Test
    void rejectsInvalidFixtureWithStableErrorCode() {
        assertThatThrownBy(() -> parser.parse(fixture("openapi-invalid.json"), 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPENAPI_PARSE_FAILED");
    }

    @Test
    void rejectsFixtureWhenEndpointLimitIsExceeded() {
        assertThatThrownBy(() -> parser.parse(fixture("openapi-large.json"), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPENAPI_TOO_LARGE");
    }

    private String fixture(String fileName) {
        try (var input = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream(FIXTURE_DIR + fileName),
                "Missing WP6 OpenAPI fixture: " + fileName
        )) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
