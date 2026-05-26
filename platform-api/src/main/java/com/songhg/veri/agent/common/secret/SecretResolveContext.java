package com.songhg.veri.agent.common.secret;

public record SecretResolveContext(
        /** 密钥使用目的。 */
        String purpose,
        /** 发起解析的调用方服务编码。 */
        String callerService,
        /** 密钥适用范围类型。 */
        String scopeType,
        /** 密钥适用范围 ID。 */
        String scopeId
) {
}
