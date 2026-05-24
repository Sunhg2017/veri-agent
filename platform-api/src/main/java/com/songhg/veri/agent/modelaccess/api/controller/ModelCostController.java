package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.modelaccess.api.mapper.ModelAccessApiMapper;
import com.songhg.veri.agent.modelaccess.api.response.CostAlertResponse;
import com.songhg.veri.agent.modelaccess.api.response.CostReportResponse;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access/cost")
public class ModelCostController {

    private final ModelAccessService service;
    private final ModelAccessApiMapper apiMapper;

    public ModelCostController(ModelAccessService service, ModelAccessApiMapper apiMapper) {
        this.service = service;
        this.apiMapper = apiMapper;
    }

    @GetMapping("/alerts")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public List<CostAlertResponse> costAlerts(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String actorService
    ) {
        return apiMapper.toCostAlertResponses(service.costAlerts(projectId, actorService));
    }

    @GetMapping("/report")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public CostReportResponse costReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String projectId
    ) {
        return apiMapper.toResponse(service.costReport(startDate, endDate, projectId));
    }
}
