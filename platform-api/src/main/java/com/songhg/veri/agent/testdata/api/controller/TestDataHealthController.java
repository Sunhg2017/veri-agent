package com.songhg.veri.agent.testdata.api.controller;

import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdata.application.TestDataHealthService;
import com.songhg.veri.agent.testdata.application.view.TestDataHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/test-data")
public class TestDataHealthController {

    private final TestDataHealthService service;

    public TestDataHealthController(TestDataHealthService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public TestDataHealthResponse health() {
        return service.health();
    }
}
