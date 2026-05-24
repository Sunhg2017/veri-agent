package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.modelaccess.api.response.ProviderHealthResponse;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access")
public class ModelAccessController {

    private final ModelAccessService service;

    public ModelAccessController(ModelAccessService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ProviderHealthResponse health() {
        return new ProviderHealthResponse(
                "model-access",
                "UP",
                service.enabledProviderCount(),
                service.activePromptCount(),
                service.providerRateLimitEnabled(),
                service.providerRateLimitMaxRequests(),
                service.providerRateLimitWindowSeconds(),
                service.providerConcurrencyLimitEnabled(),
                service.providerMaxConcurrentRequests(),
                service.openCircuitProviderCount()
        );
    }
}
