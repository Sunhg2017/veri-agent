package com.songhg.veri.agent.modelaccess.application.port;

public record PlatformInvocationPolicy(
        String sensitivityLevel,
        boolean allowPublicModel
) {

    public static PlatformInvocationPolicy unrestricted() {
        return new PlatformInvocationPolicy(null, true);
    }
}
