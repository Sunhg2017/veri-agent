package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.CreateIntegrationRequest;
import com.songhg.veri.agent.management.application.command.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.application.view.IntegrationView;

/**
 * Integration management use cases. Integrations represent external system links, so mutations must
 * preserve auditability and avoid exposing secret material in returned views.
 */
public interface IntegrationOperations {

    /**
     * Lists configured external integrations.
     */
    PageResponse<IntegrationView> integrations(PageQuery pageQuery);

    /**
     * Returns one integration by key.
     */
    IntegrationView integration(String key);

    /**
     * Creates an integration configuration after input validation at the API boundary.
     */
    IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor);

    /**
     * Updates non-secret integration metadata and records the actor.
     */
    IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor);

    /**
     * Enables or disables an integration after the caller passes status-specific permission checks.
     */
    IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor);
}
