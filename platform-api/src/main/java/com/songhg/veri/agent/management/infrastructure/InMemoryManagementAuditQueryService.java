package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.audit.InMemoryAuditLogWriter;
import com.songhg.veri.agent.management.api.response.AuditLogView;
import com.songhg.veri.agent.management.api.response.AuditOutboxView;
import com.songhg.veri.agent.management.application.AuditLogQuery;
import com.songhg.veri.agent.management.application.AuditOutboxQuery;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class InMemoryManagementAuditQueryService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<AuditLogView> auditLogs = new ArrayList<>();
    private final List<AuditOutboxView> auditOutbox = new ArrayList<>();
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementAuditQueryService(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        auditLogs.addAll(List.of(
                new AuditLogView("2026-05-16 10:31", "system", "健康检查", "platform-api", "成功"),
                new AuditLogView("2026-05-16 09:48", "shao.min", "创建部门", "端体验组", "成功"),
                new AuditLogView("2026-05-15 18:12", "he.xu", "更新角色", "ProjectOwner", "成功")
        ));
        auditOutbox.addAll(List.of(
                new AuditOutboxView(
                        "8f57078c-4a7f-4b80-bf72-7ef03d252001",
                        "trc_outbox_pending",
                        "audit:pending:001",
                        "PENDING",
                        1,
                        "2026-05-21 10:05",
                        "",
                        "",
                        "",
                        "创建部门",
                        "department",
                        "dept-qa",
                        "SUCCESS",
                        "2026-05-21 10:00",
                        "2026-05-21 10:00"
                ),
                new AuditOutboxView(
                        "8f57078c-4a7f-4b80-bf72-7ef03d252002",
                        "trc_outbox_failed",
                        "audit:failed:001",
                        "FAILED",
                        4,
                        "2026-05-21 10:30",
                        "",
                        "wp1-audit-worker-1",
                        "insert audit_log timeout",
                        "重置密码",
                        "user",
                        "tester.lifecycle",
                        "SUCCESS",
                        "2026-05-21 09:45",
                        "2026-05-21 09:58"
                )
        ));
    }

    PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        List<AuditLogView> combined = new ArrayList<>();
        combined.addAll(InMemoryAuditLogWriter.records().stream()
                .map(this::auditRecordView)
                .toList());
        combined.addAll(auditLogs);
        List<AuditLogView> filtered = combined.stream()
                .filter(item -> matchesAuditLog(item, query))
                .toList();
        return page(filtered, PageQuery.of(pageQuery.index(), pageQuery.size()));
    }

    String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor) {
        PageResponse<AuditLogView> page = auditLogs(PageQuery.of(0, 100), query, actor);
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
        audit(actor, "导出审计", "audit_log");
        return csv.toString();
    }

    PageResponse<AuditOutboxView> auditOutbox(PageQuery pageQuery, AuditOutboxQuery query, AuthUserPrincipal actor) {
        List<AuditOutboxView> filtered = auditOutbox.stream()
                .filter(item -> matchesAuditOutbox(item, query))
                .toList();
        return page(filtered, PageQuery.of(pageQuery.index(), pageQuery.size()));
    }

    private AuditLogView auditRecordView(AuditLogWriter.AuditRecord record) {
        return new AuditLogView(
                LocalDateTime.now().format(TIME_FORMAT),
                record.actor() == null ? "system" : record.actor().username(),
                record.action(),
                record.targetName(),
                resultName(record.result())
        );
    }

    private void appendCsvValue(StringBuilder csv, Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        csv.append('"').append(escaped).append('"').append(',');
    }

    private boolean matchesAuditLog(AuditLogView item, AuditLogQuery query) {
        String keyword = query.search().toLowerCase();
        if (!keyword.isBlank() && !item.toString().toLowerCase().contains(keyword)) {
            return false;
        }
        if (!query.actor().isBlank() && !item.actor().equalsIgnoreCase(query.actor())) {
            return false;
        }
        if (!query.action().isBlank() && !item.action().equals(query.action())) {
            return false;
        }
        if (!query.resourceType().isBlank() && !displayResourceType(item.action()).equalsIgnoreCase(query.resourceType())) {
            return false;
        }
        if (!query.result().isBlank()
                && !item.result().equalsIgnoreCase(query.result())
                && !item.result().equals(resultName(query.result()))) {
            return false;
        }
        OffsetDateTime itemTime = parseDisplayTime(item.time());
        if (query.startTime() != null && itemTime.isBefore(query.startTime())) {
            return false;
        }
        return query.endTime() == null || !itemTime.isAfter(query.endTime());
    }

    private boolean matchesAuditOutbox(AuditOutboxView item, AuditOutboxQuery query) {
        String keyword = query.search().toLowerCase();
        if (!keyword.isBlank() && !item.toString().toLowerCase().contains(keyword)) {
            return false;
        }
        if (!query.status().isBlank() && !item.status().equals(query.status())) {
            return false;
        }
        return query.traceId().isBlank() || item.traceId().equals(query.traceId());
    }

    private OffsetDateTime parseDisplayTime(String value) {
        LocalDateTime localDateTime = LocalDateTime.parse(value, TIME_FORMAT);
        return localDateTime.atOffset(ZoneOffset.ofHours(8));
    }

    private String displayResourceType(String action) {
        return switch (action) {
            case "创建部门" -> "department";
            case "邀请用户", "启用用户", "停用用户", "锁定用户", "解锁用户", "重置密码" -> "user";
            case "创建项目" -> "project";
            case "登记应用" -> "application";
            case "新增环境", "环境连通性检查" -> "environment";
            case "分配角色", "解绑角色" -> "rbac_role_binding";
            case "创建角色", "更新角色", "启用角色", "停用角色" -> "rbac_role";
            case "创建密钥引用", "轮换密钥引用", "撤销密钥引用" -> "secret_reference";
            default -> "";
        };
    }

    private String resultName(String result) {
        return switch (result.toUpperCase()) {
            case "SUCCESS" -> "成功";
            case "DENIED" -> "拒绝";
            case "FAILED" -> "失败";
            default -> result;
        };
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
