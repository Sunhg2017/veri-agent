package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.AuditLogPageRequest;
import com.songhg.veri.agent.management.api.request.AuditOutboxPageRequest;
import com.songhg.veri.agent.management.api.response.AuditLogView;
import com.songhg.veri.agent.management.api.response.AuditOutboxView;
import com.songhg.veri.agent.management.application.port.AuditOperations;
import com.songhg.veri.agent.management.application.query.AuditLogQuery;
import com.songhg.veri.agent.management.application.query.AuditOutboxQuery;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit read/export endpoint. Query construction is kept at the HTTP boundary because it combines
 * paging parameters with optional transport filters.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class AuditController {

    private final AuditOperations auditOperations;
    private final ManagementApiMapper mapper;

    public AuditController(AuditOperations auditOperations, ManagementApiMapper mapper) {
        this.auditOperations = auditOperations;
        this.mapper = mapper;
    }

    @GetMapping("/audit-logs")
    @RequirePermission(PermissionCodes.AUDIT_READ)
    public PageResponse<AuditLogView> auditLogs(
            @Valid AuditLogPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toAuditLogPage(
                auditOperations.auditLogs(pageRequest.toPageQuery(), auditLogQuery(pageRequest), principal)
        );
    }

    @GetMapping(value = "/audit-logs/export", produces = "text/csv")
    @RequirePermission(PermissionCodes.AUDIT_READ)
    @RequirePermission(PermissionCodes.AUDIT_EXPORT)
    public ResponseEntity<String> exportAuditLogs(
            @Valid AuditLogPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        String csv = auditOperations.exportAuditLogsCsv(auditLogQuery(pageRequest), principal);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp1-audit-logs.csv\"")
                .body(csv);
    }

    @GetMapping("/audit-outbox")
    @RequirePermission(PermissionCodes.AUDIT_READ)
    public PageResponse<AuditOutboxView> auditOutbox(
            @Valid AuditOutboxPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toAuditOutboxPage(
                auditOperations.auditOutbox(
                        pageRequest.toPageQuery(),
                        AuditOutboxQuery.of(pageRequest.getSearch(), pageRequest.getStatus(), pageRequest.getTraceId()),
                        principal
                )
        );
    }

    private AuditLogQuery auditLogQuery(AuditLogPageRequest pageRequest) {
        return AuditLogQuery.of(
                pageRequest.getSearch(),
                pageRequest.getActor(),
                pageRequest.getAction(),
                pageRequest.getResourceType(),
                pageRequest.getResult(),
                pageRequest.getStartTime(),
                pageRequest.getEndTime()
        );
    }
}
