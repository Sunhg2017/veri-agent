package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.documentinput.application.DocumentInputService;
import com.songhg.veri.agent.documentinput.application.command.CreateDocumentImportRequest;
import com.songhg.veri.agent.documentinput.application.command.DocumentPublishRequest;
import com.songhg.veri.agent.documentinput.application.query.DocumentCandidatePageRequest;
import com.songhg.veri.agent.documentinput.application.query.DocumentImportPageRequest;
import com.songhg.veri.agent.documentinput.application.query.DocumentImportQuery;
import com.songhg.veri.agent.documentinput.application.view.DocumentCandidateResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentImportResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentPublishRecordResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentPublishResponse;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@ApiVersion
@RestController
@RequestMapping("/api/v1/document-input/imports")
public class DocumentImportController {

    private final DocumentInputService service;

    public DocumentImportController(DocumentInputService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_IMPORT)
    public DocumentImportResponse importDocument(@Valid @RequestBody CreateDocumentImportRequest request) {
        return service.importDocument(request);
    }

    @PostMapping(path = "/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_IMPORT)
    public DocumentImportResponse importMultipart(
            @RequestParam String projectId,
            @RequestParam DocumentSourceType sourceType,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String sourceRef,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) UUID mappingId,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam("file") MultipartFile file
    ) throws java.io.IOException {
        return service.importMultipart(
                projectId,
                sourceType,
                title,
                sourceRef,
                sourceUrl,
                mappingId,
                sourceId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
    }

    @GetMapping
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public PageResponse<DocumentImportResponse> imports(@Valid DocumentImportPageRequest request) {
        return service.imports(new DocumentImportQuery(
                request.getProjectId(),
                request.getSourceId(),
                request.getSourceType(),
                request.getStatus(),
                request.toPageQuery()
        ));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public DocumentImportResponse importRecord(@PathVariable UUID id) {
        return service.importRecord(id);
    }

    @GetMapping("/{id}/candidates")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public PageResponse<DocumentCandidateResponse> candidates(
            @PathVariable UUID id,
            @Valid DocumentCandidatePageRequest request
    ) {
        return service.candidates(request.toQuery(id));
    }

    @PostMapping("/{id}/publish")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_PUBLISH)
    public DocumentPublishResponse publishImport(
            @PathVariable UUID id,
            @RequestBody(required = false) DocumentPublishRequest request
    ) {
        return service.publishImport(id, request);
    }

    @GetMapping("/{id}/publish-records")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public PageResponse<DocumentPublishRecordResponse> publishRecords(@PathVariable UUID id) {
        return service.publishRecords(id);
    }
}
