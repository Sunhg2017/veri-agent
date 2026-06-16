package com.songhg.veri.agent.reporting.infrastructure.mapper;

import com.songhg.veri.agent.reporting.application.query.ReportQuery;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportingMapper {

    int insertReportIfAbsent(ReportExecutionReport report);

    void updateReport(ReportExecutionReport report);

    ReportExecutionReport report(@Param("id") UUID id);

    ReportExecutionReport reportByProjectRunRequestKey(
            @Param("projectId") String projectId,
            @Param("executionRunId") UUID executionRunId,
            @Param("requestKey") String requestKey
    );

    List<ReportExecutionReport> reports(@Param("query") ReportQuery query);

    long countReports(@Param("query") ReportQuery query);

    String reportProjectScopeId(@Param("id") UUID id);
}
