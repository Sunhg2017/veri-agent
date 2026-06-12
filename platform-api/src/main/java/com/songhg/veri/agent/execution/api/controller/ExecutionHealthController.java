package com.songhg.veri.agent.execution.api.controller;

import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.execution.application.ExecutionHealthService;
import com.songhg.veri.agent.execution.application.view.ExecutionHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/execution")
public class ExecutionHealthController {

    private final ExecutionHealthService service;

    public ExecutionHealthController(ExecutionHealthService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ExecutionHealthResponse health() {
        return service.health();
    }
}
