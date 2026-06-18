package com.songhg.veri.agent.uie2e.api.controller;

import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.uie2e.application.UiE2eHealthService;
import com.songhg.veri.agent.uie2e.application.view.UiE2eHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/ui-e2e")
public class UiE2eHealthController {

    private final UiE2eHealthService service;

    public UiE2eHealthController(UiE2eHealthService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public UiE2eHealthResponse health() {
        return service.health();
    }
}
