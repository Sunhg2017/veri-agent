package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.management.application.port.SecretReferenceOperations;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.command.CreateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.DisableSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.RotateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.view.SecretReferenceView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Profile("local")
@Service
final class InMemoryManagementSecretReferenceService implements SecretReferenceOperations {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<SecretReferenceView> secrets = new ArrayList<>();
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementSecretReferenceService(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    public synchronized PageResponse<SecretReferenceView> secrets(PageQuery pageQuery) {
        return page(secrets, pageQuery);
    }

    public synchronized SecretReferenceView createSecret(CreateSecretReferenceCommand request, AuthUserPrincipal actor) {
        String secretRef = request.secretRef().trim();
        if (secrets.stream().anyMatch(secret -> secret.secretRef().equals(secretRef))) {
            throw new BusinessException(ErrorCode.CONFLICT, "密钥引用已存在");
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        SecretReferenceView view = new SecretReferenceView(
                UUID.randomUUID().toString(),
                secretRef,
                defaultText(request.providerCode(), "local"),
                "LOCAL_ENCRYPTED",
                request.purpose().trim(),
                request.scopeType().trim(),
                request.scopeId().toString(),
                maskedSecret(),
                defaultText(request.secretVersion(), "v1"),
                "ACTIVE",
                now,
                request.expiresAt() == null ? "" : request.expiresAt().toString(),
                now,
                now
        );
        secrets.add(0, view);
        audit(actor, "创建密钥引用", secretRef);
        return view;
    }

    public synchronized SecretReferenceView rotateSecret(RotateSecretReferenceCommand request, AuthUserPrincipal actor) {
        SecretReferenceView current = requireSecret(request.secretRef());
        if ("REVOKED".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已撤销密钥不可轮换");
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        SecretReferenceView updated = new SecretReferenceView(
                current.id(),
                current.secretRef(),
                current.providerCode(),
                current.providerType(),
                current.purpose(),
                current.scopeType(),
                current.scopeId(),
                maskedSecret(),
                defaultText(request.secretVersion(), nextSecretVersion(current.secretVersion())),
                "ACTIVE",
                now,
                request.expiresAt() == null ? current.expiresAt() : request.expiresAt().toString(),
                current.createdAt(),
                now
        );
        replaceSecret(updated);
        audit(actor, "轮换密钥引用", current.secretRef());
        return updated;
    }

    public synchronized SecretReferenceView disableSecret(DisableSecretReferenceCommand request, AuthUserPrincipal actor) {
        SecretReferenceView current = requireSecret(request.secretRef());
        String now = LocalDateTime.now().format(TIME_FORMAT);
        SecretReferenceView updated = new SecretReferenceView(
                current.id(),
                current.secretRef(),
                current.providerCode(),
                current.providerType(),
                current.purpose(),
                current.scopeType(),
                current.scopeId(),
                current.maskedValue(),
                current.secretVersion(),
                "REVOKED",
                current.rotatedAt(),
                current.expiresAt(),
                current.createdAt(),
                now
        );
        replaceSecret(updated);
        audit(actor, "撤销密钥引用", current.secretRef());
        return updated;
    }

    private SecretReferenceView requireSecret(String secretRef) {
        String normalized = defaultText(secretRef, "");
        return secrets.stream()
                .filter(secret -> secret.secretRef().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "密钥引用不存在"));
    }

    private void replaceSecret(SecretReferenceView updated) {
        for (int index = 0; index < secrets.size(); index++) {
            SecretReferenceView current = secrets.get(index);
            if (current.secretRef().equals(updated.secretRef())) {
                secrets.set(index, updated);
                return;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "密钥引用不存在");
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

    private <T> PageResponse<T> page(List<T> source, PageQuery pageQuery) {
        String keyword = pageQuery.search().toLowerCase();
        List<T> filtered = source.stream()
                .filter(item -> keyword.isBlank() || item.toString().toLowerCase().contains(keyword))
                .toList();
        int from = Math.min(pageQuery.offset(), filtered.size());
        int to = Math.min(from + pageQuery.size(), filtered.size());
        return PageResponse.of(filtered.subList(from, to), pageQuery.index(), pageQuery.size(), filtered.size());
    }

    private void audit(AuthUserPrincipal actor, String action, String target) {
        auditLogWriter.record(AuditLogWriter.success(
                actor, action, "management", target, target
        ));
    }
}
