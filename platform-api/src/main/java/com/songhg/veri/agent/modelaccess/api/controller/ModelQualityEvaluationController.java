package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.modelaccess.api.mapper.ModelAccessApiMapper;
import com.songhg.veri.agent.modelaccess.api.response.ModelQualityEvaluationSummaryResponse;
import com.songhg.veri.agent.modelaccess.application.ModelQualityEvaluationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access/quality")
public class ModelQualityEvaluationController {

    private final ModelQualityEvaluationService evaluationService;
    private final ModelAccessApiMapper apiMapper;

    public ModelQualityEvaluationController(
            ModelQualityEvaluationService evaluationService,
            ModelAccessApiMapper apiMapper
    ) {
        this.evaluationService = evaluationService;
        this.apiMapper = apiMapper;
    }

    @GetMapping("/evaluation-summary")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public ModelQualityEvaluationSummaryResponse evaluationSummary(
            @RequestParam(required = false) String taskType
    ) {
        return apiMapper.toResponse(evaluationService.evaluationSummary(taskType));
    }
}
