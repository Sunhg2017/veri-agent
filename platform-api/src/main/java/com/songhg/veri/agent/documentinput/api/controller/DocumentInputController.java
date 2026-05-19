package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.common.api.PageResponse;
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
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-input")
public class DocumentInputController {

    private final DocumentInputService service;

    public DocumentInputController(DocumentInputService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public DocumentInputHealthResponse health() {
        return service.health();
    }

    @GetMapping("/sources")
    public PageResponse<DocumentSourceResponse> sources(@Valid DocumentSourcePageRequest request) {
        return service.sources(new DocumentSourceQuery(
                request.getSourceType(),
                request.getStatus(),
                request.toPageQuery()
        ));
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentSourceResponse createSource(@Valid @RequestBody UpsertDocumentSourceRequest request) {
        return service.createSource(request);
    }

    @PutMapping("/sources/{id}")
    public DocumentSourceResponse updateSource(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertDocumentSourceRequest request
    ) {
        return service.updateSource(id, request);
    }

    @GetMapping("/sources/{id}/health")
    public DocumentSourceHealthResponse sourceHealth(@PathVariable UUID id) {
        return service.sourceHealth(id);
    }

    @GetMapping("/field-mapping")
    public FieldMappingResponse fieldMapping() {
        return service.fieldMapping();
    }

    @PutMapping("/field-mapping")
    public FieldMappingResponse updateFieldMapping(@Valid @RequestBody UpdateFieldMappingRequest request) {
        return service.updateFieldMapping(request);
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentImportResponse importDocument(@Valid @RequestBody CreateDocumentImportRequest request) {
        return service.importDocument(request);
    }

    @GetMapping("/imports")
    public PageResponse<DocumentImportResponse> imports(@Valid DocumentImportPageRequest request) {
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
        return service.importRecord(id);
    }

    @GetMapping("/imports/{id}/candidates")
    public PageResponse<DocumentCandidateResponse> candidates(
            @PathVariable UUID id,
            @Valid DocumentCandidatePageRequest request
    ) {
        return service.candidates(id, request.toPageQuery());
    }

    @PostMapping("/imports/{id}/publish")
    public DocumentPublishResponse publishImport(
            @PathVariable UUID id,
            @RequestBody(required = false) DocumentPublishRequest request
    ) {
        return service.publishImport(id, request);
    }

    @GetMapping("/imports/{id}/publish-records")
    public PageResponse<DocumentPublishRecordResponse> publishRecords(@PathVariable UUID id) {
        return service.publishRecords(id);
    }

    @PutMapping("/candidates/{id}")
    public DocumentCandidateResponse updateCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentCandidateRequest request
    ) {
        return service.updateCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/confirm")
    public DocumentCandidateResponse confirmCandidate(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmDocumentCandidateRequest request
    ) {
        return service.confirmCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/ignore")
    public DocumentCandidateResponse ignoreCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) IgnoreDocumentCandidateRequest request
    ) {
        return service.ignoreCandidate(id, request);
    }

    @PostMapping("/candidates/batch-action")
    public DocumentCandidateBatchActionResponse batchCandidateAction(
            @Valid @RequestBody CandidateBatchActionRequest request
    ) {
        return service.batchCandidateAction(request);
    }

    @GetMapping("/webhook-events")
    public PageResponse<DocumentWebhookEventResponse> webhookEvents(@Valid WebhookEventPageRequest request) {
        return service.webhookEvents(new DocumentWebhookEventQuery(
                request.getSourceCode(),
                request.getStatus(),
                request.toPageQuery()
        ));
    }

    @GetMapping("/webhook-events/{id}")
    public DocumentWebhookEventResponse webhookEvent(@PathVariable UUID id) {
        return service.webhookEvent(id);
    }

    @PostMapping("/webhook-events/{id}/replay")
    public DocumentWebhookEventResponse replayWebhookEvent(@PathVariable UUID id) {
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
}
