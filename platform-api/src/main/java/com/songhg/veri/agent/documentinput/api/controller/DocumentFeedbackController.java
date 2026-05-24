package com.songhg.veri.agent.documentinput.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.documentinput.application.DocumentInputService;
import com.songhg.veri.agent.documentinput.application.query.DocumentParseFeedbackPageRequest;
import com.songhg.veri.agent.documentinput.application.view.DocumentParseFeedbackSampleResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/document-input")
public class DocumentFeedbackController {

    private final DocumentInputService service;

    public DocumentFeedbackController(DocumentInputService service) {
        this.service = service;
    }

    @GetMapping("/feedback-samples")
    @RequirePermission(PermissionCodes.REQUIREMENT_INPUT_READ)
    public PageResponse<DocumentParseFeedbackSampleResponse> feedbackSamples(
            @Valid DocumentParseFeedbackPageRequest request
    ) {
        return service.parseFeedbackSamples(request.toQuery());
    }
}
