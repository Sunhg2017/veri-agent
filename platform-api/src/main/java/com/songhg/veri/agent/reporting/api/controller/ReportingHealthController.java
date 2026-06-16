package com.songhg.veri.agent.reporting.api.controller;

import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.reporting.application.ReportingHealthService;
import com.songhg.veri.agent.reporting.application.view.ReportingHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingHealthController {

    private final ReportingHealthService service;

    public ReportingHealthController(ReportingHealthService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ReportingHealthResponse health() {
        return service.health();
    }
}
