package com.songhg.veri.agent.auth.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class PostgresAuthSessionStore implements AuthSessionStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresAuthSessionStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(AuthSessionDraft draft) {
        jdbcTemplate.update("""
                insert into iam_session (
                    id,
                    user_id,
                    session_token_hash,
                    refresh_token_hash,
                    auth_version,
                    expires_at
                )
                values (
                    :sessionId,
                    :userId,
                    :accessTokenHash,
                    :refreshTokenHash,
                    :authVersion,
                    :expiresAt
                )
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", draft.sessionId())
                        .addValue("userId", draft.userId())
                        .addValue("accessTokenHash", draft.accessTokenHash())
                        .addValue("refreshTokenHash", draft.refreshTokenHash())
                        .addValue("authVersion", draft.authVersion())
                        .addValue("expiresAt", Timestamp.from(draft.expiresAt()))
        );
    }

    @Override
    public boolean isActive(UUID sessionId, UUID userId, long authVersion, Instant now) {
        Boolean active = jdbcTemplate.queryForObject("""
                select exists (
                    select 1
                    from iam_session s
                    join iam_user u on u.id = s.user_id
                        and u.deleted_at is null
                    where s.id = :sessionId
                      and s.user_id = :userId
                      and s.auth_version = :authVersion
                      and u.auth_version = :authVersion
                      and u.status = 'ENABLED'
                      and s.revoked_at is null
                      and s.expires_at > :now
                )
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId)
                        .addValue("authVersion", authVersion)
                        .addValue("now", Timestamp.from(now)),
                Boolean.class
        );
        return Boolean.TRUE.equals(active);
    }

    @Override
    public Optional<AuthSessionRecord> findByRefreshTokenHash(String refreshTokenHash) {
        List<AuthSessionRecord> sessions = jdbcTemplate.query("""
                select
                    id,
                    user_id,
                    refresh_token_hash,
                    auth_version,
                    expires_at,
                    revoked_at is not null as revoked
                from iam_session
                where refresh_token_hash = :refreshTokenHash
                limit 1
                """,
                Map.of("refreshTokenHash", refreshTokenHash),
                (rs, rowNum) -> new AuthSessionRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("refresh_token_hash"),
                        rs.getLong("auth_version"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getBoolean("revoked")
                )
        );
        return sessions.stream().findFirst();
    }

    @Override
    public void revoke(UUID sessionId, UUID revokedBy, String reason) {
        jdbcTemplate.update("""
                update iam_session
                set revoked_at = coalesce(revoked_at, now()),
                    revoked_by = coalesce(revoked_by, :revokedBy),
                    revoke_reason = coalesce(revoke_reason, :reason)
                where id = :sessionId
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("revokedBy", revokedBy)
                        .addValue("reason", reason)
        );
    }
}
