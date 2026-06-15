package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.command.AcquireTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.query.TestAccountLeasePageRequest;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolPageRequest;
import com.songhg.veri.agent.testdata.application.query.TestDataSetPageRequest;
import com.songhg.veri.agent.testdata.application.query.TestDataTaskPageRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestDataPermissionScopeResolver {

    private final TestDataPlatformContextClient contextClient;
    private final TestDataSetService dataSetService;
    private final TestAccountPoolService accountPoolService;
    private final TestAccountLeaseService accountLeaseService;
    private final TestDataTaskService dataTaskService;

    public TestDataPermissionScopeResolver(
            TestDataPlatformContextClient contextClient,
            TestDataSetService dataSetService,
            TestAccountPoolService accountPoolService,
            TestAccountLeaseService accountLeaseService,
            TestDataTaskService dataTaskService
    ) {
        this.contextClient = contextClient;
        this.dataSetService = dataSetService;
        this.accountPoolService = accountPoolService;
        this.accountLeaseService = accountLeaseService;
        this.dataTaskService = dataTaskService;
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

    public ResourceScope leaseRequest(AcquireTestAccountLeaseCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope leaseList(TestAccountLeasePageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope lease(UUID id) {
        return ResourceScope.project(accountLeaseService.accountLeaseProjectScopeId(id));
    }

    public ResourceScope taskRequest(CreateTestDataTaskCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope taskList(TestDataTaskPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope task(UUID id) {
        return ResourceScope.project(dataTaskService.dataTaskProjectScopeId(id));
    }

    private ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }
}
