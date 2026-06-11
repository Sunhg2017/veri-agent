package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApiAutomationScopeService {

    private final ApiAutomationRepository repository;

    public ApiAutomationScopeService(ApiAutomationRepository repository) {
        this.repository = repository;
    }

    public String specProjectScopeId(UUID id) {
        return repository.specProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OpenAPI 规格不存在: " + id));
    }

    public String generationTaskProjectScopeId(UUID id) {
        return repository.generationTaskProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化生成任务不存在: " + id));
    }
}
