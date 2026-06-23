package com.songhg.veri.agent.reporting.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.reporting.application.ReportDefectDraftService;
import com.songhg.veri.agent.reporting.application.ReportExportService;
import com.songhg.veri.agent.reporting.application.ReportCompareService;
import com.songhg.veri.agent.reporting.application.ReportService;
import com.songhg.veri.agent.reporting.application.command.GenerateReportCommand;
import com.songhg.veri.agent.reporting.application.command.ReviewDefectDraftCommand;
import com.songhg.veri.agent.reporting.application.query.ReportPageRequest;
import com.songhg.veri.agent.reporting.application.view.ReportCompareResponse;
import com.songhg.veri.agent.reporting.application.view.ReportDefectDraftResponse;
import com.songhg.veri.agent.reporting.application.view.ReportDiagnosisResponse;
import com.songhg.veri.agent.reporting.application.view.ReportDetailResponse;
import com.songhg.veri.agent.reporting.application.view.ReportExportResponse;
import com.songhg.veri.agent.reporting.application.view.ReportSummaryResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;
    private final ReportCompareService compareService;
    private final ReportExportService exportService;
    private final ReportDefectDraftService defectDraftService;

    public ReportController(
            ReportService service,
            ReportCompareService compareService,
            ReportExportService exportService,
            ReportDefectDraftService defectDraftService
    ) {
        this.service = service;
        this.compareService = compareService;
        this.exportService = exportService;
        this.defectDraftService = defectDraftService;
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

    @GetMapping("/{id}/compare")
    @RequirePermission(value = PermissionCodes.REPORT_READ, scope = ReportPermissionScopes.REPORT_COMPARE)
    public ReportCompareResponse compareReport(
            @PathVariable UUID id,
            @RequestParam UUID baselineReportId
    ) {
        return compareService.compare(id, baselineReportId);
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

    @PostMapping("/{id}/defect-drafts")
    @RequirePermission(value = PermissionCodes.REPORT_GENERATE, scope = ReportPermissionScopes.REPORT)
    public ResponseEntity<ApiResponse<ReportDefectDraftResponse>> createDefectDraft(@PathVariable UUID id) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(defectDraftService.createDraft(id), TraceContext.getTraceId()));
    }

    @PatchMapping("/{id}/defect-drafts/{draftId}")
    @RequirePermission(value = PermissionCodes.REPORT_MANAGE, scope = ReportPermissionScopes.REPORT)
    public ReportDefectDraftResponse reviewDefectDraft(
            @PathVariable UUID id,
            @PathVariable UUID draftId,
            @Valid @RequestBody ReviewDefectDraftCommand command
    ) {
        return defectDraftService.reviewDraft(id, draftId, command);
    }

    @GetMapping("/{id}/export")
    @RequirePermission(value = PermissionCodes.REPORT_EXPORT, scope = ReportPermissionScopes.REPORT)
    public ReportExportResponse exportReport(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "JSON") String exportType
    ) {
        return exportService.exportReport(id, exportType);
    }

    @GetMapping("/{id}/exports/{exportId}/download")
    @RequirePermission(value = PermissionCodes.REPORT_EXPORT, scope = ReportPermissionScopes.REPORT)
    public ResponseEntity<byte[]> downloadExport(
            @PathVariable UUID id,
            @PathVariable UUID exportId
    ) {
        ReportExportService.DownloadableExport export = exportService.downloadExport(id, exportId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(export.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(export.content());
    }
}
