package com.songhg.veri.agent.common.secret;

public record ResolvedSecret(
        /** 业务侧引用的密钥标识。 */
        String secretRef,
        /** 解析后的明文密钥值；禁止写入日志。 */
        String value,
        /** 实际解析该密钥的提供方编码。 */
        String provider,
        /** 密钥版本。 */
        String version
) {
    @Override
    public String toString() {
        return "ResolvedSecret[secretRef=%s, value=****, provider=%s, version=%s]"
                .formatted(secretRef, provider, version);
    }
}
