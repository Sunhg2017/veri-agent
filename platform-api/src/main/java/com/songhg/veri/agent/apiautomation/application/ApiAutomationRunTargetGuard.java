package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Normalizes and classifies runner base URLs before any raw target value can reach persistence or audit payloads.
 */
final class ApiAutomationRunTargetGuard {

    private static final int RUNNER_BASE_URL_MAX_CHARS = 512;
    private static final Set<String> BLOCKED_RUN_TARGET_HOSTS = Set.of("localhost", "metadata.google.internal");
    private static final Pattern IPV4_LITERAL_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final ApiAutomationProperties properties;

    ApiAutomationRunTargetGuard(ApiAutomationProperties properties) {
        this.properties = properties;
    }

    boolean allowedBaseUrlConfigured() {
        return !allowedBaseUrlPatterns().isEmpty();
    }

    /**
     * Returns the normalized target used for runner dispatch plus digest/host metadata safe for storage.
     */
    RunTarget validateRunTarget(String rawBaseUrl) {
        String bounded = SensitiveTextSanitizer.boundedNullableText(rawBaseUrl, RUNNER_BASE_URL_MAX_CHARS);
        if (!StringUtils.hasText(bounded)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必填");
        }
        URI uri;
        try {
            uri = new URI(bounded);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必须是合法 HTTP/HTTPS URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 仅支持 http/https");
        }
        if (StringUtils.hasText(uri.getRawUserInfo()) || StringUtils.hasText(uri.getRawQuery())
                || StringUtils.hasText(uri.getRawFragment())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 不允许携带 userInfo/query/fragment");
        }
        String host = normalizedHost(uri.getHost());
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必须包含 host");
        }
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "";
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        int port = uri.getPort();
        String authority = port > 0 ? host + ":" + port : host;
        String normalized = scheme + "://" + authority + path;
        return new RunTarget(
                normalized,
                host,
                SensitiveTextSanitizer.sha256Hex(normalized),
                blockedTargetHost(host),
                allowedTargetHost(host)
        );
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

    private boolean blockedTargetHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_RUN_TARGET_HOSTS.contains(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }
        if ("169.254.169.254".equals(normalized) || "::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized)) {
            return true;
        }
        if (IPV4_LITERAL_PATTERN.matcher(normalized).matches()) {
            return privateIpv4(normalized);
        }
        return normalized.startsWith("[") || normalized.endsWith(".local");
    }

    private boolean privateIpv4(String host) {
        String[] parts = host.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        if (first == 10 || first == 127 || first == 0 || first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        return first == 192 && second == 168;
    }

    private boolean allowedTargetHost(String host) {
        List<String> patterns = allowedBaseUrlPatterns();
        if (patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> hostMatchesPattern(host, pattern));
    }

    private List<String> allowedBaseUrlPatterns() {
        if (!StringUtils.hasText(properties.runnerAllowedBaseUrlPatterns())) {
            return List.of();
        }
        return List.of(properties.runnerAllowedBaseUrlPatterns().split(",")).stream()
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean hostMatchesPattern(String host, String pattern) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String normalizedPattern = pattern;
        if (normalizedPattern.startsWith("http://") || normalizedPattern.startsWith("https://")) {
            try {
                normalizedPattern = normalizedHost(new URI(normalizedPattern).getHost());
            } catch (URISyntaxException exception) {
                return false;
            }
        }
        if (normalizedPattern.startsWith("*.")) {
            String suffix = normalizedPattern.substring(1);
            return normalizedHost.endsWith(suffix) && normalizedHost.length() > suffix.length();
        }
        return normalizedHost.equals(normalizedPattern);
    }

    record RunTarget(
            String normalizedBaseUrl,
            String host,
            String digest,
            boolean blocked,
            boolean allowed
    ) {
    }
}
