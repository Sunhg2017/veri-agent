package com.songhg.veri.agent.modelaccess.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI wp2OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Veri Agent WP2 Model Access API")
                        .version("0.1.0")
                        .description("WP2 model provider, prompt version, guarded invocation, audit, fallback, and cost APIs."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("service-token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
