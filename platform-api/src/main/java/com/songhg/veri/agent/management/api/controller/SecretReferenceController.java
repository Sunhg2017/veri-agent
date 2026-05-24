package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.response.SecretReferenceResponse;
import com.songhg.veri.agent.management.application.port.SecretReferenceOperations;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Secret reference endpoint. The controller only accepts secret material; encryption, rotation,
 * revocation and audit side effects are constrained inside the secret operation service.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class SecretReferenceController {

    private final SecretReferenceOperations secretReferenceOperations;
    private final ManagementApiMapper mapper;

    public SecretReferenceController(
            SecretReferenceOperations secretReferenceOperations,
            ManagementApiMapper mapper
    ) {
        this.secretReferenceOperations = secretReferenceOperations;
        this.mapper = mapper;
    }

    @GetMapping("/secrets")
    @RequirePermission(PermissionCodes.SECRET_READ)
    public PageResponse<SecretReferenceResponse> secrets(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toSecretReferencePage(secretReferenceOperations.secrets(pageRequest.toPageQuery()));
    }

    @PostMapping("/secrets")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.SECRET_MANAGE)
    public SecretReferenceResponse createSecret(
            @Valid @RequestBody CreateSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(secretReferenceOperations.createSecret(mapper.toCommand(request), principal));
    }

    @PostMapping("/secrets/rotate")
    @RequirePermission(PermissionCodes.SECRET_ROTATE)
    public SecretReferenceResponse rotateSecret(
            @Valid @RequestBody RotateSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(secretReferenceOperations.rotateSecret(mapper.toCommand(request), principal));
    }

    @PostMapping("/secrets/disable")
    @RequirePermission(PermissionCodes.SECRET_DISABLE)
    public SecretReferenceResponse disableSecret(
            @Valid @RequestBody DisableSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(secretReferenceOperations.disableSecret(mapper.toCommand(request), principal));
    }
}
