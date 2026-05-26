package com.songhg.veri.agent.auth.domain;

import java.util.List;
import java.util.UUID;

public record AuthUserRecord(
        /** 用户 ID。 */
        UUID userId,
        /** 用户名。 */
        String username,
        /** 用户显示名。 */
        String displayName,
        /** 邮箱。 */
        String email,
        /** 密码哈希。 */
        String passwordHash,
        /** 是否必须修改密码。 */
        boolean mustChangePassword,
        /** 认证版本号，密码或安全状态变化时递增。 */
        long authVersion,
        /** 用户角色编码列表。 */
        List<String> roles
) {
}
