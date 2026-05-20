package com.songhg.veri.agent.common.security;

import com.songhg.veri.agent.asset.config.AssetProperties;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ModelAccessProperties modelAccessProperties;
    private final AssetProperties assetProperties;
    private final DocumentInputProperties documentInputProperties;

    public ServiceTokenAuthenticationFilter(
            ModelAccessProperties modelAccessProperties,
            AssetProperties assetProperties,
            DocumentInputProperties documentInputProperties
    ) {
        this.modelAccessProperties = modelAccessProperties;
        this.assetProperties = assetProperties;
        this.documentInputProperties = documentInputProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/document-input/")) {
            return path.equals("/api/v1/document-input/health")
                    || path.startsWith("/api/v1/document-input/webhooks/")
                    || !TokenSecurity.constantTimeEquals(documentInputProperties.serviceToken(), bearerToken(request));
        }
        if (path.startsWith("/api/v1/asset/")) {
            return path.equals("/api/v1/asset/health")
                    || !TokenSecurity.constantTimeEquals(assetProperties.serviceToken(), bearerToken(request));
        }
        if (path.startsWith("/api/v1/model-access/")) {
            return path.equals("/api/v1/model-access/health")
                    || !TokenSecurity.constantTimeEquals(modelAccessProperties.serviceToken(), bearerToken(request));
        }
        return !path.startsWith("/api/v1/model-access/")
                && !path.startsWith("/api/v1/asset/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.endsWith("/health") && !path.startsWith("/api/v1/document-input/sources/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authenticateServiceCaller(request, response, serviceToken(path), serviceName(path))) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String serviceToken(String path) {
        if (path.startsWith("/api/v1/model-access/")) {
            return modelAccessProperties.serviceToken();
        }
        if (path.startsWith("/api/v1/document-input/")) {
            return documentInputProperties.serviceToken();
        }
        return assetProperties.serviceToken();
    }

    private String serviceName(String path) {
        if (path.startsWith("/api/v1/model-access/")) {
            return "model-access";
        }
        if (path.startsWith("/api/v1/document-input/")) {
            return "document-input";
        }
        return "asset-service";
    }

    private boolean authenticateServiceCaller(
            HttpServletRequest request,
            HttpServletResponse response,
            String expectedToken,
            String serviceName
    ) throws IOException {
        if (!StringUtils.hasText(expectedToken)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, serviceName + " 服务令牌未配置");
        }
        String actualToken = bearerToken(request);
        if (!TokenSecurity.constantTimeEquals(expectedToken, actualToken)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"服务令牌无效\"}");
            return false;
        }
        String callerService = headerOrDefault(request, "X-Caller-Service", serviceName);
        String delegatedUserId = headerOrDefault(request, "X-Delegated-User-Id", "system");
        ServicePrincipal principal = new ServicePrincipal(callerService, delegatedUserId);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return "";
    }

    private String headerOrDefault(HttpServletRequest request, String headerName, String defaultValue) {
        String value = request.getHeader(headerName);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
