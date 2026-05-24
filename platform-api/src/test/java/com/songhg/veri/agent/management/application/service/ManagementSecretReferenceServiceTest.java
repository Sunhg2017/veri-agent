package com.songhg.veri.agent.management.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.management.application.command.CreateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.RotateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.view.SecretReferenceView;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretProviderRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretReferenceRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManagementSecretReferenceServiceTest {

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
    void createSecretDefaultsVersionAndRejectsExternalProviderWrites() {
        ManagementStore mapper = mock(ManagementStore.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        ManagementSecretReferenceService service = service(mapper, auditLogWriter);
        UUID providerId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        String secretRef = "secret://wp1/default-version";
        when(mapper.findSecretProviderForManage(anyMap()))
                .thenReturn(new SecretProviderRow(providerId, "local", "LOCAL_ENCRYPTED", "ENABLED"))
                .thenReturn(new SecretProviderRow(providerId, "vault", "EXTERNAL_VAULT", "ENABLED"));
        when(mapper.insertSecretReference(anyMap())).thenReturn(1);
        when(mapper.insertSecretLocalStore(anyMap())).thenReturn(1);
        when(mapper.findSecretReferenceView(anyMap())).thenReturn(view(secretId, secretRef, scopeId, "v1", "ACTIVE"));

        SecretReferenceView created = service.createSecret(createRequest(secretRef, "local", null, scopeId), actor());

        assertThat(created.secretVersion()).isEqualTo("v1");
        assertThat(capturedInsert(mapper).get("secretVersion")).isEqualTo("v1");
        assertThatThrownBy(() -> service.createSecret(
                createRequest("secret://wp1/external", "vault", null, scopeId),
                actor()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前密钥提供方不支持本地写入和轮换");
        verify(mapper, times(1)).insertSecretReference(anyMap());
        verify(mapper, times(1)).insertSecretLocalStore(anyMap());
        verify(auditLogWriter).record(any());
    }

    @Test
    void rotateSecretRejectsUnsupportedProviderOrNonActiveStatusBeforeWriting() {
        ManagementStore mapper = mock(ManagementStore.class);
        ManagementSecretReferenceService service = service(mapper, mock(AuditLogWriter.class));
        UUID secretId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        when(mapper.findSecretReferenceRow(anyMap()))
                .thenReturn(row(secretId, "secret://wp1/external", "EXTERNAL_VAULT", "ACTIVE", "v1", scopeId))
                .thenReturn(row(secretId, "secret://wp1/revoked", "LOCAL_ENCRYPTED", "REVOKED", "v1", scopeId));

        assertThatThrownBy(() -> service.rotateSecret(
                rotateRequest("secret://wp1/external", null),
                actor()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前密钥引用不支持本地轮换");
        assertThatThrownBy(() -> service.rotateSecret(
                rotateRequest("secret://wp1/revoked", null),
                actor()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有 ACTIVE 密钥可轮换");
        verify(mapper, never()).updateSecretReferenceRotation(anyMap());
        verify(mapper, never()).upsertSecretLocalStoreRotation(anyMap());
    }

    @Test
    void rotateSecretDerivesNextVersionForNumericAndNamedVersions() {
        ManagementStore mapper = mock(ManagementStore.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        ManagementSecretReferenceService service = service(mapper, auditLogWriter);
        UUID secretId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        when(mapper.findSecretReferenceRow(anyMap()))
                .thenReturn(row(secretId, "secret://wp1/numeric", "LOCAL_ENCRYPTED", "ACTIVE", "v1", scopeId))
                .thenReturn(row(secretId, "secret://wp1/named", "LOCAL_ENCRYPTED", "ACTIVE", "blue", scopeId));
        when(mapper.updateSecretReferenceRotation(anyMap())).thenReturn(1);
        when(mapper.upsertSecretLocalStoreRotation(anyMap())).thenReturn(1);
        when(mapper.findSecretReferenceView(anyMap()))
                .thenReturn(view(secretId, "secret://wp1/numeric", scopeId, "v2", "ACTIVE"))
                .thenReturn(view(secretId, "secret://wp1/named", scopeId, "blue-rotated", "ACTIVE"));

        SecretReferenceView numeric = service.rotateSecret(rotateRequest("secret://wp1/numeric", null), actor());
        SecretReferenceView named = service.rotateSecret(rotateRequest("secret://wp1/named", null), actor());

        assertThat(numeric.secretVersion()).isEqualTo("v2");
        assertThat(named.secretVersion()).isEqualTo("blue-rotated");
        assertThat(capturedRotations(mapper))
                .extracting(params -> params.get("secretVersion"))
                .containsExactly("v2", "blue-rotated");
        verify(auditLogWriter, times(2)).record(any());
    }

    private ManagementSecretReferenceService service(
            ManagementStore mapper,
            AuditLogWriter auditLogWriter
    ) {
        return new ManagementSecretReferenceService(mapper, auditLogWriter, SECRET_PROPERTIES);
    }

    private AuthUserPrincipal actor() {
        return new AuthUserPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin",
                "Admin",
                "admin@example.test",
                false,
                1,
                List.of("SuperAdmin")
        );
    }

    private CreateSecretReferenceCommand createRequest(
            String secretRef,
            String providerCode,
            String secretVersion,
            UUID scopeId
    ) {
        return new CreateSecretReferenceCommand(
                secretRef,
                providerCode,
                "WEBHOOK_SIGNING",
                "CONFIG",
                scopeId,
                "PlainSecret123",
                secretVersion,
                null
        );
    }

    private RotateSecretReferenceCommand rotateRequest(String secretRef, String secretVersion) {
        return new RotateSecretReferenceCommand(secretRef, "RotatedSecret456", secretVersion, null);
    }

    private SecretReferenceRow row(
            UUID id,
            String secretRef,
            String providerType,
            String status,
            String version,
            UUID scopeId
    ) {
        return new SecretReferenceRow(
                id,
                secretRef,
                "provider",
                providerType,
                "WEBHOOK_SIGNING",
                "CONFIG",
                scopeId,
                version,
                status
        );
    }

    private SecretReferenceView view(UUID id, String secretRef, UUID scopeId, String version, String status) {
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedInsert(ManagementStore mapper) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertSecretReference(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> capturedRotations(ManagementStore mapper) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(2)).updateSecretReferenceRotation(captor.capture());
        return captor.getAllValues();
    }
}
