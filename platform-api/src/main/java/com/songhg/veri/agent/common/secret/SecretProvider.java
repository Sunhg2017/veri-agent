package com.songhg.veri.agent.common.secret;

import java.util.Optional;

public interface SecretProvider {

    default Optional<ResolvedSecret> resolve(String secretRef) {
        return resolve(secretRef, null);
    }

    Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context);
}
