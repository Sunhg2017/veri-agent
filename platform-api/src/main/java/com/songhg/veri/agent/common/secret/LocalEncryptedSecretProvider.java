package com.songhg.veri.agent.common.secret;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("db")
public class LocalEncryptedSecretProvider implements SecretProvider {

    private final JdbcTemplate jdbcTemplate;
    private final SecretProviderProperties properties;
    private final SecretProviderAuditRecorder auditRecorder;

    public LocalEncryptedSecretProvider(
            JdbcTemplate jdbcTemplate,
            SecretProviderProperties properties,
            SecretProviderAuditRecorder auditRecorder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.auditRecorder = auditRecorder == null ? SecretProviderAuditRecorder.noop() : auditRecorder;
    }

    LocalEncryptedSecretProvider(JdbcTemplate jdbcTemplate, SecretProviderProperties properties) {
        this(jdbcTemplate, properties, SecretProviderAuditRecorder.noop());
    }

    @Override
    public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
        if (!StringUtils.hasText(secretRef)) {
            return Optional.empty();
        }
        SecretRow row;
        try {
            row = jdbcTemplate.queryForObject("""
                    select sr.secret_ref,
                           sr.purpose,
                           sr.scope_type,
                           sr.scope_id::text as scope_id,
                           sr.secret_version,
                           sp.provider_code,
                           sp.provider_type,
                           sl.cipher_text,
                           sl.iv,
                           sl.auth_tag,
                           sl.algorithm,
                           sl.master_key_version,
                           sl.status as local_status
                    from secret_reference sr
                    join secret_provider sp on sp.id = sr.provider_id
                    left join secret_local_store sl on sl.secret_ref_id = sr.id and sl.deleted_at is null
                    where sr.secret_ref = ?
                      and sr.status = 'ACTIVE'
                      and (sr.expires_at is null or sr.expires_at > now())
                      and sr.deleted_at is null
                      and sp.status = 'ENABLED'
                      and sp.deleted_at is null
                      and sp.provider_type = 'LOCAL_ENCRYPTED'
                    """, (rs, rowNum) -> new SecretRow(
                    rs.getString("secret_ref"),
                    rs.getString("purpose"),
                    rs.getString("scope_type"),
                    rs.getString("scope_id"),
                    rs.getString("secret_version"),
                    rs.getString("provider_code"),
                    rs.getString("provider_type"),
                    rs.getString("cipher_text"),
                    rs.getString("iv"),
                    rs.getString("auth_tag"),
                    rs.getString("algorithm"),
                    rs.getString("master_key_version"),
                    rs.getString("local_status")
            ), secretRef.trim());
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
        SecretProviderAuditRecorder.Target auditTarget = auditTarget(row);
        try {
            if (context != null && StringUtils.hasText(context.purpose())
                    && !context.purpose().trim().equalsIgnoreCase(row.purpose())) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                        "密钥用途不匹配");
            }
            validateScope(row, context);
            if (!"ACTIVE".equals(row.localStatus())) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "本地密文状态不可用");
            }
            if (!"AES-256-GCM".equalsIgnoreCase(row.algorithm())) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                        "暂不支持的本地密钥算法: " + row.algorithm());
            }
            ResolvedSecret resolvedSecret = new ResolvedSecret(
                    row.secretRef(),
                    LocalSecretCipher.decrypt(
                            row.cipherText(),
                            row.iv(),
                            row.authTag(),
                            row.algorithm(),
                            row.masterKeyVersion(),
                            properties,
                            row.secretRef()
                    ),
                    row.providerCode(),
                    row.secretVersion()
            );
            auditRecorder.recordSuccess(auditTarget, context);
            return Optional.of(resolvedSecret);
        } catch (BusinessException exception) {
            auditRecorder.recordFailure(auditTarget, context, exception.getMessage());
            throw exception;
        }
    }

    private SecretProviderAuditRecorder.Target auditTarget(SecretRow row) {
        return new SecretProviderAuditRecorder.Target(
                row.secretRef(),
                row.providerCode(),
                row.providerType(),
                row.secretVersion(),
                row.purpose(),
                row.scopeType(),
                row.scopeId()
        );
    }

    private void validateScope(SecretRow row, SecretResolveContext context) {
        if (context == null) {
            return;
        }
        if (StringUtils.hasText(context.scopeType())
                && !context.scopeType().trim().equalsIgnoreCase(row.scopeType())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域类型不匹配");
        }
        if (StringUtils.hasText(context.scopeId())
                && !context.scopeId().trim().equalsIgnoreCase(row.scopeId())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域不匹配");
        }
    }

    private record SecretRow(
            String secretRef,
            String purpose,
            String scopeType,
            String scopeId,
            String secretVersion,
            String providerCode,
            String providerType,
            String cipherText,
            String iv,
            String authTag,
            String algorithm,
            String masterKeyVersion,
            String localStatus
    ) {
    }
}
