package com.songhg.veri.agent.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.security.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAgeSeconds
) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of("http://localhost:5173", "http://127.0.0.1:5173")
                : List.copyOf(allowedOrigins);
        allowedOriginPatterns = allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
        allowedMethods = allowedMethods == null
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : List.copyOf(allowedMethods);
        allowedHeaders = allowedHeaders == null
                ? List.of("Authorization", "Content-Type", "X-Trace-Id", "X-Caller-Service", "X-Delegated-User-Id",
                        "X-VA-Timestamp", "X-VA-Signature", "X-VA-Event-Id")
                : List.copyOf(allowedHeaders);
        exposedHeaders = exposedHeaders == null ? List.of("X-Trace-Id") : List.copyOf(exposedHeaders);
        maxAgeSeconds = maxAgeSeconds <= 0 ? 3600 : maxAgeSeconds;
    }
}
