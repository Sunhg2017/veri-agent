package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.CreateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.DisableSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.RotateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.view.SecretReferenceView;

/**
 * Secret reference management use cases. Returned views must stay masked; clear text is accepted
 * only at creation or rotation boundaries and must not be stored outside the secret provider path.
 */
public interface SecretReferenceOperations {

    /**
     * Lists masked secret references for administrative review.
     */
    PageResponse<SecretReferenceView> secrets(PageQuery pageQuery);

    /**
     * Creates a new secret reference and stores provider-specific secret material.
     */
    SecretReferenceView createSecret(CreateSecretReferenceCommand request, AuthUserPrincipal actor);

    /**
     * Rotates the secret material for an existing active reference.
     */
    SecretReferenceView rotateSecret(RotateSecretReferenceCommand request, AuthUserPrincipal actor);

    /**
     * Disables a secret reference and removes runtime access to the secret material.
     */
    SecretReferenceView disableSecret(DisableSecretReferenceCommand request, AuthUserPrincipal actor);
}
