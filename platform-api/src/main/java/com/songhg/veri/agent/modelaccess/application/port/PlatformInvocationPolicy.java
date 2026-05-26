package com.songhg.veri.agent.modelaccess.application.port;

public record PlatformInvocationPolicy(
        /** 平台上下文要求的敏感级别。 */
        String sensitivityLevel,
        /** 平台上下文是否允许公共模型。 */
        boolean allowPublicModel
) {

    public static PlatformInvocationPolicy unrestricted() {
        return new PlatformInvocationPolicy(null, true);
    }
}
