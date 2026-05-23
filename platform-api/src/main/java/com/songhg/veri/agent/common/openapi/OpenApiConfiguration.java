package com.songhg.veri.agent.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    @Bean
    OperationCustomizer publicApiDocumentationCustomizer() {
        return (operation, handlerMethod) -> {
            String tag = defaultTag(handlerMethod.getBeanType());
            if (operation.getTags() == null || operation.getTags().isEmpty()) {
                operation.setTags(List.of(tag));
            }
            if (!StringUtils.hasText(operation.getSummary())) {
                String operationId = StringUtils.hasText(operation.getOperationId())
                        ? operation.getOperationId()
                        : handlerMethod.getMethod().getName();
                operation.setSummary(tag + " - " + humanizeOperationId(operationId));
            }
            operation.setResponses(documentedResponses(operation));
            return operation;
        };
    }

    private static ApiResponses documentedResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
        }
        responses.forEach((status, response) -> {
            if (response != null && !StringUtils.hasText(response.getDescription())) {
                response.setDescription(defaultDescription(status));
            }
        });
        addResponseIfAbsent(responses, "400", "请求参数或请求体校验失败");
        addResponseIfAbsent(responses, "401", "未登录或令牌无效");
        addResponseIfAbsent(responses, "403", "权限不足或资源作用域不匹配");
        addResponseIfAbsent(responses, "500", "服务端处理失败，响应体包含 traceId");
        return responses;
    }

    private static void addResponseIfAbsent(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, new ApiResponse().description(description));
        }
    }

    private static String defaultDescription(String status) {
        return switch (status) {
            case "200" -> "请求成功";
            case "201" -> "资源已创建";
            case "202" -> "请求已接受，异步处理或延迟处理";
            case "204" -> "请求成功，无响应体";
            case "400" -> "请求参数或请求体校验失败";
            case "401" -> "未登录或令牌无效";
            case "403" -> "权限不足或资源作用域不匹配";
            case "404" -> "资源不存在";
            case "409" -> "资源状态或唯一性冲突";
            case "500" -> "服务端处理失败，响应体包含 traceId";
            default -> "HTTP " + status + " response";
        };
    }

    private static String defaultTag(Class<?> beanType) {
        String packageName = beanType.getPackageName();
        if (packageName.contains(".auth.")) {
            return "WP1 Auth";
        }
        if (packageName.contains(".management.")) {
            return "WP1 Management";
        }
        if (packageName.contains(".integration.")) {
            return "WP1 Integration";
        }
        if (packageName.contains(".modelaccess.")) {
            return "WP2 Model Access";
        }
        if (packageName.contains(".asset.")) {
            return "WP3 Asset";
        }
        if (packageName.contains(".documentinput.")) {
            return "WP4 Document Input";
        }
        if (beanType.getSimpleName().contains("Health")) {
            return "Platform Health";
        }
        if (beanType.getSimpleName().contains("Example")) {
            return "Platform Examples";
        }
        return beanType.getSimpleName().replace("Controller", "");
    }

    private static String humanizeOperationId(String operationId) {
        String normalized = operationId.replaceAll("_\\d+$", "");
        List<String> words = new ArrayList<>();
        for (String token : normalized.split("[_-]+")) {
            splitCamelCase(token, words);
        }
        if (words.isEmpty()) {
            return normalized;
        }
        return String.join(" ", words);
    }

    private static void splitCamelCase(String token, List<String> words) {
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                words.add(formatWord(current.toString()));
                current.setLength(0);
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            words.add(formatWord(current.toString()));
        }
    }

    private static String formatWord(String word) {
        if (!StringUtils.hasText(word)) {
            return "";
        }
        String lower = word.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "api" -> "API";
            case "apis" -> "APIs";
            case "csv" -> "CSV";
            case "id" -> "ID";
            case "sse" -> "SSE";
            case "url" -> "URL";
            case "wp1" -> "WP1";
            case "wp2" -> "WP2";
            case "wp3" -> "WP3";
            case "wp4" -> "WP4";
            default -> Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        };
    }
}
