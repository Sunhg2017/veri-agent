package com.songhg.veri.agent.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnBean(AuthTokenService.class)
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof AuthUserPrincipal user)
                || !user.mustChangePassword()
                || isAllowedBeforePasswordChange(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(ErrorCode.PASSWORD_CHANGE_REQUIRED.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(
                        ErrorCode.PASSWORD_CHANGE_REQUIRED.name(),
                        "首次登录必须先修改密码",
                        TraceContext.getTraceId(),
                        null
                )
        );
    }

    private boolean isAllowedBeforePasswordChange(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        if (path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html")) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && (
                path.equals("/api/v1/health")
                        || path.equals("/api/v1/model-access/health")
                        || path.equals("/api/v1/asset/health")
                        || path.equals("/api/v1/document-input/health")
                        || path.equals("/api/v1/auth/me"))) {
            return true;
        }
        return HttpMethod.POST.matches(method) && (
                path.equals("/api/v1/auth/login")
                        || path.equals("/api/v1/auth/refresh")
                        || path.equals("/api/v1/auth/logout")
                        || path.equals("/api/v1/auth/change-password"));
    }
}
