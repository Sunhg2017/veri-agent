package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.LocalSecretCipher;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.management.api.request.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.response.SecretReferenceView;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SecretProviderRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SecretReferenceRow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.dao.DuplicateKeyException;

final class PostgresManagementSecretReferenceService {

    private final ManagementMapper mapper;
    private final AuditLogWriter auditLogWriter;
    private final SecretProviderProperties secretProviderProperties;

    PostgresManagementSecretReferenceService(
            ManagementMapper mapper,
            AuditLogWriter auditLogWriter,
            SecretProviderProperties secretProviderProperties
    ) {
        this.mapper = mapper;
        this.auditLogWriter = auditLogWriter;
        this.secretProviderProperties = secretProviderProperties;
    }

    PageResponse<SecretReferenceView> secrets(PageQuery pageQuery) {
        return page(mapper::listSecretReferences, mapper::countSecretReferences, pageQuery, values());
    }

    SecretReferenceView createSecret(CreateSecretReferenceRequest request, AuthUserPrincipal actor) {
        String secretRef = request.secretRef().trim();
        String providerCode = defaultText(request.providerCode(), "");
        SecretProviderRow provider = requireOne(
                mapper::findSecretProviderForManage,
                values("providerCode", providerCode),
                "密钥提供方不存在"
        );
        ensureLocalProvider(provider);
        UUID secretRefId = UUID.randomUUID();
        LocalSecretCipher.EncryptedMaterial material = LocalSecretCipher.encrypt(request.value(), secretProviderProperties);
        String secretVersion = defaultText(request.secretVersion(), "v1");
        try {
            update(mapper::insertSecretReference, actor, values(
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
            update(mapper::insertSecretLocalStore, actor, values(
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

    SecretReferenceView rotateSecret(RotateSecretReferenceRequest request, AuthUserPrincipal actor) {
        SecretReferenceRow current = secretReferenceRow(request.secretRef());
        ensureLocalProvider(current);
        if (!"ACTIVE".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有 ACTIVE 密钥可轮换");
        }
        LocalSecretCipher.EncryptedMaterial material = LocalSecretCipher.encrypt(request.value(), secretProviderProperties);
        String nextVersion = defaultText(request.secretVersion(), nextSecretVersion(current.secretVersion()));
        update(mapper::updateSecretReferenceRotation, actor, values(
                "secretRefId", current.id(),
                "secretVersion", nextVersion,
                "maskedValue", maskedSecret(),
                "expiresAt", request.expiresAt()
        ));
        update(mapper::upsertSecretLocalStoreRotation, actor, values(
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

    SecretReferenceView disableSecret(DisableSecretReferenceRequest request, AuthUserPrincipal actor) {
        SecretReferenceRow current = secretReferenceRow(request.secretRef());
        update(mapper::revokeSecretReference, actor, values("secretRefId", current.id()));
        update(mapper::revokeSecretLocalStore, actor, values("secretRefId", current.id()));
        SecretReferenceView updated = secretReferenceByRef(current.secretRef());
        audit(actor, "撤销密钥引用", "secret_reference", updated.id(), updated.secretRef());
        return updated;
    }

    private SecretReferenceRow secretReferenceRow(String secretRef) {
        return requireOne(mapper::findSecretReferenceRow, values("secretRef", normalizeSearch(secretRef)), "密钥引用不存在");
    }

    private SecretReferenceView secretReferenceByRef(String secretRef) {
        return requireOne(mapper::findSecretReferenceView, values("secretRef", normalizeSearch(secretRef)), "密钥引用不存在");
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

    private int update(ToIntFunction<Map<String, Object>> statement, AuthUserPrincipal actor, Map<String, Object> params) {
        return statement.applyAsInt(withActor(actor, params));
    }

    private <T> PageResponse<T> page(
            Function<Map<String, Object>, List<T>> listStatement,
            ToLongFunction<Map<String, Object>> countStatement,
            PageQuery pageQuery,
            Map<String, Object> extraParams
    ) {
        Map<String, Object> params = pageParams(pageQuery, extraParams);
        List<T> items = listStatement.apply(params);
        long total = countStatement.applyAsLong(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    private Map<String, Object> pageParams(PageQuery pageQuery, Map<String, Object> extraParams) {
        Map<String, Object> params = new HashMap<>(extraParams);
        params.put("search", pageQuery.search());
        params.put("searchPattern", pageQuery.searchPattern());
        params.put("limit", pageQuery.size());
        params.put("offset", pageQuery.offset());
        return params;
    }

    private Map<String, Object> withActor(AuthUserPrincipal actor, Map<String, Object> source) {
        Map<String, Object> params = new HashMap<>(source);
        params.put("actorId", actor.userId());
        return params;
    }

    private Map<String, Object> values(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现");
        }
        Map<String, Object> params = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            params.put((String) pairs[index], pairs[index + 1]);
        }
        return params;
    }

    private <T> T requireOne(Function<Map<String, Object>, T> statement, Map<String, Object> params, String notFoundMessage) {
        Map<String, Object> normalized = new HashMap<>(params);
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
