package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UiE2eRunEnvironmentResolver {

    private final ManagementStore managementStore;

    public UiE2eRunEnvironmentResolver(ManagementStore managementStore) {
        this.managementStore = managementStore;
    }

    /**
     * Resolves a UI target base URL from an environment reference and keeps only aggregate-safe metadata.
     */
    public ResolvedUiE2eBaseUrl resolve(String projectId, String baseUrlRef) {
        String normalizedRef = SensitiveTextSanitizer.boundedNullableText(baseUrlRef, 128);
        if (!StringUtils.hasText(normalizedRef) || !normalizedRef.startsWith("env:")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        String environmentKey = normalizedRef.substring("env:".length()).trim();
        if (!StringUtils.hasText(environmentKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        UUID projectUuid;
        try {
            projectUuid = UUID.fromString(projectId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
        EnvironmentRuntimeRef scope = managementStore.findEnvironmentRuntimeRef(ManagementStoreParams.of(
                "keyword", SensitiveTextSanitizer.boundedNullableText(environmentKey, 128),
                "projectId", projectUuid
        ));
        if (scope == null || !projectUuid.equals(scope.projectId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
        if (!"ENABLED".equals(scope.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        EnvironmentRef environmentRef = managementStore.findEnvironmentRef(ManagementStoreParams.of(
                "keyword", SensitiveTextSanitizer.boundedNullableText(environmentKey, 128)
        ));
        if (environmentRef == null || !projectUuid.equals(environmentRef.projectId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
        EnvironmentConnectivityTargetRow target = managementStore.findEnvironmentConnectivityTarget(ManagementStoreParams.of(
                "keyword", SensitiveTextSanitizer.boundedNullableText(environmentKey, 128)
        ));
        if (target == null || !StringUtils.hasText(target.webUrl())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        String normalizedBaseUrl = normalizedRuntimeBaseUrl(target.webUrl());
        URI uri = uri(normalizedBaseUrl);
        String host = normalizedHost(uri.getHost());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("environmentKey", scope.code());
        summary.put("environmentName", scope.name());
        summary.put("baseUrlRefDigest", "sha256:" + SensitiveTextSanitizer.sha256Hex(normalizedRef));
        summary.put("baseUrlHostDigest", "sha256:" + SensitiveTextSanitizer.sha256Hex(host));
        summary.put("rawBaseUrlStored", false);
        summary.put("rawBaseUrlExported", false);
        return new ResolvedUiE2eBaseUrl(normalizedBaseUrl, host, normalizedRef, scope.code(), summary);
    }

    private String normalizedRuntimeBaseUrl(String rawBaseUrl) {
        String bounded = SensitiveTextSanitizer.boundedNullableText(rawBaseUrl, 512);
        if (!StringUtils.hasText(bounded)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        URI uri = uri(bounded);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        if (StringUtils.hasText(uri.getRawUserInfo())
                || StringUtils.hasText(uri.getRawQuery())
                || StringUtils.hasText(uri.getRawFragment())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        String host = normalizedHost(uri.getHost());
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "";
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        int port = uri.getPort();
        String authority = port > 0 ? host + ":" + port : host;
        return scheme + "://" + authority + path;
    }

    private URI uri(String rawValue) {
        try {
            return new URI(rawValue);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
    }

    private String normalizedHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        try {
            return IDN.toASCII(host.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    public record ResolvedUiE2eBaseUrl(
            String normalizedBaseUrl,
            String normalizedHost,
            String baseUrlRef,
            String environmentKey,
            Map<String, Object> summary
    ) {
    }
}
