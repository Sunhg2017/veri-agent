package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.documentinput.application.DocumentInputService;
import com.songhg.veri.agent.documentinput.application.query.WebhookEventPageRequest;
import com.songhg.veri.agent.documentinput.application.view.DocumentImportResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentWebhookEventResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/document-input")
public class DocumentWebhookController {

    private final DocumentInputService service;

    public DocumentWebhookController(DocumentInputService service) {
        this.service = service;
    }

    @GetMapping("/webhook-events")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public PageResponse<DocumentWebhookEventResponse> webhookEvents(@Valid WebhookEventPageRequest request) {
        return service.webhookEvents(request.toQuery());
    }

    @GetMapping("/webhook-events/{id}")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public DocumentWebhookEventResponse webhookEvent(@PathVariable UUID id) {
        return service.webhookEvent(id);
    }

    @PostMapping("/webhook-events/{id}/replay")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_WEBHOOK_REPLAY)
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
