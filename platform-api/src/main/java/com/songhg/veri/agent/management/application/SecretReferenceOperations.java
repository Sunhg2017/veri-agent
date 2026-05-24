package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.request.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.response.SecretReferenceView;

public interface SecretReferenceOperations {

    PageResponse<SecretReferenceView> secrets(PageQuery pageQuery);

    SecretReferenceView createSecret(CreateSecretReferenceRequest request, AuthUserPrincipal actor);

    SecretReferenceView rotateSecret(RotateSecretReferenceRequest request, AuthUserPrincipal actor);

    SecretReferenceView disableSecret(DisableSecretReferenceRequest request, AuthUserPrincipal actor);
}
