package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.documentinput.application.CandidateBatchActionRequest;
import com.songhg.veri.agent.documentinput.application.ConfirmDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.CreateDocumentImportRequest;
import com.songhg.veri.agent.documentinput.application.DocumentCandidatePageRequest;
import com.songhg.veri.agent.documentinput.application.DocumentImportPageRequest;
import com.songhg.veri.agent.documentinput.application.DocumentParseFeedbackPageRequest;
import com.songhg.veri.agent.documentinput.application.DocumentPublishRequest;
import com.songhg.veri.agent.documentinput.application.DocumentSourcePageRequest;
import com.songhg.veri.agent.documentinput.application.IgnoreDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.UpdateFieldMappingRequest;
import com.songhg.veri.agent.documentinput.application.UpdateDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.UpsertDocumentSourceRequest;
import com.songhg.veri.agent.documentinput.application.WebhookEventPageRequest;
import com.songhg.veri.agent.documentinput.application.DocumentCandidateBatchActionResponse;
import com.songhg.veri.agent.documentinput.application.DocumentCandidateResponse;
import com.songhg.veri.agent.documentinput.application.DocumentImportResponse;
import com.songhg.veri.agent.documentinput.application.DocumentInputHealthResponse;
import com.songhg.veri.agent.documentinput.application.DocumentParseFeedbackSampleResponse;
import com.songhg.veri.agent.documentinput.application.DocumentPublishRecordResponse;
import com.songhg.veri.agent.documentinput.application.DocumentPublishResponse;
import com.songhg.veri.agent.documentinput.application.DocumentSourceHealthResponse;
import com.songhg.veri.agent.documentinput.application.DocumentSourceResponse;
import com.songhg.veri.agent.documentinput.application.DocumentWebhookEventResponse;
import com.songhg.veri.agent.documentinput.application.FieldMappingResponse;
import com.songhg.veri.agent.documentinput.application.DocumentImportQuery;
import com.songhg.veri.agent.documentinput.application.DocumentInputService;
import com.songhg.veri.agent.documentinput.application.DocumentSourceQuery;
import com.songhg.veri.agent.documentinput.application.DocumentWebhookEventQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

@ApiVersion
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
    @RequirePermission("requirementInput:read")
    public PageResponse<DocumentSourceResponse> sources(@Valid DocumentSourcePageRequest request) {
        return service.sources(new DocumentSourceQuery(
                request.getSourceType(),
                request.getStatus(),
                request.toPageQuery()
        ));
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("requirementInput:manage")
    public DocumentSourceResponse createSource(@Valid @RequestBody UpsertDocumentSourceRequest request) {
        return service.createSource(request);
    }

    @PutMapping("/sources/{id}")
    @RequirePermission("requirementInput:manage")
    public DocumentSourceResponse updateSource(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertDocumentSourceRequest request
    ) {
        return service.updateSource(id, request);
    }

    @GetMapping("/sources/{id}/health")
    @RequirePermission("requirementInput:read")
    public DocumentSourceHealthResponse sourceHealth(@PathVariable UUID id) {
        return service.sourceHealth(id);
    }

    @GetMapping("/field-mapping")
    @RequirePermission("requirementInput:read")
    public FieldMappingResponse fieldMapping() {
        return service.fieldMapping();
    }

    @PutMapping("/field-mapping")
    @RequirePermission("requirementInput:manage")
    public FieldMappingResponse updateFieldMapping(@Valid @RequestBody UpdateFieldMappingRequest request) {
        return service.updateFieldMapping(request);
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("requirementInput:import")
    public DocumentImportResponse importDocument(@Valid @RequestBody CreateDocumentImportRequest request) {
        return service.importDocument(request);
    }

    @PostMapping(path = "/imports/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("requirementInput:import")
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

    @GetMapping("/imports")
    @RequirePermission("requirementInput:read")
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
    @RequirePermission("requirementInput:read")
    public DocumentImportResponse importRecord(@PathVariable UUID id) {
        return service.importRecord(id);
    }

    @GetMapping("/imports/{id}/candidates")
    @RequirePermission("requirementInput:read")
    public PageResponse<DocumentCandidateResponse> candidates(
            @PathVariable UUID id,
            @Valid DocumentCandidatePageRequest request
    ) {
        return service.candidates(request.toQuery(id));
    }

    @PostMapping("/imports/{id}/publish")
    @RequirePermission("requirementInput:publish")
    public DocumentPublishResponse publishImport(
            @PathVariable UUID id,
            @RequestBody(required = false) DocumentPublishRequest request
    ) {
        return service.publishImport(id, request);
    }

    @GetMapping("/imports/{id}/publish-records")
    @RequirePermission("requirementInput:read")
    public PageResponse<DocumentPublishRecordResponse> publishRecords(@PathVariable UUID id) {
        return service.publishRecords(id);
    }

    @PutMapping("/candidates/{id}")
    @RequirePermission("requirementInput:candidate_review")
    public DocumentCandidateResponse updateCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentCandidateRequest request
    ) {
        return service.updateCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/confirm")
    @RequirePermission("requirementInput:candidate_review")
    public DocumentCandidateResponse confirmCandidate(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmDocumentCandidateRequest request
    ) {
        return service.confirmCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/ignore")
    @RequirePermission("requirementInput:candidate_review")
    public DocumentCandidateResponse ignoreCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) IgnoreDocumentCandidateRequest request
    ) {
        return service.ignoreCandidate(id, request);
    }

    @PostMapping("/candidates/batch-action")
    @RequirePermission("requirementInput:candidate_review")
    public DocumentCandidateBatchActionResponse batchCandidateAction(
            @Valid @RequestBody CandidateBatchActionRequest request
    ) {
        return service.batchCandidateAction(request);
    }

    @GetMapping("/feedback-samples")
    @RequirePermission("requirementInput:read")
    public PageResponse<DocumentParseFeedbackSampleResponse> feedbackSamples(
            @Valid DocumentParseFeedbackPageRequest request
    ) {
        return service.parseFeedbackSamples(request.toQuery());
    }

    @GetMapping("/webhook-events")
    @RequirePermission("requirementInput:read")
    public PageResponse<DocumentWebhookEventResponse> webhookEvents(@Valid WebhookEventPageRequest request) {
        return service.webhookEvents(request.toQuery());
    }

    @GetMapping("/webhook-events/{id}")
    @RequirePermission("requirementInput:read")
    public DocumentWebhookEventResponse webhookEvent(@PathVariable UUID id) {
        return service.webhookEvent(id);
    }

    @PostMapping("/webhook-events/{id}/replay")
    @RequirePermission("requirementInput:webhook_replay")
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
            @RequestHeader(name = "X-VA-Event-Version", required = false) String eventVersion,
            HttpServletRequest request
    ) {
        return service.handleWebhook(
                sourceCode,
                payload,
                timestamp,
                signature,
                eventId,
                idempotencyKey,
                eventVersion,
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP")
        );
    }

}
