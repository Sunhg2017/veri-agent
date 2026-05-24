package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.modelaccess.api.mapper.ModelAccessApiMapper;
import com.songhg.veri.agent.modelaccess.api.request.CreatePromptRequest;
import com.songhg.veri.agent.modelaccess.api.request.ReviewPromptRequest;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access/prompts")
public class ModelPromptController {

    private final ModelAccessService service;
    private final ModelAccessApiMapper apiMapper;

    public ModelPromptController(
            ModelAccessService service,
            ModelAccessApiMapper apiMapper
    ) {
        this.service = service;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public List<PromptTemplate> prompts(@RequestParam(required = false) String promptKey) {
        return service.prompts(promptKey);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public PromptTemplate createPrompt(@Valid @RequestBody CreatePromptRequest request) {
        return service.createPrompt(apiMapper.toCommand(request));
    }

    @PostMapping("/{id}/activate")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public PromptTemplate activatePrompt(@PathVariable UUID id) {
        return service.activatePrompt(id);
    }

    @PostMapping("/{id}/approve")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public PromptTemplate approvePrompt(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewPromptRequest request
    ) {
        return service.approvePrompt(id, request == null ? null : request.reviewNote());
    }

    @PostMapping("/{id}/reject")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public PromptTemplate rejectPrompt(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewPromptRequest request
    ) {
        return service.rejectPrompt(id, request == null ? null : request.reviewNote());
    }
}
