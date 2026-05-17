package com.songhg.veri.agent.modelaccess.security;

import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String DELEGATED_USER_HEADER = "X-Delegated-User-Id";
    public static final String CALLER_SERVICE_HEADER = "X-Caller-Service";

    private final ModelAccessProperties properties;

    public ServiceTokenAuthenticationFilter(ModelAccessProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = bearerToken(authorization);
        if (StringUtils.hasText(token) && TokenSecurity.constantTimeEquals(properties.serviceToken(), token)) {
            String callerService = headerOrDefault(request, CALLER_SERVICE_HEADER, "unknown-service");
            String delegatedUserId = headerOrDefault(request, DELEGATED_USER_HEADER, "system");
            ServicePrincipal principal = new ServicePrincipal(callerService, delegatedUserId);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_WP2_CALLER"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String bearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String headerOrDefault(HttpServletRequest request, String header, String defaultValue) {
        String value = request.getHeader(header);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
