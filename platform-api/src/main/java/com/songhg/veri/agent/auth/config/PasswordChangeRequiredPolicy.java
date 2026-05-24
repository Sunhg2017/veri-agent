package com.songhg.veri.agent.auth.config;

import com.songhg.veri.agent.auth.application.AuthProperties.PasswordChangeAllowedRequest;
import com.songhg.veri.agent.auth.application.AuthProperties.PasswordChangePathMatch;
import com.songhg.veri.agent.auth.application.AuthProperties.PasswordChangeRequired;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;

final class PasswordChangeRequiredPolicy {

    private static final String ANY_METHOD = "*";

    private final PasswordChangeRequired properties;

    PasswordChangeRequiredPolicy(PasswordChangeRequired properties) {
        this.properties = properties == null ? PasswordChangeRequired.defaults() : properties;
    }

    boolean allows(HttpServletRequest request) {
        // CORS preflight must pass through before the user can complete the forced password change.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();
        return properties.allowedRequests().stream()
                .anyMatch(rule -> matches(rule, method, path));
    }

    private boolean matches(PasswordChangeAllowedRequest rule, String method, String path) {
        return methodMatches(rule.method(), method) && pathMatches(rule, path);
    }

    private boolean methodMatches(String allowedMethod, String requestMethod) {
        return ANY_METHOD.equals(allowedMethod) || allowedMethod.equalsIgnoreCase(requestMethod);
    }

    private boolean pathMatches(PasswordChangeAllowedRequest rule, String requestPath) {
        if (rule.match() == PasswordChangePathMatch.PREFIX) {
            return requestPath.startsWith(rule.path());
        }
        return requestPath.equals(rule.path());
    }
}
