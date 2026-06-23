package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.reporting.application.command.BatchReportExportCommand;
import com.songhg.veri.agent.execution.application.ExecutionRunService;
import com.songhg.veri.agent.reporting.application.command.GenerateReportCommand;
import com.songhg.veri.agent.reporting.application.query.ReportPageRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReportPermissionScopeResolver {

    private final ReportingPlatformContextClient contextClient;
    private final ReportService reportService;
    private final ExecutionRunService executionRunService;

    public ReportPermissionScopeResolver(
            ReportingPlatformContextClient contextClient,
            ReportService reportService,
            ExecutionRunService executionRunService
    ) {
        this.contextClient = contextClient;
        this.reportService = reportService;
        this.executionRunService = executionRunService;
    }

    public ResourceScope reportRequest(GenerateReportCommand command) {
        if (command != null && StringUtils.hasText(command.projectId())) {
            return project(command.projectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope reportList(ReportPageRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        if (request != null && request.getExecutionRunId() != null) {
            return ResourceScope.project(executionRunService.runProjectScopeId(request.getExecutionRunId()));
        }
        return ResourceScope.platform();
    }

    public ResourceScope report(UUID id) {
        return ResourceScope.project(reportService.reportProjectScopeId(id));
    }

    public List<ResourceScope> reportCompare(UUID id, UUID baselineReportId) {
        List<ResourceScope> scopes = new ArrayList<>();
        if (id != null) {
            scopes.add(report(id));
        }
        if (baselineReportId != null) {
            scopes.add(report(baselineReportId));
        }
        return scopes.isEmpty() ? List.of(ResourceScope.platform()) : List.copyOf(scopes);
    }

    public List<ResourceScope> reportBatchExport(BatchReportExportCommand command) {
        List<ResourceScope> scopes = new ArrayList<>();
        if (command != null && command.reportIds() != null) {
            for (UUID reportId : command.reportIds()) {
                if (reportId != null) {
                    scopes.add(report(reportId));
                }
            }
        }
        return scopes.isEmpty() ? List.of(ResourceScope.platform()) : List.copyOf(scopes);
    }

    private ResourceScope project(String projectId) {
        return ResourceScope.project(contextClient.projectContext(projectId).resourceId());
    }
}
