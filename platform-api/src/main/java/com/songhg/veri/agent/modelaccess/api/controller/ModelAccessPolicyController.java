package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.modelaccess.api.request.UpsertModelAccessPolicyRequest;
import com.songhg.veri.agent.modelaccess.api.response.ModelAccessPolicyResponse;
import com.songhg.veri.agent.modelaccess.application.ModelAccessActorResolver;
import com.songhg.veri.agent.modelaccess.application.ModelAccessPolicyOperationsService;
import com.songhg.veri.agent.modelaccess.application.command.UpsertModelAccessPolicyCommand;
import com.songhg.veri.agent.modelaccess.application.query.ModelAccessPolicyQuery;
import com.songhg.veri.agent.modelaccess.application.view.ModelAccessEffectivePolicy;
import com.songhg.veri.agent.modelaccess.domain.ModelAccessPolicyOverride;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access/policies")
public class ModelAccessPolicyController {

    private final ModelAccessPolicyOperationsService service;
    private final ModelAccessActorResolver actorResolver;

    public ModelAccessPolicyController(
            ModelAccessPolicyOperationsService service,
            ModelAccessActorResolver actorResolver
    ) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public List<ModelAccessPolicyResponse> policies(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeKey
    ) {
        return service.policies(new ModelAccessPolicyQuery(scopeType, scopeKey))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ModelAccessPolicyResponse upsertPolicy(@Valid @RequestBody UpsertModelAccessPolicyRequest request) {
        return toResponse(service.upsertPolicy(new UpsertModelAccessPolicyCommand(
                request.getScopeType(),
                request.getScopeKey(),
                request.getEnabled(),
                request.getModelInvocationEnabled(),
                request.getPublicModelAllowed(),
                request.getDailyBudgetLimit(),
                request.getCostAlertWarningRatio(),
                request.getBudgetOverrunAction(),
                request.getRoutingGroup(),
                request.getReason()
        )));
    }

    @GetMapping("/effective")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public ModelAccessEffectivePolicy effectivePolicy(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String environmentId,
            @RequestParam(required = false) List<String> roles
    ) {
        return service.effectivePolicy(projectId, environmentId, previewRoles(roles));
    }

    private List<String> previewRoles(List<String> roles) {
        List<String> normalized = new ArrayList<>();
        if (roles != null) {
            for (String roleGroup : roles) {
                if (!StringUtils.hasText(roleGroup)) {
                    continue;
                }
                for (String role : roleGroup.split(",")) {
                    String value = role.trim();
                    if (!value.isBlank() && !normalized.contains(value)) {
                        normalized.add(value);
                    }
                }
            }
        }
        if (!normalized.isEmpty()) {
            return normalized;
        }
        AuthUserPrincipal principal = actorResolver.currentUserPrincipal();
        return principal == null ? List.of() : principal.roles();
    }

    private ModelAccessPolicyResponse toResponse(ModelAccessPolicyOverride policy) {
        return new ModelAccessPolicyResponse(
                policy.id(),
                policy.scopeType(),
                policy.scopeKey(),
                policy.enabled(),
                policy.modelInvocationEnabled(),
                policy.publicModelAllowed(),
                policy.dailyBudgetLimit(),
                policy.costAlertWarningRatio(),
                policy.budgetOverrunAction(),
                policy.routingGroup(),
                policy.reason(),
                policy.updatedBy(),
                policy.createdAt(),
                policy.updatedAt(),
                false
        );
    }
}
