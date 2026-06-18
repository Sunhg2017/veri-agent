package com.songhg.veri.agent.scheduling.application;

import com.songhg.veri.agent.testdesign.application.TestDesignEventRecoveryService;
import com.songhg.veri.agent.testdesign.application.TestDesignPublishCompensationService;
import com.songhg.veri.agent.testdesign.application.TestDesignPublishEventRecoveryService;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.log.XxlJobFileAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestDesignJobHandlerTest {

    @AfterEach
    void clearContext() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void publishCompensationJobDelegatesToServiceWrapper() throws Exception {
        TestDesignPublishCompensationService compensationService = mock(TestDesignPublishCompensationService.class);
        TestDesignJobHandler handler = new TestDesignJobHandler(
                mock(TestDesignEventRecoveryService.class),
                mock(TestDesignPublishEventRecoveryService.class),
                compensationService
        );
        XxlJobContext context = new XxlJobContext(3001L, "", logFileName("test-design"), 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.testDesignPublishCompensationJob();

        verify(compensationService).compensateFailedLinkedCandidates("xxl-job");
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    }

    private String logFileName(String testName) throws Exception {
        Path logDir = Files.createTempDirectory("xxl-job-handler-test");
        XxlJobFileAppender.initLogPath(logDir.toString());
        return logDir.resolve(testName + ".log").toString();
    }
}
