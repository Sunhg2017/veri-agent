package com.songhg.veri.agent.auth.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "veri-agent.auth")
public record AuthProperties(
        /** JWT 签名密钥，生产环境必须由安全配置注入 */
        String tokenSecret,
        /** 访问令牌有效期，单位分钟 */
        @Min(1) long accessTokenTtlMinutes,
        /** 是否启用会话清理任务 */
        boolean sessionCleanupEnabled,
        /** 会话清理保留时长，单位秒 */
        @Min(1) long sessionCleanupRetentionSeconds,
        /** 强制改密期间仍允许访问的请求白名单 */
        @Valid PasswordChangeRequired passwordChangeRequired
) {
    public AuthProperties {
        passwordChangeRequired = passwordChangeRequired == null
                ? PasswordChangeRequired.defaults()
                : passwordChangeRequired;
    }

    public record PasswordChangeRequired(
            /** 强制改密场景下允许放行的接口列表 */
            @Valid List<PasswordChangeAllowedRequest> allowedRequests
    ) {
        public PasswordChangeRequired {
            allowedRequests = allowedRequests == null
                    ? List.of()
                    : List.copyOf(allowedRequests);
        }

        public static PasswordChangeRequired defaults() {
            // Fail-safe defaults mirror application-auth.yml to avoid locking users out of password recovery.
            return new PasswordChangeRequired(List.of(
                    allow(null, "/actuator/", PasswordChangePathMatch.PREFIX),
                    allow(null, "/v3/api-docs/", PasswordChangePathMatch.PREFIX),
                    allow(null, "/swagger-ui/", PasswordChangePathMatch.PREFIX),
                    allow(null, "/swagger-ui.html", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/model-access/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/asset/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/document-input/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/ui-e2e/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/execution/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/reports/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/auth/me", PasswordChangePathMatch.EXACT),
                    allow("POST", "/api/v1/auth/login", PasswordChangePathMatch.EXACT),
                    allow("POST", "/api/v1/auth/refresh", PasswordChangePathMatch.EXACT),
                    allow("POST", "/api/v1/auth/logout", PasswordChangePathMatch.EXACT),
                    allow("POST", "/api/v1/auth/change-password", PasswordChangePathMatch.EXACT)
            ));
        }

        private static PasswordChangeAllowedRequest allow(
                String method,
                String path,
                PasswordChangePathMatch match
        ) {
            return new PasswordChangeAllowedRequest(method, path, match);
        }
    }

    public record PasswordChangeAllowedRequest(
            /** HTTP 方法；空值归一化为通配符 */
            String method,
            /** 请求路径 */
            @NotBlank String path,
            /** 路径匹配方式 */
            PasswordChangePathMatch match
    ) {
        public PasswordChangeAllowedRequest {
            method = normalizeMethod(method);
            path = path == null ? null : path.trim();
            match = match == null ? PasswordChangePathMatch.EXACT : match;
        }

        private static String normalizeMethod(String value) {
            if (value == null || value.isBlank()) {
                return "*";
            }
            return value.trim().toUpperCase(Locale.ROOT);
        }
    }

    public enum PasswordChangePathMatch {
        EXACT,
        PREFIX
    }
}
