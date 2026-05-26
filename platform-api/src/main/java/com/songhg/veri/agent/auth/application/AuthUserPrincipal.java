package com.songhg.veri.agent.auth.application;

import java.util.List;
import java.util.UUID;

public record AuthUserPrincipal(
        /** 当前登录用户 ID */
        UUID userId,
        /** 当前登录会话 ID */
        UUID sessionId,
        /** 登录账号 */
        String username,
        /** 用户展示名称 */
        String displayName,
        /** 用户邮箱 */
        String email,
        /** 是否必须先完成密码修改 */
        boolean mustChangePassword,
        /** 认证版本号，用于会话失效判断 */
        long authVersion,
        /** 当前用户拥有的角色编码列表 */
        List<String> roles
) {
}
