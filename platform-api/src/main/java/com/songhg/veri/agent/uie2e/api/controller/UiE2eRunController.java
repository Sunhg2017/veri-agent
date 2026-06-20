package com.songhg.veri.agent.uie2e.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.uie2e.application.UiE2eRunService;
import com.songhg.veri.agent.uie2e.application.command.CancelUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunPageRequest;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunExportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunSummaryResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/ui-e2e/runs")
public class UiE2eRunController {

    private final UiE2eRunService service;

    public UiE2eRunController(UiE2eRunService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(value = PermissionCodes.UI_E2E_EXECUTE, scope = UiE2ePermissionScopes.RUN_REQUEST)
    public ResponseEntity<UiE2eRunDetailResponse> createRun(@Valid @RequestBody CreateUiE2eRunCommand command) {
        UiE2eRunDetailResponse response = service.createRun(command);
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.RUN_LIST)
    public PageResponse<UiE2eRunSummaryResponse> runs(@Valid UiE2eRunPageRequest request) {
        return service.runs(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.RUN)
    public UiE2eRunDetailResponse run(@PathVariable UUID id) {
        return service.run(id);
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission(value = PermissionCodes.UI_E2E_EXECUTE, scope = UiE2ePermissionScopes.RUN)
    public UiE2eRunDetailResponse cancelRun(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelUiE2eRunCommand command
    ) {
        return service.cancelRun(id, command);
    }

    @GetMapping("/{id}/export")
    @RequirePermission(value = PermissionCodes.UI_E2E_EXPORT, scope = UiE2ePermissionScopes.RUN)
    public UiE2eRunExportResponse exportRun(@PathVariable UUID id) {
        return service.exportRun(id);
    }

    @GetMapping("/{id}/artifacts/{artifactId}/download")
    @RequirePermission(value = PermissionCodes.UI_E2E_EXPORT, scope = UiE2ePermissionScopes.RUN)
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable UUID id, @PathVariable UUID artifactId) {
        UiE2eRunService.DownloadableArtifact artifact = service.downloadArtifact(id, artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(artifact.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(artifact.content());
    }
}
