package com.songhg.veri.agent.management.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.secret.LocalSecretCipher;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.management.application.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.application.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.application.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.application.SecretReferenceView;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SecretProviderRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SecretReferenceRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostgresManagementConsoleServiceSecretTest {

    private static final SecretProviderProperties SECRET_PROPERTIES = new SecretProviderProperties(
            "0123456789abcdef0123456789abcdef",
            "validation-v1",
            "",
            "",
            3,
            1,
            "",
            "",
            ""
    );

    @Test
    void localEncryptedSecretLifecycleWritesCipherMaterialAndRevokesStore() {
        ManagementMapper mapper = mock(ManagementMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostgresManagementConsoleService service = new PostgresManagementConsoleService(
                mapper,
                null,
                auditLogWriter,
                null,
                null,
                new ObjectMapper(),
                SECRET_PROPERTIES
        );
        UUID providerId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        String secretRef = "secret://wp1/test-secret";
        AuthUserPrincipal actor = new AuthUserPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin",
                "Admin",
                "admin@example.test",
                false,
                1,
                List.of("SuperAdmin")
        );

        when(mapper.findSecretProviderForManage(anyMap()))
                .thenReturn(new SecretProviderRow(providerId, "local", "LOCAL_ENCRYPTED", "ENABLED"));
        when(mapper.insertSecretReference(anyMap())).thenReturn(1);
        when(mapper.insertSecretLocalStore(anyMap())).thenReturn(1);
        when(mapper.updateSecretReferenceRotation(anyMap())).thenReturn(1);
        when(mapper.upsertSecretLocalStoreRotation(anyMap())).thenReturn(1);
        when(mapper.revokeSecretReference(anyMap())).thenReturn(1);
        when(mapper.revokeSecretLocalStore(anyMap())).thenReturn(1);
        when(mapper.findSecretReferenceRow(anyMap()))
                .thenReturn(
                        new SecretReferenceRow(secretId, secretRef, "local", "LOCAL_ENCRYPTED",
                                "WEBHOOK_SIGNING", "CONFIG", scopeId, "v1", "ACTIVE"),
                        new SecretReferenceRow(secretId, secretRef, "local", "LOCAL_ENCRYPTED",
                                "WEBHOOK_SIGNING", "CONFIG", scopeId, "v2", "ACTIVE")
                );
        when(mapper.findSecretReferenceView(anyMap()))
                .thenReturn(
                        view(secretId, secretRef, scopeId, "v1", "ACTIVE"),
                        view(secretId, secretRef, scopeId, "v2", "ACTIVE"),
                        view(secretId, secretRef, scopeId, "v2", "REVOKED")
                );

        SecretReferenceView created = service.createSecret(new CreateSecretReferenceRequest(
                secretRef,
                "local",
                "WEBHOOK_SIGNING",
                "CONFIG",
                scopeId,
                "PlainSecret123",
                "v1",
                null
        ), actor);

        assertThat(created.secretVersion()).isEqualTo("v1");
        Map<String, Object> createStore = capturedMap(mapper, "insert");
        assertThat(decrypt(createStore, secretRef)).isEqualTo("PlainSecret123");
        assertThat((String) createStore.get("cipherText")).doesNotContain("PlainSecret123");
        assertThat(createStore.get("masterKeyVersion")).isEqualTo("validation-v1");

        SecretReferenceView rotated = service.rotateSecret(new RotateSecretReferenceRequest(
                secretRef,
                "RotatedSecret456",
                "v2",
                null
        ), actor);

        assertThat(rotated.secretVersion()).isEqualTo("v2");
        Map<String, Object> rotatedStore = capturedMap(mapper, "rotate");
        assertThat(decrypt(rotatedStore, secretRef)).isEqualTo("RotatedSecret456");
        assertThat((String) rotatedStore.get("cipherText")).doesNotContain("RotatedSecret456");

        SecretReferenceView disabled = service.disableSecret(new DisableSecretReferenceRequest(secretRef), actor);

        assertThat(disabled.status()).isEqualTo("REVOKED");
        verify(mapper).revokeSecretReference(anyMap());
        verify(mapper).revokeSecretLocalStore(anyMap());
        verify(auditLogWriter, times(3)).record(org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> capturedMap(ManagementMapper mapper, String operation) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        if ("insert".equals(operation)) {
            verify(mapper).insertSecretLocalStore(captor.capture());
        } else {
            verify(mapper).upsertSecretLocalStoreRotation(captor.capture());
        }
        return captor.getValue();
    }

    private static String decrypt(Map<String, Object> material, String secretRef) {
        return LocalSecretCipher.decrypt(
                (String) material.get("cipherText"),
                (String) material.get("iv"),
                (String) material.get("authTag"),
                (String) material.get("algorithm"),
                (String) material.get("masterKeyVersion"),
                SECRET_PROPERTIES,
                secretRef
        );
    }

    private static SecretReferenceView view(UUID id, String secretRef, UUID scopeId, String version, String status) {
        return new SecretReferenceView(
                id.toString(),
                secretRef,
                "local",
                "LOCAL_ENCRYPTED",
                "WEBHOOK_SIGNING",
                "CONFIG",
                scopeId.toString(),
                "********",
                version,
                status,
                null,
                null,
                null,
                null
        );
    }
}
