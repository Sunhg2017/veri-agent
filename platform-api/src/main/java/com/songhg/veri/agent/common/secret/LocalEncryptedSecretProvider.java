package com.songhg.veri.agent.common.secret;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("db")
public class LocalEncryptedSecretProvider implements SecretProvider {

    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_TAG_BITS = 128;

    private final JdbcTemplate jdbcTemplate;
    private final SecretProviderProperties properties;

    public LocalEncryptedSecretProvider(JdbcTemplate jdbcTemplate, SecretProviderProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
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
        if (context != null && StringUtils.hasText(context.purpose())
                && !context.purpose().trim().equalsIgnoreCase(row.purpose())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥用途不匹配: " + row.secretRef());
        }
        validateScope(row, context);
        if (!"ACTIVE".equals(row.localStatus())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "本地密文状态不可用: " + secretRef);
        }
        if (!"AES-256-GCM".equalsIgnoreCase(row.algorithm())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "暂不支持的本地密钥算法: " + row.algorithm());
        }
        return Optional.of(new ResolvedSecret(
                row.secretRef(),
                decrypt(row),
                row.providerCode(),
                row.secretVersion()
        ));
    }

    private void validateScope(SecretRow row, SecretResolveContext context) {
        if (context == null) {
            return;
        }
        if (StringUtils.hasText(context.scopeType())
                && !context.scopeType().trim().equalsIgnoreCase(row.scopeType())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域类型不匹配: " + row.secretRef());
        }
        if (StringUtils.hasText(context.scopeId())
                && !context.scopeId().trim().equalsIgnoreCase(row.scopeId())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域不匹配: " + row.secretRef());
        }
    }

    private String decrypt(SecretRow row) {
        try {
            byte[] key = masterKey(row.masterKeyVersion());
            byte[] iv = decodeMaterial(row.iv());
            byte[] cipherText = decodeMaterial(row.cipherText());
            byte[] authTag = decodeMaterial(row.authTag());
            byte[] cipherTextWithTag = new byte[cipherText.length + authTag.length];
            System.arraycopy(cipherText, 0, cipherTextWithTag, 0, cipherText.length);
            System.arraycopy(authTag, 0, cipherTextWithTag, cipherText.length, authTag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherTextWithTag), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "本地密文解密失败: " + row.secretRef());
        }
    }

    private byte[] masterKey(String requiredVersion) {
        String configuredVersion = StringUtils.hasText(properties.localMasterKeyVersion())
                ? properties.localMasterKeyVersion().trim()
                : "v1";
        if (StringUtils.hasText(requiredVersion) && !requiredVersion.trim().equals(configuredVersion)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "本地密钥版本不匹配: " + requiredVersion);
        }
        String rawKey = properties.localMasterKey();
        if (!StringUtils.hasText(rawKey)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "缺少 WP1_LOCAL_SECRET_MASTER_KEY");
        }
        byte[] decoded = decodeKey(rawKey.trim());
        if (decoded.length != AES_256_KEY_BYTES) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "WP1_LOCAL_SECRET_MASTER_KEY 必须为 32 字节 AES-256 密钥");
        }
        return decoded;
    }

    private byte[] decodeKey(String value) {
        List<java.util.function.Function<String, byte[]>> decoders = List.of(
                this::decodeHexIfPossible,
                item -> Base64.getDecoder().decode(item),
                item -> item.getBytes(StandardCharsets.UTF_8)
        );
        for (var decoder : decoders) {
            try {
                byte[] decoded = decoder.apply(value);
                if (decoded.length == AES_256_KEY_BYTES) {
                    return decoded;
                }
            } catch (Exception ignored) {
                // Try the next supported encoding.
            }
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeMaterial(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "密文材料不完整");
        }
        String normalized = value.trim();
        try {
            return decodeHexIfPossible(normalized);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(normalized);
        }
    }

    private byte[] decodeHexIfPossible(String value) {
        if (value.length() % 2 != 0 || !value.matches("(?i)^[0-9a-f]+$")) {
            throw new IllegalArgumentException("not hex");
        }
        return HexFormat.of().parseHex(value);
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
