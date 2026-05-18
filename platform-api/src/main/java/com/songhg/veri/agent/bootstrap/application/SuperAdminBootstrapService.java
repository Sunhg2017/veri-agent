package com.songhg.veri.agent.bootstrap.application;

import com.songhg.veri.agent.bootstrap.api.request.SuperAdminBootstrapRequest;
import com.songhg.veri.agent.bootstrap.api.response.SuperAdminBootstrapResponse;
import com.songhg.veri.agent.bootstrap.domain.BootstrapStateStore;
import com.songhg.veri.agent.bootstrap.domain.BootstrapUserDraft;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.security.TokenSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SuperAdminBootstrapService {

    private static final String SUPER_ADMIN_ROLE = "SuperAdmin";

    private final BootstrapProperties properties;
    private final BootstrapStateStore stateStore;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminBootstrapService(
            BootstrapProperties properties,
            BootstrapStateStore stateStore,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.passwordEncoder = passwordEncoder;
    }

    public SuperAdminBootstrapResponse bootstrap(SuperAdminBootstrapRequest request) {
        verifyBootstrapToken(request.bootstrapToken());

        if (stateStore.hasSuperAdmin()) {
            throw new BusinessException(ErrorCode.CONFLICT, "超级管理员已初始化");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        BootstrapUserDraft draft = new BootstrapUserDraft(
                request.username(),
                passwordHash,
                request.displayName(),
                request.email(),
                SUPER_ADMIN_ROLE,
                true
        );

        String userId = stateStore.createSuperAdmin(draft);
        return new SuperAdminBootstrapResponse(userId, SUPER_ADMIN_ROLE, true);
    }

    private void verifyBootstrapToken(String providedToken) {
        String expectedToken = properties.token();
        if (!StringUtils.hasText(expectedToken)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "初始化令牌未配置");
        }
        if (!TokenSecurity.constantTimeEquals(expectedToken, providedToken)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "初始化令牌无效");
        }
    }
}
