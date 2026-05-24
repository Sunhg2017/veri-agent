package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.documentinput.application.DocumentInputService;
import com.songhg.veri.agent.documentinput.application.command.UpdateFieldMappingRequest;
import com.songhg.veri.agent.documentinput.application.command.UpsertDocumentSourceRequest;
import com.songhg.veri.agent.documentinput.application.query.DocumentSourcePageRequest;
import com.songhg.veri.agent.documentinput.application.query.DocumentSourceQuery;
import com.songhg.veri.agent.documentinput.application.view.DocumentSourceHealthResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentSourceResponse;
import com.songhg.veri.agent.documentinput.application.view.FieldMappingResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/document-input")
public class DocumentSourceController {

    private final DocumentInputService service;

    public DocumentSourceController(DocumentInputService service) {
        this.service = service;
    }

    @GetMapping("/sources")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public PageResponse<DocumentSourceResponse> sources(@Valid DocumentSourcePageRequest request) {
        return service.sources(new DocumentSourceQuery(
                request.getSourceType(),
                request.getStatus(),
                request.toPageQuery()
        ));
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_MANAGE)
    public DocumentSourceResponse createSource(@Valid @RequestBody UpsertDocumentSourceRequest request) {
        return service.createSource(request);
    }

    @PutMapping("/sources/{id}")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_MANAGE)
    public DocumentSourceResponse updateSource(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertDocumentSourceRequest request
    ) {
        return service.updateSource(id, request);
    }

    @GetMapping("/sources/{id}/health")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public DocumentSourceHealthResponse sourceHealth(@PathVariable UUID id) {
        return service.sourceHealth(id);
    }

    @GetMapping("/field-mapping")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public FieldMappingResponse fieldMapping() {
        return service.fieldMapping();
    }

    @PutMapping("/field-mapping")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_MANAGE)
    public FieldMappingResponse updateFieldMapping(@Valid @RequestBody UpdateFieldMappingRequest request) {
        return service.updateFieldMapping(request);
    }
}
