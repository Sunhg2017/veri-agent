package com.songhg.veri.agent.document.api.controller;

import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.document.application.DocumentInputService;
import com.songhg.veri.agent.document.application.view.DocumentInputHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
