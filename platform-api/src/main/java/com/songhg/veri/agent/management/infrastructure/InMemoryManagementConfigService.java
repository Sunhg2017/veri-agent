package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.CreateSettingRequest;
import com.songhg.veri.agent.management.application.UpdateSettingRequest;
import com.songhg.veri.agent.management.application.SettingView;
import java.util.ArrayList;
import java.util.List;

final class InMemoryManagementConfigService {

    private final List<SettingView> settings = new ArrayList<>();
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementConfigService(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        settings.addAll(List.of(
                new SettingView("password.min_length", "密码最小长度", "10 位", "全局安全策略", "已启用"),
                new SettingView("audit.retention_days", "审计日志保留", "365 天", "合规策略", "已启用"),
                new SettingView("audit.retention_cleanup_enabled", "审计保留清理", "false", "合规策略", "已停用"),
                new SettingView("audit.retention_min_days", "审计最小保留", "30 天", "合规策略", "已启用"),
                new SettingView("project.default_status", "默认项目状态", "规划中", "项目开通", "已启用")
        ));
    }

    PageResponse<SettingView> settings(PageQuery pageQuery) {
        return page(settings.stream()
                .filter(setting -> "已启用".equals(setting.status()))
                .toList(), pageQuery);
    }

    SettingView setting(String key) {
        return requireSetting(key);
    }

    SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor) {
        String key = request.key().trim();
        rejectSensitivePlainSetting(key, request.value());
        if (settings.stream().anyMatch(setting -> setting.key().equals(key))) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统设置已存在");
        }
        SettingView view = new SettingView(
                key,
                defaultText(request.name(), key),
                request.value().trim(),
                settingScopeName(request.scopeType()),
                "已启用"
        );
        settings.add(0, view);
        audit(actor, "创建设置", view.name());
        return view;
    }

    SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor) {
        SettingView current = requireSetting(key);
        rejectSensitivePlainSetting(current.key(), defaultText(request.value(), current.value()));
        SettingView updated = new SettingView(
                current.key(),
                defaultText(request.name(), current.name()),
                defaultText(request.value(), current.value()),
                settingScopeName(request.scopeType(), current.scope()),
                current.status()
        );
        settings.removeIf(setting -> setting.key().equals(current.key()));
        settings.add(0, updated);
        audit(actor, "更新设置", updated.name());
        return updated;
    }

    SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统设置状态只支持 ENABLED 或 DISABLED");
        }
        SettingView current = requireSetting(key);
        SettingView updated = new SettingView(
                current.key(),
                current.name(),
                current.value(),
                current.scope(),
                "ENABLED".equals(status) ? "已启用" : "已停用"
        );
        settings.removeIf(setting -> setting.key().equals(current.key()));
        settings.add(0, updated);
        audit(actor, "ENABLED".equals(status) ? "启用设置" : "停用设置", updated.name());
        return updated;
    }

    private SettingView requireSetting(String key) {
        return settings.stream()
                .filter(setting -> setting.key().equals(key) || setting.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "系统设置不存在"));
    }

    private String settingScopeName(String scopeType) {
        return settingScopeName(scopeType, "平台级");
    }

    private String settingScopeName(String scopeType, String fallback) {
        return switch (defaultText(scopeType, "")) {
            case "SYSTEM" -> "平台级";
            case "PROJECT" -> "项目级";
            case "APPLICATION" -> "应用级";
            case "ENVIRONMENT" -> "环境级";
            default -> fallback;
        };
    }

    private void rejectSensitivePlainSetting(String key, String value) {
        String normalizedKey = defaultText(key, "").toLowerCase();
        String normalizedValue = defaultText(value, "");
        boolean sensitiveKey = normalizedKey.matches(".*(password|passwd|pwd|secret|token|api[_.-]?key|cookie|credential|private[_.-]?key).*");
        if (sensitiveKey && !normalizedValue.matches("^(\\*+|已配置|secret-ref:.+|\\$\\{[A-Za-z0-9_]+})$")) {
            throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "敏感配置必须使用密钥引用或掩码值");
        }
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
