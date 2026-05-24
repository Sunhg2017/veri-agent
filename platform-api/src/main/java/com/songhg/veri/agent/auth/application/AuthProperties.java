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
        String tokenSecret,
        @Min(1) long accessTokenTtlMinutes,
        boolean sessionCleanupEnabled,
        @Min(1) long sessionCleanupRetentionSeconds,
        @Valid PasswordChangeRequired passwordChangeRequired
) {
    public AuthProperties {
        passwordChangeRequired = passwordChangeRequired == null
                ? PasswordChangeRequired.defaults()
                : passwordChangeRequired;
    }

    public record PasswordChangeRequired(
            @Valid List<PasswordChangeAllowedRequest> allowedRequests
    ) {
        public PasswordChangeRequired {
            allowedRequests = allowedRequests == null
                    ? List.of()
                    : List.copyOf(allowedRequests);
        }

        public static PasswordChangeRequired defaults() {
            // Fail-safe defaults mirror application-platform.yml to avoid locking users out of password recovery.
            return new PasswordChangeRequired(List.of(
                    allow(null, "/actuator/", PasswordChangePathMatch.PREFIX),
                    allow(null, "/v3/api-docs/", PasswordChangePathMatch.PREFIX),
                    allow(null, "/swagger-ui/", PasswordChangePathMatch.PREFIX),
                    allow(null, "/swagger-ui.html", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/model-access/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/asset/health", PasswordChangePathMatch.EXACT),
                    allow("GET", "/api/v1/document-input/health", PasswordChangePathMatch.EXACT),
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
            String method,
            @NotBlank String path,
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
