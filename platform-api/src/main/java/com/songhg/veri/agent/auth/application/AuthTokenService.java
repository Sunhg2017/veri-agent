package com.songhg.veri.agent.auth.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.security.TokenSecurity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_TOKEN_SECRET_BYTES = 32;

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthSessionStore sessionStore;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AuthTokenService(
            AuthProperties properties,
            ObjectMapper objectMapper,
            AuthSessionStore sessionStore
    ) {
        this(properties, objectMapper, sessionStore, Clock.systemUTC());
    }

    AuthTokenService(
            AuthProperties properties,
            ObjectMapper objectMapper,
            AuthSessionStore sessionStore,
            Clock clock
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessionStore = sessionStore;
        this.clock = clock;
    }

    public IssuedToken issue(AuthUserRecord user) {
        ensureTokenSecret();
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plusSeconds(properties.accessTokenTtlMinutes() * 60);
        String refreshToken = randomToken();
        TokenPayload payload = new TokenPayload(
                user.userId(),
                sessionId,
                user.username(),
                user.displayName(),
                user.email(),
                user.mustChangePassword(),
                user.authVersion(),
                user.roles(),
                expiresAt.getEpochSecond()
        );
        String encodedPayload = base64Url(writePayload(payload));
        String signature = sign(encodedPayload);
        String accessToken = encodedPayload + "." + signature;
        sessionStore.create(new AuthSessionDraft(
                sessionId,
                user.userId(),
                hashToken(accessToken),
                hashToken(refreshToken),
                user.authVersion(),
                expiresAt
        ));
        return new IssuedToken(accessToken, refreshToken, sessionId, "Bearer", expiresAt);
    }

    public Optional<AuthUserPrincipal> verify(String token) {
        ensureTokenSecret();
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String expectedSignature = sign(parts[0]);
        if (!TokenSecurity.constantTimeEquals(expectedSignature, parts[1])) {
            return Optional.empty();
        }
        TokenPayload payload = readPayload(parts[0]);
        if (payload.expiresAtEpochSeconds() <= Instant.now(clock).getEpochSecond()) {
            return Optional.empty();
        }
        if (!sessionStore.isActive(
                payload.sessionId(),
                payload.userId(),
                payload.authVersion(),
                Instant.now(clock)
        )) {
            return Optional.empty();
        }
        return Optional.of(new AuthUserPrincipal(
                payload.userId(),
                payload.sessionId(),
                payload.username(),
                payload.displayName(),
                payload.email(),
                payload.mustChangePassword(),
                payload.authVersion(),
                payload.roles()
        ));
    }

    public String hashToken(String token) {
        try {
            return base64Url(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("cannot hash auth token", exception);
        }
    }

    private byte[] writePayload(TokenPayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize auth token payload", exception);
        }
    }

    private TokenPayload readPayload(String encodedPayload) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encodedPayload);
            return objectMapper.readValue(payload, TokenPayload.class);
        } catch (IllegalArgumentException | IOException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效或已过期");
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return base64Url(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("cannot sign auth token", exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void ensureTokenSecret() {
        if (!StringUtils.hasText(properties.tokenSecret())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "认证签名密钥未配置");
        }
        int secretBytes = properties.tokenSecret().getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_TOKEN_SECRET_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "认证签名密钥长度不足，至少需要 32 字节随机值");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return base64Url(bytes);
    }

    public record IssuedToken(
            /** 签发给客户端的访问令牌 */
            String accessToken,
            /** 用于刷新会话的刷新令牌 */
            String refreshToken,
            /** 本次登录创建的会话 ID */
            UUID sessionId,
            /** 令牌类型，当前固定为 Bearer */
            String tokenType,
            /** 访问令牌过期时间 */
            Instant expiresAt
    ) {
    }

    record TokenPayload(
            UUID userId,
            UUID sessionId,
            String username,
            String displayName,
            String email,
            boolean mustChangePassword,
            long authVersion,
            List<String> roles,
            long expiresAtEpochSeconds
    ) {
    }
}
