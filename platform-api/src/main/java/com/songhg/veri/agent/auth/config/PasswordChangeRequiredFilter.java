package com.songhg.veri.agent.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthProperties;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnBean(AuthTokenService.class)
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final PasswordChangeRequiredPolicy passwordChangeRequiredPolicy;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper, AuthProperties authProperties) {
        this.objectMapper = objectMapper;
        this.passwordChangeRequiredPolicy = new PasswordChangeRequiredPolicy(authProperties.passwordChangeRequired());
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
                || passwordChangeRequiredPolicy.allows(request)) {
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
}
