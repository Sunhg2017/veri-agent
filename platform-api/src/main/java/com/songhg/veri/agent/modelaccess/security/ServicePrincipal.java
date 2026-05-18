package com.songhg.veri.agent.modelaccess.security;

public record ServicePrincipal(String callerService, String delegatedUserId) {
}
