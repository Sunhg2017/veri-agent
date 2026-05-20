package com.songhg.veri.agent.auth.infrastructure.mapper;

import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    AuthUserRow findEnabledUserByUsername(@Param("username") String username);

    AuthUserRow findEnabledUserById(@Param("userId") UUID userId);

    void changePassword(
            @Param("userId") UUID userId,
            @Param("passwordHash") String passwordHash,
            @Param("updatedBy") UUID updatedBy
    );

    void createSession(AuthSessionDraft draft);

    boolean isSessionActive(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("authVersion") long authVersion,
            @Param("now") Instant now
    );

    AuthSessionRecord findByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);

    void revokeSession(
            @Param("sessionId") UUID sessionId,
            @Param("revokedBy") UUID revokedBy,
            @Param("reason") String reason
    );

    int cleanupSessions(
            @Param("expiresBefore") Instant expiresBefore,
            @Param("revokedBefore") Instant revokedBefore
    );
}
