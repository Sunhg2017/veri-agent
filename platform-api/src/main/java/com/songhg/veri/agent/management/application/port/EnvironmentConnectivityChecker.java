package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.management.application.view.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.application.view.EnvironmentConnectivityEndpointView;
import com.songhg.veri.agent.management.config.ManagementProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentConnectivityChecker {

    private final ManagementProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public EnvironmentConnectivityChecker(ManagementProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getEnvironmentConnectivityTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    EnvironmentConnectivityChecker(ManagementProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /**
     * Probes configured WEB/API URLs without following redirects or exposing credentials in the
     * returned display URL. The result is diagnostic only and does not mutate environment status.
     */
    public EnvironmentConnectivityCheckView check(String environment, String webUrl, String apiBaseUrl) {
        String checkedAt = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String traceId = TraceContext.getTraceId();
        List<ProbeTarget> targets = targets(webUrl, apiBaseUrl);
        if (targets.isEmpty()) {
            return new EnvironmentConnectivityCheckView(
                    environment,
                    "SKIPPED",
                    checkedAt,
                    null,
                    "未配置 webUrl 或 apiBaseUrl",
                    traceId,
                    List.of()
            );
        }
        if (!properties.isEnvironmentConnectivityCheckEnabled()) {
            return new EnvironmentConnectivityCheckView(
                    environment,
                    "SKIPPED",
                    checkedAt,
                    null,
                    "环境连通性探活已通过配置关闭",
                    traceId,
                    targets.stream()
                            .map(target -> new EnvironmentConnectivityEndpointView(
                                    target.label(),
                                    safeDisplayUrl(target.url()),
                                    "SKIPPED",
                                    null,
                                    null,
                                    "探活已关闭"
                            ))
                            .toList()
            );
        }

        Instant startedAt = Instant.now();
        List<EnvironmentConnectivityEndpointView> endpoints = new ArrayList<>();
        for (ProbeTarget target : targets) {
            endpoints.add(probe(target));
        }
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        boolean allUp = endpoints.stream().allMatch(endpoint -> "UP".equals(endpoint.status()));
        return new EnvironmentConnectivityCheckView(
                environment,
                allUp ? "UP" : "DOWN",
                checkedAt,
                latencyMs,
                allUp ? "全部环境地址已响应" : "一个或多个环境地址未返回可用状态",
                traceId,
                endpoints
        );
    }

    private EnvironmentConnectivityEndpointView probe(ProbeTarget target) {
        URI uri;
        try {
            uri = URI.create(target.url().trim());
        } catch (IllegalArgumentException exception) {
            return down(target.label(), "地址格式不合法", null, null, "环境地址格式不合法");
        }
        String displayUrl = displayUrl(uri);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!List.of("http", "https").contains(scheme)) {
            return down(target.label(), displayUrl, null, null, "环境地址需使用 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return down(target.label(), displayUrl, null, null, "环境地址需包含主机名");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            return down(target.label(), displayUrl, null, null, "环境地址不允许包含认证信息");
        }
        Instant startedAt = Instant.now();
        try {
            // A GET is intentional here: several deployment endpoints do not implement HEAD.
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(properties.getEnvironmentConnectivityTimeoutMs()))
                    .header("User-Agent", "veri-agent-environment-connectivity-check")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            int statusCode = response.statusCode();
            if (statusCode < 500) {
                return new EnvironmentConnectivityEndpointView(
                        target.label(),
                        displayUrl,
                        "UP",
                        latencyMs,
                        statusCode,
                        "目标已响应"
                );
            }
            return down(target.label(), displayUrl, latencyMs, statusCode, "目标返回服务端错误状态");
        } catch (HttpTimeoutException exception) {
            return down(target.label(), displayUrl, Duration.between(startedAt, Instant.now()).toMillis(), null, "目标在超时时间内未响应");
        } catch (IOException exception) {
            return down(target.label(), displayUrl, Duration.between(startedAt, Instant.now()).toMillis(), null, "目标未返回可用响应");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return down(target.label(), displayUrl, Duration.between(startedAt, Instant.now()).toMillis(), null, "探活请求被中断");
        }
    }

    private EnvironmentConnectivityEndpointView down(
            String target,
            String displayUrl,
            Long latencyMs,
            Integer statusCode,
            String message
    ) {
        return new EnvironmentConnectivityEndpointView(target, displayUrl, "DOWN", latencyMs, statusCode, message);
    }

    private List<ProbeTarget> targets(String webUrl, String apiBaseUrl) {
        List<ProbeTarget> targets = new ArrayList<>();
        if (hasText(webUrl)) {
            targets.add(new ProbeTarget("WEB", webUrl.trim()));
        }
        if (hasText(apiBaseUrl)) {
            targets.add(new ProbeTarget("API", apiBaseUrl.trim()));
        }
        return targets;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String safeDisplayUrl(String rawUrl) {
        try {
            return displayUrl(URI.create(rawUrl.trim()));
        } catch (IllegalArgumentException exception) {
            return "地址格式不合法";
        }
    }

    private String displayUrl(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost();
        if (scheme.isBlank() || host == null || host.isBlank()) {
            return "地址格式不合法";
        }
        StringBuilder display = new StringBuilder(scheme).append("://");
        if (host.contains(":") && !host.startsWith("[")) {
            display.append('[').append(host).append(']');
        } else {
            display.append(host);
        }
        if (uri.getPort() >= 0) {
            display.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        if (path != null && !path.isBlank()) {
            display.append(path);
        }
        return display.toString();
    }

    private record ProbeTarget(String label, String url) {
    }
}
