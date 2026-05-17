package com.songhg.veri.agent.auth.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthIdentityStore;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class PostgresAuthIdentityStore implements AuthIdentityStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresAuthIdentityStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUsername(String username) {
        List<AuthUserRecord> users = jdbcTemplate.query("""
                select
                    u.id as user_id,
                    u.username,
                    u.display_name,
                    u.email,
                    u.password_hash,
                    u.must_change_password,
                    u.auth_version,
                    coalesce(string_agg(distinct b.role_code, ','), '') as role_codes
                from iam_user u
                left join rbac_role_binding b on b.subject_type = 'USER'
                    and b.subject_id = u.id
                    and b.status = 'ENABLED'
                    and b.deleted_at is null
                    and (b.expires_at is null or b.expires_at > now())
                where u.username = :username
                  and u.status = 'ENABLED'
                  and u.deleted_at is null
                group by u.id
                limit 1
                """,
                Map.of("username", username),
                this::mapUser
        );
        return users.stream().findFirst();
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUserId(UUID userId) {
        List<AuthUserRecord> users = jdbcTemplate.query("""
                select
                    u.id as user_id,
                    u.username,
                    u.display_name,
                    u.email,
                    u.password_hash,
                    u.must_change_password,
                    u.auth_version,
                    coalesce(string_agg(distinct b.role_code, ','), '') as role_codes
                from iam_user u
                left join rbac_role_binding b on b.subject_type = 'USER'
                    and b.subject_id = u.id
                    and b.status = 'ENABLED'
                    and b.deleted_at is null
                    and (b.expires_at is null or b.expires_at > now())
                where u.id = :userId
                  and u.status = 'ENABLED'
                  and u.deleted_at is null
                group by u.id
                limit 1
                """,
                Map.of("userId", userId),
                this::mapUser
        );
        return users.stream().findFirst();
    }

    @Override
    public void changePassword(UUID userId, String passwordHash, UUID updatedBy) {
        jdbcTemplate.update("""
                update iam_user
                set password_hash = :passwordHash,
                    must_change_password = false,
                    auth_version = auth_version + 1,
                    updated_by = :updatedBy,
                    updated_at = now()
                where id = :userId
                  and status = 'ENABLED'
                  and deleted_at is null
                """,
                Map.of(
                        "userId", userId,
                        "passwordHash", passwordHash,
                        "updatedBy", updatedBy
                )
        );
    }

    private AuthUserRecord mapUser(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuthUserRecord(
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getBoolean("must_change_password"),
                rs.getLong("auth_version"),
                splitRoles(rs.getString("role_codes"))
        );
    }

    private List<String> splitRoles(String roleCodes) {
        if (roleCodes == null || roleCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roleCodes.split(","))
                .filter(role -> !role.isBlank())
                .sorted()
                .toList();
    }
}
