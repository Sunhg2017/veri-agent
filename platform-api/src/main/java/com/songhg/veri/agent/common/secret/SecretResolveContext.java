package com.songhg.veri.agent.common.secret;

public record SecretResolveContext(
        String purpose,
        String callerService,
        String scopeType,
        String scopeId
) {
}
