package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.documentinput.application.DocumentInputService;
import com.songhg.veri.agent.documentinput.application.command.CandidateBatchActionRequest;
import com.songhg.veri.agent.documentinput.application.command.ConfirmDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.command.IgnoreDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.command.UpdateDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.view.DocumentCandidateBatchActionResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentCandidateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/document-input/candidates")
public class DocumentCandidateController {

    private final DocumentInputService service;

    public DocumentCandidateController(DocumentInputService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW)
    public DocumentCandidateResponse updateCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentCandidateRequest request
    ) {
        return service.updateCandidate(id, request);
    }

    @PostMapping("/{id}/confirm")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW)
    public DocumentCandidateResponse confirmCandidate(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmDocumentCandidateRequest request
    ) {
        return service.confirmCandidate(id, request);
    }

    @PostMapping("/{id}/ignore")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW)
    public DocumentCandidateResponse ignoreCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) IgnoreDocumentCandidateRequest request
    ) {
        return service.ignoreCandidate(id, request);
    }

    @PostMapping("/batch-action")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW)
    public DocumentCandidateBatchActionResponse batchCandidateAction(
            @Valid @RequestBody CandidateBatchActionRequest request
    ) {
        return service.batchCandidateAction(request);
    }
}
