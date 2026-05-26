package com.songhg.veri.agent.auth.infrastructure.mapper;

import java.util.UUID;

public record AuthUserRow(
        /** 用户主键 ID。 */
        UUID userId,
        /** 登录账号。 */
        String username,
        /** 用户展示名称。 */
        String displayName,
        /** 用户邮箱。 */
        String email,
        /** 密码哈希。 */
        String passwordHash,
        /** 是否必须先修改密码。 */
        boolean mustChangePassword,
        /** 认证版本号，用于踢出旧会话。 */
        long authVersion,
        /** 聚合后的角色编码文本。 */
        String roleCodes
) {
}
