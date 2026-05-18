package com.songhg.veri.agent.bootstrap.api.response;


public record SuperAdminBootstrapResponse(
        String userId,
        String role,
        boolean mustChangePassword
) {
}
