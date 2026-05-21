package com.songhg.veri.agent.common.secret;

import com.songhg.veri.agent.common.error.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalEncryptedSecretProviderTest {

    private static final String MASTER_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void resolvesLocalEncryptedSecretWhenPurposeAndScopeMatch() {
        String scopeId = UUID.randomUUID().toString();
        LocalSecretCipher.EncryptedMaterial material = encrypt("source-secret");
        LocalEncryptedSecretProvider provider = provider(row(scopeId, material));

        Optional<ResolvedSecret> resolved = provider.resolve("secret://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                scopeId
        ));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().value()).isEqualTo("source-secret");
        assertThat(resolved.get().version()).isEqualTo("v1");
    }

    @Test
    void rejectsLocalEncryptedSecretWhenScopeDoesNotMatch() {
        LocalSecretCipher.EncryptedMaterial material = encrypt("source-secret");
        LocalEncryptedSecretProvider provider = provider(row(UUID.randomUUID().toString(), material));

        assertThatThrownBy(() -> provider.resolve("secret://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                UUID.randomUUID().toString()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密钥作用域不匹配");
    }

    @Test
    void resolvedSecretToStringDoesNotExposeSecretValue() {
        ResolvedSecret secret = new ResolvedSecret("secret://wp4/source-a", "source-secret", "local", "v1");

        assertThat(secret.toString())
                .contains("value=****")
                .doesNotContain("source-secret");
    }

    @Test
    void localSecretCipherProducesMaterialResolvableByProvider() {
        String scopeId = UUID.randomUUID().toString();
        LocalSecretCipher.EncryptedMaterial material = LocalSecretCipher.encrypt(
                "provider-created-secret",
                new SecretProviderProperties(MASTER_KEY, "v1", "", "", 3, 1, "")
        );
        LocalEncryptedSecretProvider provider = provider(row(scopeId, material));

        Optional<ResolvedSecret> resolved = provider.resolve("secret://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                scopeId
        ));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().value()).isEqualTo("provider-created-secret");
        assertThat(material.cipherText()).doesNotContain("provider-created-secret");
    }

    private LocalEncryptedSecretProvider provider(SecretDbRow row) {
        return new LocalEncryptedSecretProvider(jdbcTemplateReturning(row), new SecretProviderProperties(MASTER_KEY, "v1", "", "", 3, 1, ""));
    }

    private JdbcTemplate jdbcTemplateReturning(SecretDbRow row) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
                try {
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("secret_ref")).thenReturn(row.secretRef());
                    when(resultSet.getString("purpose")).thenReturn(row.purpose());
                    when(resultSet.getString("scope_type")).thenReturn(row.scopeType());
                    when(resultSet.getString("scope_id")).thenReturn(row.scopeId());
                    when(resultSet.getString("secret_version")).thenReturn(row.secretVersion());
                    when(resultSet.getString("provider_code")).thenReturn(row.providerCode());
                    when(resultSet.getString("provider_type")).thenReturn(row.providerType());
                    when(resultSet.getString("cipher_text")).thenReturn(row.cipherText());
                    when(resultSet.getString("iv")).thenReturn(row.iv());
                    when(resultSet.getString("auth_tag")).thenReturn(row.authTag());
                    when(resultSet.getString("algorithm")).thenReturn(row.algorithm());
                    when(resultSet.getString("master_key_version")).thenReturn(row.masterKeyVersion());
                    when(resultSet.getString("local_status")).thenReturn(row.localStatus());
                    return rowMapper.mapRow(resultSet, 0);
                } catch (SQLException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    private SecretDbRow row(String scopeId, LocalSecretCipher.EncryptedMaterial material) {
        return new SecretDbRow(
                "secret://wp4/source-a",
                "WEBHOOK_SIGNING",
                "CONFIG",
                scopeId,
                "v1",
                "local",
                "LOCAL_ENCRYPTED",
                material.cipherText(),
                material.iv(),
                material.authTag(),
                material.algorithm(),
                material.masterKeyVersion(),
                "ACTIVE"
        );
    }

    private LocalSecretCipher.EncryptedMaterial encrypt(String value) {
        return LocalSecretCipher.encrypt(value, new SecretProviderProperties(MASTER_KEY, "v1", "", "", 3, 1, ""));
    }

    private record SecretDbRow(
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
