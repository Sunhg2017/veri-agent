package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.modelaccess.api.mapper.ModelAccessApiMapper;
import com.songhg.veri.agent.modelaccess.api.request.CreateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.request.UpdateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.response.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderResilienceResponse;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access/providers")
public class ModelProviderController {

    private final ModelAccessService service;
    private final ModelAccessApiMapper apiMapper;

    public ModelProviderController(ModelAccessService service, ModelAccessApiMapper apiMapper) {
        this.service = service;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public List<ModelProviderConfig> providers() {
        return service.providers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ModelProviderConfig createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return service.createProvider(apiMapper.toCommand(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ModelProviderConfig updateProvider(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        return service.updateProvider(id, apiMapper.toCommand(request));
    }

    @PostMapping("/{id}/enable")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ModelProviderConfig enableProvider(@PathVariable UUID id) {
        return service.setProviderStatus(id, ProviderStatus.ENABLED);
    }

    @PostMapping("/{id}/disable")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ModelProviderConfig disableProvider(@PathVariable UUID id) {
        return service.setProviderStatus(id, ProviderStatus.DISABLED);
    }

    @PostMapping("/{id}/check")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ProviderCheckResponse checkProvider(@PathVariable UUID id) {
        return apiMapper.toResponse(service.checkProvider(id));
    }

    @GetMapping("/{id}/resilience")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public ProviderResilienceResponse providerResilience(@PathVariable UUID id) {
        return apiMapper.toResponse(service.providerResilience(id));
    }

    @PostMapping("/{id}/circuit/reset")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ProviderResilienceResponse resetProviderCircuit(@PathVariable UUID id) {
        return apiMapper.toResponse(service.resetProviderCircuit(id));
    }
}
