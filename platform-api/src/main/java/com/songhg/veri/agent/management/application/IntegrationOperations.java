package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.request.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.api.response.IntegrationView;

public interface IntegrationOperations {

    PageResponse<IntegrationView> integrations(PageQuery pageQuery);

    IntegrationView integration(String key);

    IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor);

    IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor);

    IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor);
}
