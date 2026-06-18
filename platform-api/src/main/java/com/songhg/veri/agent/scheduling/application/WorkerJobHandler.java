package com.songhg.veri.agent.scheduling.application;

import com.songhg.veri.agent.execution.application.ExecutionSchedulerService;
import com.songhg.veri.agent.reporting.application.ReportGenerationWorkerService;
import com.songhg.veri.agent.testdata.application.TestDataWorkerService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB handlers for bounded queue workers that claim and process async work.
 */
@Component
@ConditionalOnProperty(prefix = "veri-agent.xxl-job", name = "enabled", havingValue = "true")
public class WorkerJobHandler {

    private final ExecutionSchedulerService executionSchedulerService;
    private final TestDataWorkerService testDataWorkerService;
    private final ReportGenerationWorkerService reportGenerationWorkerService;

    public WorkerJobHandler(
            ExecutionSchedulerService executionSchedulerService,
            TestDataWorkerService testDataWorkerService,
            ReportGenerationWorkerService reportGenerationWorkerService
    ) {
        this.executionSchedulerService = executionSchedulerService;
        this.testDataWorkerService = testDataWorkerService;
        this.reportGenerationWorkerService = reportGenerationWorkerService;
    }

    @XxlJob("executionSchedulerJob")
    public void executionSchedulerJob() throws Exception {
        XxlJobTraceSupport.execute("executionSchedulerJob", () -> {
            executionSchedulerService.runOnce();
            return null;
        });
    }

    @XxlJob("testDataWorkerJob")
    public void testDataWorkerJob() throws Exception {
        XxlJobTraceSupport.execute("testDataWorkerJob", () -> {
            testDataWorkerService.runOnce();
            return null;
        });
    }

    @XxlJob("reportGenerationWorkerJob")
    public void reportGenerationWorkerJob() throws Exception {
        XxlJobTraceSupport.execute("reportGenerationWorkerJob", () -> {
            reportGenerationWorkerService.runOnce();
            return null;
        });
    }
}
