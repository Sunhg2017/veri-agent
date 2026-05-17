package com.songhg.veri.agent.modelaccess.application;

public record PlatformInvocationPolicy(
        String sensitivityLevel,
        boolean allowPublicModel
) {

    public static PlatformInvocationPolicy unrestricted() {
        return new PlatformInvocationPolicy(null, true);
    }
}
