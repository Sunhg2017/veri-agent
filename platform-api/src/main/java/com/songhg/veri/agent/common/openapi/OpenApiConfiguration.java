package com.songhg.veri.agent.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

@Configuration
public class OpenApiConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String CURRENT_API_VERSION = "v1";

    @Bean
    OpenAPI wp1OpenApi() {
        Info info = new Info()
                .title("Veri Agent WP1 Platform API")
                .version("0.1.0")
                .description("Single-platform WP1 control-plane APIs for auth, management, audit, settings, and WP2-WP4 modules.");
        info.addExtension("x-api-version-policy", Map.of(
                "current", CURRENT_API_VERSION,
                "pathPrefix", "/api/" + CURRENT_API_VERSION,
                "breakingChangeRule", "Breaking changes require a new path prefix such as /api/v2.",
                "compatibleChanges", List.of("new optional fields", "new endpoints", "new enum values with fallback handling"),
                "lifecycles", List.of(ApiLifecycle.STABLE.name(), ApiLifecycle.INTERNAL.name(), ApiLifecycle.DEPRECATED.name())
        ));
        return new OpenAPI()
                .info(info)
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque-session-token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OperationCustomizer apiVersionOperationCustomizer() {
        return (operation, handlerMethod) -> {
            ApiVersion apiVersion = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiVersion.class);
            if (apiVersion == null) {
                apiVersion = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiVersion.class);
            }
            if (apiVersion == null) {
                return operation;
            }
            operation.addExtension("x-api-version", apiVersion.value());
            operation.addExtension("x-api-lifecycle", apiVersion.lifecycle().name());
            operation.addExtension("x-api-version-since", apiVersion.since());
            if (StringUtils.hasText(apiVersion.sunset())) {
                operation.addExtension("x-api-sunset", apiVersion.sunset());
            }
            if (StringUtils.hasText(apiVersion.replacement())) {
                operation.addExtension("x-api-replacement", apiVersion.replacement());
            }
            return operation;
        };
    }
}
