package com.songhg.veri.agent.reporting.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.reporting.application.ReportService;
import com.songhg.veri.agent.reporting.application.command.GenerateReportCommand;
import com.songhg.veri.agent.reporting.application.query.ReportPageRequest;
import com.songhg.veri.agent.reporting.application.view.ReportDiagnosisResponse;
import com.songhg.veri.agent.reporting.application.view.ReportDetailResponse;
import com.songhg.veri.agent.reporting.application.view.ReportSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(value = PermissionCodes.REPORT_GENERATE, scope = ReportPermissionScopes.REPORT_REQUEST)
    public ResponseEntity<ApiResponse<ReportDetailResponse>> generateReport(
            @Valid @RequestBody GenerateReportCommand command
    ) {
        ReportDetailResponse response = service.generateReport(command);
        HttpStatus status = response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.ok(response, TraceContext.getTraceId()));
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.REPORT_READ, scope = ReportPermissionScopes.REPORT_LIST)
    public PageResponse<ReportSummaryResponse> reports(@Valid ReportPageRequest request) {
        return service.reports(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.REPORT_READ, scope = ReportPermissionScopes.REPORT)
    public ReportDetailResponse report(@PathVariable UUID id) {
        return service.report(id);
    }

    @PostMapping("/{id}/retry")
    @RequirePermission(value = PermissionCodes.REPORT_GENERATE, scope = ReportPermissionScopes.REPORT)
    public ReportDetailResponse retryReport(@PathVariable UUID id) {
        return service.retryReport(id);
    }

    @PostMapping("/{id}/archive")
    @RequirePermission(value = PermissionCodes.REPORT_MANAGE, scope = ReportPermissionScopes.REPORT)
    public ReportDetailResponse archiveReport(@PathVariable UUID id) {
        return service.archiveReport(id);
    }

    @PostMapping("/{id}/diagnoses")
    @RequirePermission(value = PermissionCodes.REPORT_DIAGNOSE, scope = ReportPermissionScopes.REPORT)
    public ReportDiagnosisResponse diagnoseReport(@PathVariable UUID id) {
        return service.diagnoseReport(id);
    }

    @GetMapping("/{id}/diagnoses/latest")
    @RequirePermission(value = PermissionCodes.REPORT_READ, scope = ReportPermissionScopes.REPORT)
    public ReportDiagnosisResponse latestDiagnosis(@PathVariable UUID id) {
        return service.latestDiagnosis(id);
    }
}
