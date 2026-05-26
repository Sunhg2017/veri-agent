package com.songhg.veri.agent.document.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentWebhookIngressGuard {

    private static final long DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 60;
    private static final int MAX_COUNTERS_BEFORE_SWEEP = 10_000;

    private final DocumentInputProperties properties;
    private final ConcurrentHashMap<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public DocumentWebhookIngressGuard(DocumentInputProperties properties) {
        this.properties = properties;
    }

    public String resolveClientIp(String remoteAddress, String forwardedFor, String realIp) {
        String remoteIp = normalizeIpToken(remoteAddress);
        if (!StringUtils.hasText(remoteIp)) {
            return "unknown";
        }
        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }
        return firstValidForwardedIp(forwardedFor)
                .or(() -> parseAddress(normalizeIpToken(realIp)).map(InetAddress::getHostAddress))
                .orElse(remoteIp);
    }

    public boolean isIpAllowed(String sourceCode, String clientIp) {
        List<CidrBlock> allowlist = allowedCidrs(sourceCode);
        if (allowlist.isEmpty()) {
            return true;
        }
        Optional<InetAddress> address = parseAddress(normalizeIpToken(clientIp));
        return address.isPresent() && allowlist.stream().anyMatch(block -> block.matches(address.get()));
    }

    public boolean ipAllowlistEnabled(String sourceCode) {
        return !allowedCidrs(sourceCode).isEmpty();
    }

    public boolean ipAllowlistConfigured() {
        Map<String, String> sourceCidrs = properties.webhookSourceAllowedCidrs();
        return StringUtils.hasText(properties.webhookAllowedCidrs())
                || (sourceCidrs != null && sourceCidrs.values().stream().anyMatch(StringUtils::hasText));
    }

    public boolean trustedProxyCidrsConfigured() {
        return StringUtils.hasText(properties.webhookTrustedProxyCidrs());
    }

    public boolean rateLimitEnabled() {
        return rateLimitMaxRequests() > 0;
    }

    public int rateLimitMaxRequests() {
        return Math.max(0, properties.webhookRateLimitMaxRequests());
    }

    public long rateLimitWindowSeconds() {
        return properties.webhookRateLimitWindowSeconds() <= 0
                ? DEFAULT_RATE_LIMIT_WINDOW_SECONDS
                : properties.webhookRateLimitWindowSeconds();
    }

    public RateLimitDecision checkRateLimit(String sourceCode, String clientIp, String idempotencyKey) {
        int limit = rateLimitMaxRequests();
        if (limit <= 0) {
            return RateLimitDecision.allowed(limit, rateLimitWindowSeconds());
        }
        long windowSeconds = rateLimitWindowSeconds();
        long now = Instant.now().getEpochSecond();
        List<RateLimitKey> keys = new ArrayList<>();
        keys.add(new RateLimitKey("sourceCode", "source:" + normalizeKey(sourceCode)));
        if (StringUtils.hasText(clientIp)) {
            keys.add(new RateLimitKey("remoteIp", "remoteIp:" + normalizeKey(clientIp)));
        }
        if (StringUtils.hasText(idempotencyKey)) {
            keys.add(new RateLimitKey("idempotencyKey",
                    "idempotencyKey:" + normalizeKey(sourceCode) + ":" + normalizeKey(idempotencyKey)));
        }
        for (RateLimitKey key : keys) {
            int count = increment(key.value(), now, windowSeconds);
            if (count > limit) {
                return RateLimitDecision.rejected(key.dimension(), limit, windowSeconds);
            }
        }
        if (rateWindows.size() > MAX_COUNTERS_BEFORE_SWEEP) {
            sweepExpired(now, windowSeconds);
        }
        return RateLimitDecision.allowed(limit, windowSeconds);
    }

    private int increment(String key, long now, long windowSeconds) {
        long windowStart = now - (now % windowSeconds);
        RateWindow updated = rateWindows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartEpochSecond() != windowStart) {
                return new RateWindow(windowStart, 1);
            }
            return new RateWindow(windowStart, existing.count() + 1);
        });
        return updated == null ? 1 : updated.count();
    }

    private void sweepExpired(long now, long windowSeconds) {
        long oldestCurrentWindow = now - (now % windowSeconds);
        rateWindows.entrySet().removeIf(entry -> entry.getValue().windowStartEpochSecond() < oldestCurrentWindow);
    }

    private boolean isTrustedProxy(String remoteIp) {
        List<CidrBlock> trustedProxies = parseCidrs(properties.webhookTrustedProxyCidrs());
        if (trustedProxies.isEmpty()) {
            return false;
        }
        Optional<InetAddress> address = parseAddress(remoteIp);
        return address.isPresent() && trustedProxies.stream().anyMatch(block -> block.matches(address.get()));
    }

    private Optional<String> firstValidForwardedIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return Optional.empty();
        }
        for (String token : forwardedFor.split(",")) {
            String normalized = normalizeIpToken(token);
            if (parseAddress(normalized).isPresent()) {
                return Optional.of(parseAddress(normalized).orElseThrow().getHostAddress());
            }
        }
        return Optional.empty();
    }

    private List<CidrBlock> allowedCidrs(String sourceCode) {
        List<CidrBlock> cidrs = new ArrayList<>(parseCidrs(properties.webhookAllowedCidrs()));
        String sourceCidrs = sourceAllowedCidrs(sourceCode);
        if (StringUtils.hasText(sourceCidrs)) {
            cidrs.addAll(parseCidrs(sourceCidrs));
        }
        return cidrs;
    }

    private String sourceAllowedCidrs(String sourceCode) {
        Map<String, String> configured = properties.webhookSourceAllowedCidrs();
        if (configured == null || configured.isEmpty() || !StringUtils.hasText(sourceCode)) {
            return null;
        }
        return configured.entrySet().stream()
                .filter(entry -> sourceCode.trim().equalsIgnoreCase(entry.getKey().trim()))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<CidrBlock> parseCidrs(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<CidrBlock> cidrs = new ArrayList<>();
        for (String token : value.split("[,;\\s]+")) {
            if (StringUtils.hasText(token)) {
                cidrs.add(CidrBlock.parse(token.trim()));
            }
        }
        return cidrs;
    }

    private Optional<InetAddress> parseAddress(String value) {
        if (!StringUtils.hasText(value) || "unknown".equalsIgnoreCase(value.trim())) {
            return Optional.empty();
        }
        return parseLiteralAddress(value.trim());
    }

    private static Optional<InetAddress> parseLiteralAddress(String value) {
        if (!StringUtils.hasText(value) || !isIpLiteral(value.trim())) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value.trim()));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static boolean isIpLiteral(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$")) {
            return true;
        }
        return trimmed.contains(":") && trimmed.matches("^[0-9A-Fa-f:.%]+$");
    }

    private String normalizeIpToken(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String token = value.trim().replace("\"", "");
        if (token.startsWith("[")) {
            int end = token.indexOf(']');
            return end > 0 ? token.substring(1, end) : token;
        }
        long colonCount = token.chars().filter(ch -> ch == ':').count();
        if (colonCount == 1 && token.contains(".")) {
            return token.substring(0, token.indexOf(':'));
        }
        return token;
    }

    private String normalizeKey(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    public record RateLimitDecision(
            /** 是否允许当前请求继续处理 */
            boolean allowed,
            /** 命中的限流维度 */
            String dimension,
            /** 当前限流窗口允许的最大请求数 */
            int limit,
            /** 当前限流窗口秒数 */
            long windowSeconds
    ) {
        static RateLimitDecision allowed(int limit, long windowSeconds) {
            return new RateLimitDecision(true, null, limit, windowSeconds);
        }

        static RateLimitDecision rejected(String dimension, int limit, long windowSeconds) {
            return new RateLimitDecision(false, dimension, limit, windowSeconds);
        }
    }

    private record RateLimitKey(String dimension, String value) {
    }

    private record RateWindow(long windowStartEpochSecond, int count) {
    }

    private record CidrBlock(byte[] networkAddress, int prefixBits) {
        static CidrBlock parse(String value) {
            String[] parts = value.split("/", 2);
            InetAddress address = parseLiteralAddress(parts[0])
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "webhook IP/CIDR 配置无效: " + value));
            int maxBits = address.getAddress().length * 8;
            int prefix = maxBits;
            if (parts.length == 2) {
                try {
                    prefix = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    throw new BusinessException(ErrorCode.INVALID_STATE, "webhook IP/CIDR 配置无效: " + value);
                }
            }
            if (prefix < 0 || prefix > maxBits) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "webhook IP/CIDR 配置无效: " + value);
            }
            return new CidrBlock(address.getAddress(), prefix);
        }

        boolean matches(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != networkAddress.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidateAddress[i] != networkAddress[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (candidateAddress[fullBytes] & mask) == (networkAddress[fullBytes] & mask);
        }
    }
}
