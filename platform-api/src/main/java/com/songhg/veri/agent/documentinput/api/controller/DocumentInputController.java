package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.documentinput.api.request.CandidateBatchActionRequest;
import com.songhg.veri.agent.documentinput.api.request.ConfirmDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.api.request.CreateDocumentImportRequest;
import com.songhg.veri.agent.documentinput.api.request.DocumentCandidatePageRequest;
import com.songhg.veri.agent.documentinput.api.request.DocumentImportPageRequest;
import com.songhg.veri.agent.documentinput.api.request.DocumentPublishRequest;
import com.songhg.veri.agent.documentinput.api.request.DocumentSourcePageRequest;
import com.songhg.veri.agent.documentinput.api.request.IgnoreDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.api.request.UpdateFieldMappingRequest;
import com.songhg.veri.agent.documentinput.api.request.UpdateDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.api.request.UpsertDocumentSourceRequest;
import com.songhg.veri.agent.documentinput.api.request.WebhookEventPageRequest;
import com.songhg.veri.agent.documentinput.api.response.DocumentCandidateBatchActionResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentCandidateResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentImportResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentInputHealthResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentPublishRecordResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentPublishResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentSourceHealthResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentSourceResponse;
import com.songhg.veri.agent.documentinput.api.response.DocumentWebhookEventResponse;
import com.songhg.veri.agent.documentinput.api.response.FieldMappingResponse;
import com.songhg.veri.agent.documentinput.application.DocumentImportQuery;
import com.songhg.veri.agent.documentinput.application.DocumentInputService;
import com.songhg.veri.agent.documentinput.application.DocumentSourceQuery;
import com.songhg.veri.agent.documentinput.application.DocumentWebhookEventQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/document-input")
public class DocumentInputController {

    private final DocumentInputService service;
    private final AuthorizationService authorizationService;

    public DocumentInputController(DocumentInputService service, AuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/health")
    public DocumentInputHealthResponse health() {
        return service.health();
    }

    @GetMapping("/sources")
    public PageResponse<DocumentSourceResponse> sources(@Valid DocumentSourcePageRequest request) {
        requirePermission("requirementInput:read");
        return service.sources(new DocumentSourceQuery(
                request.getSourceType(),
                request.getStatus(),
                request.toPageQuery()
        ));
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentSourceResponse createSource(@Valid @RequestBody UpsertDocumentSourceRequest request) {
        requirePermission("requirementInput:manage");
        return service.createSource(request);
    }

    @PutMapping("/sources/{id}")
    public DocumentSourceResponse updateSource(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertDocumentSourceRequest request
    ) {
        requirePermission("requirementInput:manage");
        return service.updateSource(id, request);
    }

    @GetMapping("/sources/{id}/health")
    public DocumentSourceHealthResponse sourceHealth(@PathVariable UUID id) {
        requirePermission("requirementInput:read");
        return service.sourceHealth(id);
    }

    @GetMapping("/field-mapping")
    public FieldMappingResponse fieldMapping() {
        requirePermission("requirementInput:read");
        return service.fieldMapping();
    }

    @PutMapping("/field-mapping")
    public FieldMappingResponse updateFieldMapping(@Valid @RequestBody UpdateFieldMappingRequest request) {
        requirePermission("requirementInput:manage");
        return service.updateFieldMapping(request);
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentImportResponse importDocument(@Valid @RequestBody CreateDocumentImportRequest request) {
        requirePermission("requirementInput:import");
        return service.importDocument(request);
    }

    @PostMapping(path = "/imports/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
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
        requirePermission("requirementInput:import");
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

    @GetMapping("/imports")
    public PageResponse<DocumentImportResponse> imports(@Valid DocumentImportPageRequest request) {
        requirePermission("requirementInput:read");
        return service.imports(new DocumentImportQuery(
                request.getProjectId(),
                request.getSourceId(),
                request.getSourceType(),
                request.getStatus(),
                request.toPageQuery()
        ));
    }

    @GetMapping("/imports/{id}")
    public DocumentImportResponse importRecord(@PathVariable UUID id) {
        requirePermission("requirementInput:read");
        return service.importRecord(id);
    }

    @GetMapping("/imports/{id}/candidates")
    public PageResponse<DocumentCandidateResponse> candidates(
            @PathVariable UUID id,
            @Valid DocumentCandidatePageRequest request
    ) {
        requirePermission("requirementInput:read");
        return service.candidates(request.toQuery(id));
    }

    @PostMapping("/imports/{id}/publish")
    public DocumentPublishResponse publishImport(
            @PathVariable UUID id,
            @RequestBody(required = false) DocumentPublishRequest request
    ) {
        requirePermission("requirementInput:publish");
        return service.publishImport(id, request);
    }

    @GetMapping("/imports/{id}/publish-records")
    public PageResponse<DocumentPublishRecordResponse> publishRecords(@PathVariable UUID id) {
        requirePermission("requirementInput:read");
        return service.publishRecords(id);
    }

    @PutMapping("/candidates/{id}")
    public DocumentCandidateResponse updateCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentCandidateRequest request
    ) {
        requirePermission("requirementInput:candidate_review");
        return service.updateCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/confirm")
    public DocumentCandidateResponse confirmCandidate(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmDocumentCandidateRequest request
    ) {
        requirePermission("requirementInput:candidate_review");
        return service.confirmCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/ignore")
    public DocumentCandidateResponse ignoreCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) IgnoreDocumentCandidateRequest request
    ) {
        requirePermission("requirementInput:candidate_review");
        return service.ignoreCandidate(id, request);
    }

    @PostMapping("/candidates/batch-action")
    public DocumentCandidateBatchActionResponse batchCandidateAction(
            @Valid @RequestBody CandidateBatchActionRequest request
    ) {
        requirePermission("requirementInput:candidate_review");
        return service.batchCandidateAction(request);
    }

    @GetMapping("/webhook-events")
    public PageResponse<DocumentWebhookEventResponse> webhookEvents(@Valid WebhookEventPageRequest request) {
        requirePermission("requirementInput:read");
        return service.webhookEvents(request.toQuery());
    }

    @GetMapping("/webhook-events/{id}")
    public DocumentWebhookEventResponse webhookEvent(@PathVariable UUID id) {
        requirePermission("requirementInput:read");
        return service.webhookEvent(id);
    }

    @PostMapping("/webhook-events/{id}/replay")
    public DocumentWebhookEventResponse replayWebhookEvent(@PathVariable UUID id) {
        requirePermission("requirementInput:webhook_replay");
        return service.replayWebhookEvent(id);
    }

    @PostMapping("/webhooks/{sourceCode}")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentImportResponse webhook(
            @PathVariable String sourceCode,
            @RequestBody String payload,
            @RequestHeader(name = "X-VA-Timestamp", required = false) String timestamp,
            @RequestHeader(name = "X-VA-Signature", required = false) String signature,
            @RequestHeader(name = "X-VA-Event-Id", required = false) String eventId,
            @RequestHeader(name = "X-VA-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-VA-Event-Version", required = false) String eventVersion
    ) {
        return service.handleWebhook(sourceCode, payload, timestamp, signature, eventId, idempotencyKey, eventVersion);
    }

    private void requirePermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ServicePrincipal) {
            return;
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserPrincipal principal) {
            authorizationService.require(principal, permission);
            return;
        }
        throw new AccessDeniedException("缺少权限：" + permission);
    }
}
