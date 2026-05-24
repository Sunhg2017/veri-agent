package com.songhg.veri.agent.management.application.service;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.management.application.port.SecretReferenceOperations;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.LocalSecretCipher;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.management.application.command.CreateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.DisableSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.RotateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.view.SecretReferenceView;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretProviderRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretReferenceRow;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagementSecretReferenceService implements SecretReferenceOperations {

    private final ManagementStore store;
    private final AuditLogWriter auditLogWriter;
    private final SecretProviderProperties secretProviderProperties;

    ManagementSecretReferenceService(
            ManagementStore store,
            AuditLogWriter auditLogWriter,
            SecretProviderProperties secretProviderProperties
    ) {
        this.store = store;
        this.auditLogWriter = auditLogWriter;
        this.secretProviderProperties = secretProviderProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<SecretReferenceView> secrets(PageQuery pageQuery) {
        return page(store::listSecretReferences, store::countSecretReferences, pageQuery, values());
    }

    /**
     * Persists LOCAL_ENCRYPTED secret references in two steps: metadata for audit/query, encrypted
     * material for runtime resolution. Non-local providers are read-only from this service.
     */
    @Transactional
    public SecretReferenceView createSecret(CreateSecretReferenceCommand request, AuthUserPrincipal actor) {
        String secretRef = request.secretRef().trim();
        String providerCode = defaultText(request.providerCode(), "");
        SecretProviderRow provider = requireOne(
                store::findSecretProviderForManage,
                values("providerCode", providerCode),
                "密钥提供方不存在"
        );
        ensureLocalProvider(provider);
        UUID secretRefId = UUID.randomUUID();
        LocalSecretCipher.EncryptedMaterial material = LocalSecretCipher.encrypt(
                request.value(),
                secretProviderProperties
        );
        String secretVersion = defaultText(request.secretVersion(), "v1");
        try {
            update(store::insertSecretReference, actor, values(
                    "secretRefId", secretRefId,
                    "providerId", provider.id(),
                    "secretRef", secretRef,
                    "scopeType", request.scopeType().trim(),
                    "scopeId", request.scopeId(),
                    "purpose", request.purpose().trim(),
                    "maskedValue", maskedSecret(),
                    "secretVersion", secretVersion,
                    "expiresAt", request.expiresAt()
            ));
            update(store::insertSecretLocalStore, actor, values(
                    "secretRefId", secretRefId,
                    "cipherText", material.cipherText(),
                    "iv", material.iv(),
                    "authTag", material.authTag(),
                    "algorithm", material.algorithm(),
                    "masterKeyVersion", material.masterKeyVersion()
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "密钥引用已存在");
        }
        SecretReferenceView created = secretReferenceByRef(secretRef);
        audit(actor, "创建密钥引用", "secret_reference", created.id(), created.secretRef());
        return created;
    }

    /**
     * Rotates only ACTIVE local secrets so historical references cannot be silently revived or
     * overwritten after revocation.
     */
    @Transactional
    public SecretReferenceView rotateSecret(RotateSecretReferenceCommand request, AuthUserPrincipal actor) {
        SecretReferenceRow current = secretReferenceRow(request.secretRef());
        ensureLocalProvider(current);
        if (!"ACTIVE".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有 ACTIVE 密钥可轮换");
        }
        LocalSecretCipher.EncryptedMaterial material = LocalSecretCipher.encrypt(
                request.value(),
                secretProviderProperties
        );
        String nextVersion = defaultText(request.secretVersion(), nextSecretVersion(current.secretVersion()));
        update(store::updateSecretReferenceRotation, actor, values(
                "secretRefId", current.id(),
                "secretVersion", nextVersion,
                "maskedValue", maskedSecret(),
                "expiresAt", request.expiresAt()
        ));
        update(store::upsertSecretLocalStoreRotation, actor, values(
                "secretRefId", current.id(),
                "cipherText", material.cipherText(),
                "iv", material.iv(),
                "authTag", material.authTag(),
                "algorithm", material.algorithm(),
                "masterKeyVersion", material.masterKeyVersion()
        ));
        SecretReferenceView updated = secretReferenceByRef(current.secretRef());
        audit(actor, "轮换密钥引用", "secret_reference", updated.id(), updated.secretRef());
        return updated;
    }

    /**
     * Revokes both the visible reference and encrypted material; callers keep the audit trail but
     * runtime resolution no longer has ciphertext to decrypt.
     */
    @Transactional
    public SecretReferenceView disableSecret(DisableSecretReferenceCommand request, AuthUserPrincipal actor) {
        SecretReferenceRow current = secretReferenceRow(request.secretRef());
        update(store::revokeSecretReference, actor, values("secretRefId", current.id()));
        update(store::revokeSecretLocalStore, actor, values("secretRefId", current.id()));
        SecretReferenceView updated = secretReferenceByRef(current.secretRef());
        audit(actor, "撤销密钥引用", "secret_reference", updated.id(), updated.secretRef());
        return updated;
    }

    private SecretReferenceRow secretReferenceRow(String secretRef) {
        return requireOne(
                store::findSecretReferenceRow,
                values("secretRef", normalizeSearch(secretRef)),
                "密钥引用不存在"
        );
    }

    private SecretReferenceView secretReferenceByRef(String secretRef) {
        return requireOne(
                store::findSecretReferenceView,
                values("secretRef", normalizeSearch(secretRef)),
                "密钥引用不存在"
        );
    }

    private void ensureLocalProvider(SecretProviderRow provider) {
        if (!"LOCAL_ENCRYPTED".equals(provider.providerType()) || !"ENABLED".equals(provider.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前密钥提供方不支持本地写入和轮换");
        }
    }

    private void ensureLocalProvider(SecretReferenceRow secret) {
        if (!"LOCAL_ENCRYPTED".equals(secret.providerType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前密钥引用不支持本地轮换");
        }
    }

    private String maskedSecret() {
        return "********";
    }

    private String nextSecretVersion(String currentVersion) {
        String normalized = defaultText(currentVersion, "v1");
        if (normalized.matches("v\\d+")) {
            int version = Integer.parseInt(normalized.substring(1));
            return "v" + (version + 1);
        }
        return normalized + "-rotated";
    }

    private String defaultText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private int update(
            ToIntFunction<ManagementStoreParams> statement,
            AuthUserPrincipal actor,
            ManagementStoreParams params
    ) {
        return statement.applyAsInt(withActor(actor, params));
    }

    private <T> PageResponse<T> page(
            Function<ManagementStoreParams, List<T>> listStatement,
            ToLongFunction<ManagementStoreParams> countStatement,
            PageQuery pageQuery,
            ManagementStoreParams extraParams
    ) {
        ManagementStoreParams params = pageParams(pageQuery, extraParams);
        List<T> items = listStatement.apply(params);
        long total = countStatement.applyAsLong(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    private ManagementStoreParams pageParams(PageQuery pageQuery, ManagementStoreParams extraParams) {
        ManagementStoreParams params = ManagementStoreParams.copyOf(extraParams);
        params.put("search", pageQuery.search());
        params.put("searchPattern", pageQuery.searchPattern());
        params.put("limit", pageQuery.size());
        params.put("offset", pageQuery.offset());
        return params;
    }

    private ManagementStoreParams withActor(AuthUserPrincipal actor, ManagementStoreParams source) {
        ManagementStoreParams params = ManagementStoreParams.copyOf(source);
        params.put("actorId", actor.userId());
        return params;
    }

    private ManagementStoreParams values(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现");
        }
        ManagementStoreParams params = ManagementStoreParams.empty();
        for (int index = 0; index < pairs.length; index += 2) {
            params.put((String) pairs[index], pairs[index + 1]);
        }
        return params;
    }

    private <T> T requireOne(
            Function<ManagementStoreParams, T> statement,
            ManagementStoreParams params,
            String notFoundMessage
    ) {
        ManagementStoreParams normalized = ManagementStoreParams.copyOf(params);
        T value = statement.apply(normalized);
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage);
        }
        return value;
    }

    private void audit(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String targetName
    ) {
        auditLogWriter.record(AuditLogWriter.success(
                actor, action, resourceType, resourceId, targetName
        ));
    }
}
