package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves WP5 task and candidate project scopes for permission checks.
 */
@Service
public class TestDesignScopeService {

    private final TestDesignRepository repository;

    public TestDesignScopeService(TestDesignRepository repository) {
        this.repository = repository;
    }

    public String taskProjectScopeId(UUID id) {
        return repository.task(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + id))
                .projectId();
    }

    public String candidateProjectScopeId(UUID id) {
        return repository.candidate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选用例不存在: " + id))
                .projectId();
    }

    public String contextPolicyOverrideProjectScopeId(UUID id) {
        return repository.contextPolicyOverride(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上下文策略覆盖不存在: " + id))
                .projectId();
    }
}
