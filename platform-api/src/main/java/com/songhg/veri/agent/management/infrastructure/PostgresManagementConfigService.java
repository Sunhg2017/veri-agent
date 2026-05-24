package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.CreateIntegrationRequest;
import com.songhg.veri.agent.management.application.CreateSettingRequest;
import com.songhg.veri.agent.management.application.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.application.UpdateSettingRequest;
import com.songhg.veri.agent.management.application.IntegrationView;
import com.songhg.veri.agent.management.application.SettingView;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.IntegrationRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SettingRow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Profile("db")
@Service
final class PostgresManagementConfigService {

    private final ManagementMapper mapper;
    private final AuditLogWriter auditLogWriter;

    PostgresManagementConfigService(ManagementMapper mapper, AuditLogWriter auditLogWriter) {
        this.mapper = mapper;
        this.auditLogWriter = auditLogWriter;
    }

    PageResponse<IntegrationView> integrations(PageQuery pageQuery) {
        return page(mapper::listIntegrations, mapper::countIntegrations, pageQuery, values());
    }

    IntegrationView integration(String key) {
        return integrationView(integrationRow(key));
    }

    IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor) {
        String key = integrationKey(request.code());
        if (key.isBlank()) {
            key = nextCode("integration");
        }
        String name = defaultText(request.name(), key);
        String category = defaultText(request.category(), "未分类");
        String scope = defaultText(request.scope(), "平台级");
        String configKey = integrationConfigKey(key);
        try {
            update(mapper::insertConfig, actor, values(
                    "scopeType", "SYSTEM",
                    "configKey", configKey,
                    "valueJson", integrationJson(name, category, scope)
            ));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "集成配置已存在");
        }
        IntegrationView created = integrationView(integrationRow(key));
        audit(actor, "登记集成", "integration", configKey, created.name());
        return created;
    }

    IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor) {
        IntegrationRow current = integrationRow(key);
        String name = defaultText(request.name(), current.name());
        String category = defaultText(request.category(), current.category());
        String scope = defaultText(request.scope(), current.scope());
        update(mapper::updateIntegration, actor, values(
                "configKey", current.configKey(),
                "valueJson", integrationJson(name, category, scope)
        ));
        IntegrationView updated = integrationView(integrationRow(current.key()));
        audit(actor, "更新集成", "integration", current.configKey(), updated.name());
        return updated;
    }

    IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "集成配置状态只支持 ENABLED 或 DISABLED");
        }
        IntegrationRow current = integrationRow(key);
        update(mapper::changeConfigStatus, actor, values("configKey", current.configKey(), "status", status));
        IntegrationView updated = integrationView(integrationRow(current.key()));
        audit(actor, "ENABLED".equals(status) ? "启用集成" : "停用集成", "integration", current.configKey(), updated.name());
        return updated;
    }

    PageResponse<SettingView> settings(PageQuery pageQuery) {
        PageResponse<SettingRow> rows = page(mapper::listSettings, mapper::countSettings, pageQuery, values());
        return PageResponse.of(rows.items().stream().map(this::settingView).toList(), pageQuery.index(), pageQuery.size(), rows.total());
    }

    SettingView setting(String key) {
        return settingView(settingRow(key));
    }

    SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor) {
        String key = normalizeSearch(request.key());
        rejectSensitivePlainSetting(key, request.value());
        String scopeType = defaultText(request.scopeType(), "SYSTEM");
        String name = defaultText(request.name(), settingName(key));
        try {
            update(mapper::insertConfig, actor, values(
                    "scopeType", scopeType,
                    "configKey", key,
                    "valueJson", settingJson(name, request.value().trim())
            ));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统设置已存在");
        }
        SettingView created = settingView(settingRow(key));
        audit(actor, "创建设置", "config", key, created.name());
        return created;
    }

    SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor) {
        SettingRow current = settingRow(key);
        SettingView currentView = settingView(current);
        String name = defaultText(request.name(), currentView.name());
        String value = defaultText(request.value(), currentView.value());
        rejectSensitivePlainSetting(currentView.key(), value);
        String scopeType = defaultText(request.scopeType(), current.scopeType());
        update(mapper::updateSetting, actor, values(
                "scopeType", scopeType,
                "configKey", current.configKey(),
                "valueJson", settingJson(name, value)
        ));
        SettingView updated = settingView(settingRow(current.configKey()));
        audit(actor, "更新设置", "config", current.configKey(), updated.name());
        return updated;
    }

    SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统设置状态只支持 ENABLED 或 DISABLED");
        }
        SettingRow current = settingRow(key);
        update(mapper::changeConfigStatus, actor, values("status", status, "configKey", current.configKey()));
        SettingView updated = settingView(settingRow(current.configKey()));
        audit(actor, "ENABLED".equals(status) ? "启用设置" : "停用设置", "config", current.configKey(), updated.name());
        return updated;
    }

    private IntegrationRow integrationRow(String key) {
        return requireOne(mapper::findIntegrationRow, values("key", normalizeSearch(key)), "集成配置不存在");
    }

    private SettingRow settingRow(String key) {
        return requireOne(mapper::findSettingRow, values("key", normalizeSearch(key)), "系统设置不存在");
    }

    private IntegrationView integrationView(IntegrationRow row) {
        return new IntegrationView(row.key(), row.name(), row.category(), row.scope(), row.status());
    }

    private SettingView settingView(SettingRow row) {
        return new SettingView(
                row.configKey(),
                defaultText(row.displayName(), settingName(row.configKey())),
                row.value(),
                scopeName(row.scopeType()),
                row.status()
        );
    }

    private String integrationKey(String code) {
        return normalizeSearch(code).toLowerCase();
    }

    private String integrationConfigKey(String key) {
        return "integration." + key;
    }

    private String integrationJson(String name, String category, String scope) {
        return "{\"name\":\"" + escapeJson(name) + "\","
                + "\"category\":\"" + escapeJson(category) + "\","
                + "\"scope\":\"" + escapeJson(scope) + "\"}";
    }

    private String settingJson(String name, String value) {
        return "{\"_display_name\":\"" + escapeJson(name) + "\","
                + "\"_value\":\"" + escapeJson(value) + "\"}";
    }

    private void rejectSensitivePlainSetting(String key, String value) {
        String normalizedKey = normalizeSearch(key).toLowerCase();
        String normalizedValue = normalizeSearch(value);
        boolean sensitiveKey = normalizedKey.matches(".*(password|passwd|pwd|secret|token|api[_.-]?key|cookie|credential|private[_.-]?key).*");
        if (sensitiveKey && !normalizedValue.matches("^(\\*+|已配置|secret-ref:.+|\\$\\{[A-Za-z0-9_]+})$")) {
            throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "敏感配置必须使用密钥引用或掩码值");
        }
    }

    private String settingName(String configKey) {
        return switch (configKey) {
            case "audit.retention_days" -> "审计日志保留";
            case "audit.retention_cleanup_enabled" -> "审计保留清理";
            case "audit.retention_min_days" -> "审计最小保留";
            case "audit.retention_cleanup_batch_size" -> "审计清理批量";
            case "session.access_token_ttl_minutes" -> "访问令牌有效期";
            case "allow_public_model" -> "允许公有云模型";
            case "sensitivity_level" -> "默认敏感级别";
            case "default_resource_pool" -> "默认资源池";
            case "secret.default_provider" -> "默认密钥提供方";
            default -> configKey;
        };
    }

    private String scopeName(String scopeType) {
        return switch (scopeType) {
            case "SYSTEM" -> "平台级";
            case "PROJECT" -> "项目级";
            case "APPLICATION" -> "应用级";
            case "ENVIRONMENT" -> "环境级";
            default -> scopeType;
        };
    }

    private String nextCode(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String defaultText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private String escapeJson(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
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
        T value = statement.apply(params);
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
