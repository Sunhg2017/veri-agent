package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.CreateIntegrationRequest;
import com.songhg.veri.agent.management.application.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.application.IntegrationView;
import java.util.ArrayList;
import java.util.List;

final class InMemoryManagementIntegrationService {

    private final List<IntegrationView> integrations = new ArrayList<>();
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementIntegrationService(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        integrations.addAll(List.of(
                new IntegrationView("github-enterprise", "GitHub Enterprise", "代码仓库", "全局", "已启用"),
                new IntegrationView("jenkins", "Jenkins", "CI/CD", "平台级", "已启用"),
                new IntegrationView("feishu-bot", "Feishu Bot", "通知", "项目级", "已启用")
        ));
    }

    PageResponse<IntegrationView> integrations(PageQuery pageQuery) {
        return page(integrations, pageQuery);
    }

    IntegrationView integration(String key) {
        return requireIntegration(key);
    }

    IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor) {
        String key = integrationKey(request.code(), request.name());
        if (integrations.stream().anyMatch(integration -> integration.key().equals(key))) {
            throw new BusinessException(ErrorCode.CONFLICT, "集成配置已存在");
        }
        IntegrationView view = new IntegrationView(
                key,
                request.name().trim(),
                defaultText(request.category(), "未分类"),
                defaultText(request.scope(), "平台级"),
                "已启用"
        );
        integrations.add(0, view);
        audit(actor, "登记集成", view.name());
        return view;
    }

    IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor) {
        IntegrationView current = requireIntegration(key);
        IntegrationView updated = new IntegrationView(
                current.key(),
                defaultText(request.name(), current.name()),
                defaultText(request.category(), current.category()),
                defaultText(request.scope(), current.scope()),
                current.status()
        );
        integrations.removeIf(integration -> integration.key().equals(current.key()));
        integrations.add(0, updated);
        audit(actor, "更新集成", updated.name());
        return updated;
    }

    IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor) {
        IntegrationView current = requireIntegration(key);
        String nextStatus = "DISABLED".equals(status) ? "已停用" : "已启用";
        IntegrationView updated = new IntegrationView(current.key(), current.name(), current.category(), current.scope(), nextStatus);
        integrations.removeIf(integration -> integration.key().equals(current.key()));
        integrations.add(0, updated);
        audit(actor, "DISABLED".equals(status) ? "停用集成" : "启用集成", updated.name());
        return updated;
    }

    private IntegrationView requireIntegration(String key) {
        return integrations.stream()
                .filter(integration -> integration.key().equals(key) || integration.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "集成配置不存在"));
    }

    private String integrationKey(String code, String name) {
        String seed = defaultText(code, name);
        String normalized = seed.trim().toLowerCase().replaceAll("[^a-z0-9_-]+", "-").replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "integration-" + Math.abs(seed.hashCode()) : normalized;
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
