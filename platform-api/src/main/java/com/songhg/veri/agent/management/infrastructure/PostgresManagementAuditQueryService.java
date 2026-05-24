package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.management.application.port.AuditOperations;
import com.songhg.veri.agent.management.application.view.AuditLogView;
import com.songhg.veri.agent.management.application.view.AuditOutboxView;
import com.songhg.veri.agent.management.application.query.AuditLogQuery;
import com.songhg.veri.agent.management.application.query.AuditOutboxQuery;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Service
@Transactional
class PostgresManagementAuditQueryService implements AuditOperations {

    private final ManagementMapper mapper;
    private final AuditLogWriter auditLogWriter;

    PostgresManagementAuditQueryService(ManagementMapper mapper, AuditLogWriter auditLogWriter) {
        this.mapper = mapper;
        this.auditLogWriter = auditLogWriter;
    }

    public PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        Map<String, Object> params = auditParams(pageQuery, query, actor);
        List<AuditLogView> items = mapper.listAuditLogs(params);
        long total = mapper.countAuditLogs(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    public String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor) {
        PageQuery exportPage = PageQuery.of(0, 100);
        PageResponse<AuditLogView> page = auditLogs(exportPage, query, actor);
        StringBuilder csv = new StringBuilder("time,actor,action,target,result\n");
        page.items().forEach(item -> {
            appendCsvValue(csv, item.time());
            appendCsvValue(csv, item.actor());
            appendCsvValue(csv, item.action());
            appendCsvValue(csv, item.target());
            appendCsvValue(csv, item.result());
            csv.setLength(csv.length() - 1);
            csv.append('\n');
        });
        audit(actor, "导出审计", "audit_log", "audit_export", "审计日志导出");
        return csv.toString();
    }

    public PageResponse<AuditOutboxView> auditOutbox(
            PageQuery pageQuery,
            AuditOutboxQuery query,
            AuthUserPrincipal actor
    ) {
        Map<String, Object> params = auditOutboxParams(pageQuery, query);
        List<AuditOutboxView> items = mapper.listAuditOutbox(params);
        long total = mapper.countAuditOutbox(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    private Map<String, Object> auditParams(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        Map<String, Object> params = pageParams(pageQuery, scope(actor));
        params.put("actor", query.actor());
        params.put("action", query.action());
        params.put("resourceType", query.resourceType());
        params.put("result", normalizeAuditResult(query.result()));
        params.put("startTime", query.startTime());
        params.put("endTime", query.endTime());
        return params;
    }

    private Map<String, Object> auditOutboxParams(PageQuery pageQuery, AuditOutboxQuery query) {
        Map<String, Object> params = pageParams(pageQuery, values());
        params.put("status", query.status());
        params.put("traceId", query.traceId());
        return params;
    }

    private Map<String, Object> pageParams(PageQuery pageQuery, Map<String, Object> extraParams) {
        Map<String, Object> params = new HashMap<>(extraParams);
        params.put("search", pageQuery.search());
        params.put("searchPattern", pageQuery.searchPattern());
        params.put("limit", pageQuery.size());
        params.put("offset", pageQuery.offset());
        return params;
    }

    private Map<String, Object> scope(AuthUserPrincipal actor) {
        return values("actorId", actor.userId(), "platformScope", hasPlatformScope(actor));
    }

    private boolean hasPlatformScope(AuthUserPrincipal actor) {
        return actor.roles().stream().anyMatch(role -> List.of("SuperAdmin", "PlatformAdmin", "Auditor").contains(role));
    }

    private String normalizeAuditResult(String result) {
        return switch (normalizeSearch(result).toUpperCase()) {
            case "成功", "SUCCESS" -> "SUCCESS";
            case "拒绝", "DENIED" -> "DENIED";
            case "失败", "FAILED" -> "FAILED";
            default -> normalizeSearch(result).toUpperCase();
        };
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private void appendCsvValue(StringBuilder csv, Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        csv.append('"').append(escaped).append('"').append(',');
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
}
