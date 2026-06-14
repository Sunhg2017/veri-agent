package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolPageRequest;
import com.songhg.veri.agent.testdata.application.query.TestDataSetPageRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestDataPermissionScopeResolver {

    private final TestDataPlatformContextClient contextClient;
    private final TestDataSetService dataSetService;
    private final TestAccountPoolService accountPoolService;

    public TestDataPermissionScopeResolver(
            TestDataPlatformContextClient contextClient,
            TestDataSetService dataSetService,
            TestAccountPoolService accountPoolService
    ) {
        this.contextClient = contextClient;
        this.dataSetService = dataSetService;
        this.accountPoolService = accountPoolService;
    }

    public ResourceScope dataSetRequest(CreateTestDataSetCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope dataSetList(TestDataSetPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope dataSet(UUID id) {
        return ResourceScope.project(dataSetService.dataSetProjectScopeId(id));
    }

    public ResourceScope accountPoolRequest(CreateTestAccountPoolCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope accountPoolList(TestAccountPoolPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope accountPool(UUID id) {
        return ResourceScope.project(accountPoolService.accountPoolProjectScopeId(id));
    }

    public ResourceScope pooledAccount(UUID id) {
        return ResourceScope.project(accountPoolService.pooledAccountProjectScopeId(id));
    }

    private ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }
}
