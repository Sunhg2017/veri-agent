package com.songhg.veri.agent.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionDraft(
        /** 会话 ID */
        UUID sessionId,
        /** 用户 ID */
        UUID userId,
        /** 访问令牌哈希，用于服务端校验和撤销 */
        String accessTokenHash,
        /** 刷新令牌哈希，用于刷新会话校验 */
        String refreshTokenHash,
        /** 用户认证版本号，用户凭证变更后用于使旧会话失效 */
        long authVersion,
        /** 过期时间 */
        Instant expiresAt
) {
}
