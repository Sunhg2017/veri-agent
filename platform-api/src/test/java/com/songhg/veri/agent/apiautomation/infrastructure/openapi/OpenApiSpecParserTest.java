package com.songhg.veri.agent.apiautomation.infrastructure.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiSpecParserTest {

    private final OpenApiSpecParser parser = new OpenApiSpecParser(new ObjectMapper().findAndRegisterModules());

    @Test
    void parsesYamlAndMasksSensitiveExamples() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Billing API
                  version: 1.0.0
                paths:
                  /v1/payments:
                    post:
                      operationId: createPayment
                      tags: [payments]
                      parameters:
                        - name: Authorization
                          in: header
                          schema:
                            type: string
                          example: Bearer real-token-value
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                password:
                                  type: string
                                  example: clear-password
                      responses:
                        '201':
                          description: created
                """;

        var result = parser.parse(yaml, 10);

        assertThat(result.endpoints()).hasSize(1);
        assertThat(result.endpoints().get(0).httpMethod()).isEqualTo("POST");
        assertThat(result.endpoints().get(0).path()).isEqualTo("/v1/payments");
        assertThat(result.sanitizedSpecJson()).doesNotContain("real-token-value", "clear-password");
        assertThat(result.sanitizedSpecJson()).contains("***MASKED***");
        assertThat(result.summary()).containsEntry("endpointCount", 1);
    }

    @Test
    void rejectsUnsupportedOpenApiVersion() {
        String swagger2 = """
                swagger: '2.0'
                info:
                  title: Legacy API
                  version: 1.0.0
                paths: {}
                """;

        assertThatThrownBy(() -> parser.parse(swagger2, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPENAPI_PARSE_FAILED");
    }

    @Test
    void enforcesEndpointLimit() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Limit API", "version": "1.0.0"},
                  "paths": {
                    "/a": {"get": {"operationId": "a", "responses": {"200": {"description": "ok"}}}},
                    "/b": {"get": {"operationId": "b", "responses": {"200": {"description": "ok"}}}}
                  }
                }
                """;

        assertThatThrownBy(() -> parser.parse(json, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPENAPI_TOO_LARGE");
    }
}
