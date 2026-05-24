package com.songhg.veri.agent.auth.application;

import com.songhg.veri.agent.auth.application.ChangePasswordRequest;
import com.songhg.veri.agent.auth.application.LoginRequest;
import com.songhg.veri.agent.auth.application.LogoutRequest;
import com.songhg.veri.agent.auth.application.RefreshTokenRequest;
import com.songhg.veri.agent.auth.application.ChangePasswordResponse;
import com.songhg.veri.agent.auth.application.LoginResponse;
import com.songhg.veri.agent.auth.application.LogoutResponse;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import com.songhg.veri.agent.auth.domain.AuthIdentityStore;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$Jzq9ZfxqSLsHwZwYBrB7F.cxtw.TCZauDIX83dGCLMGAxXAjyqdJy";

    private final AuthIdentityStore identityStore;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;
    private final AuthSessionStore sessionStore;
    private final AuditLogWriter auditLogWriter;

    public AuthService(
            AuthIdentityStore identityStore,
            PasswordEncoder passwordEncoder,
            AuthTokenService tokenService,
            AuthSessionStore sessionStore,
            AuditLogWriter auditLogWriter
    ) {
        this.identityStore = identityStore;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
        this.auditLogWriter = auditLogWriter;
    }

    public LoginResponse login(LoginRequest request) {
        AuthUserRecord user = identityStore.findEnabledByUsername(request.username()).orElse(null);
        String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches) {
            auditLogWriter.record(AuditLogWriter.failed(
                    null,
                    "登录失败",
                    "user",
                    request.username(),
                    "账号或密码错误"
            ));
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        AuthTokenService.IssuedToken token = tokenService.issue(user);
        auditLogWriter.record(AuditLogWriter.success(
                toPrincipal(user, token.sessionId()),
                "登录成功",
                "user",
                user.userId().toString(),
                user.username()
        ));
        return toLoginResponse(user, token);
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        AuthSessionRecord session = sessionStore.findByRefreshTokenHash(tokenService.hashToken(request.refreshToken()))
                .filter(record -> record.activeAt(java.time.Instant.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期"));

        AuthUserRecord user = identityStore.findEnabledByUserId(session.userId())
                .filter(record -> record.authVersion() == session.authVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号状态已变化，请重新登录"));

        sessionStore.revoke(session.sessionId(), user.userId(), "refresh rotated");
        return toLoginResponse(user, tokenService.issue(user));
    }

    public LogoutResponse logout(AuthUserPrincipal principal, LogoutRequest request) {
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "user logout"
                : request.reason().trim();
        sessionStore.revoke(principal.sessionId(), principal.userId(), reason);
        return new LogoutResponse(true, principal.sessionId());
    }

    public ChangePasswordResponse changePassword(AuthUserPrincipal principal, ChangePasswordRequest request) {
        if (request.oldPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "新密码不能与旧密码相同");
        }

        AuthUserRecord user = identityStore.findEnabledByUserId(principal.userId())
                .filter(record -> record.authVersion() == principal.authVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号状态已变化，请重新登录"));

        if (!passwordEncoder.matches(request.oldPassword(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "旧密码不正确");
        }

        identityStore.changePassword(principal.userId(), passwordEncoder.encode(request.newPassword()), principal.userId());
        auditLogWriter.record(AuditLogWriter.success(
                principal,
                "修改密码",
                "user",
                principal.userId().toString(),
                principal.username()
        ));
        sessionStore.revoke(principal.sessionId(), principal.userId(), "password changed");
        return new ChangePasswordResponse(true, true, principal.userId());
    }

    private LoginResponse toLoginResponse(AuthUserRecord user, AuthTokenService.IssuedToken token) {
        return new LoginResponse(
                token.accessToken(),
                token.refreshToken(),
                token.sessionId(),
                token.tokenType(),
                token.expiresAt(),
                user.userId(),
                user.username(),
                user.displayName(),
                user.email(),
                user.mustChangePassword(),
                user.roles()
        );
    }

    private AuthUserPrincipal toPrincipal(AuthUserRecord user, java.util.UUID sessionId) {
        return new AuthUserPrincipal(
                user.userId(),
                sessionId,
                user.username(),
                user.displayName(),
                user.email(),
                user.mustChangePassword(),
                user.authVersion(),
                user.roles()
        );
    }
}
