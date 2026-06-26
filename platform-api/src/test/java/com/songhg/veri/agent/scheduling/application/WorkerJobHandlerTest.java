package com.songhg.veri.agent.scheduling.application;

import com.songhg.veri.agent.execution.application.ExecutionSchedulerService;
import com.songhg.veri.agent.reporting.application.ReportGenerationWorkerService;
import com.songhg.veri.agent.testdata.application.TestDataWorkerService;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.log.XxlJobFileAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkerJobHandlerTest {

    @AfterEach
    void clearContext() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void executionSchedulerJobDelegatesToManagedWorker() throws Exception {
        ExecutionSchedulerService executionSchedulerService = mock(ExecutionSchedulerService.class);
        WorkerJobHandler handler = new WorkerJobHandler(
                executionSchedulerService,
                mock(TestDataWorkerService.class),
                mock(ReportGenerationWorkerService.class)
        );
        XxlJobContext context = new XxlJobContext(1001L, "", logFileName("worker-success"), 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.executionSchedulerJob();

        verify(executionSchedulerService).runOnce();
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg()).contains("traceId=");
    }

    @Test
    void testDataWorkerJobDelegatesToWp8Worker() throws Exception {
        TestDataWorkerService testDataWorkerService = mock(TestDataWorkerService.class);
        WorkerJobHandler handler = new WorkerJobHandler(
                mock(ExecutionSchedulerService.class),
                testDataWorkerService,
                mock(ReportGenerationWorkerService.class)
        );
        XxlJobContext context = new XxlJobContext(1003L, "", logFileName("wp8-worker-success"), 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.testDataWorkerJob();

        verify(testDataWorkerService).runOnce();
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg()).contains("traceId=");
    }

    @Test
    void reportGenerationWorkerJobMarksFailureWhenWorkerThrows() throws Exception {
        ReportGenerationWorkerService reportGenerationWorkerService = mock(ReportGenerationWorkerService.class);
        doThrow(new IllegalStateException("failed secret://wp10 token=abc123"))
                .when(reportGenerationWorkerService)
                .runOnce();
        WorkerJobHandler handler = new WorkerJobHandler(
                mock(ExecutionSchedulerService.class),
                mock(TestDataWorkerService.class),
                reportGenerationWorkerService
        );
        XxlJobContext context = new XxlJobContext(1002L, "", logFileName("worker-failure"), 0, 1);
        XxlJobContext.setXxlJobContext(context);

        assertThatThrownBy(handler::reportGenerationWorkerJob)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed secret://wp10 token=abc123");
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
        assertThat(context.getHandleMsg()).contains("traceId=");
        assertThat(context.getHandleMsg()).contains("[REDACTED_SECRET_REF]");
        assertThat(context.getHandleMsg()).contains("[REDACTED]");
    }

    private String logFileName(String testName) throws Exception {
        Path logDir = Files.createTempDirectory("xxl-job-handler-test");
        XxlJobFileAppender.initLogPath(logDir.toString());
        return logDir.resolve(testName + ".log").toString();
    }
}
