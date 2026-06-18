package com.songhg.veri.agent.scheduling.application;

import com.songhg.veri.agent.auth.application.AuthSessionCleanupService;
import com.songhg.veri.agent.common.audit.AuditRetentionCleanupService;
import com.songhg.veri.agent.document.application.DocumentInputEventRecoveryService;
import com.songhg.veri.agent.document.application.DocumentInputRetentionCleanupService;
import com.songhg.veri.agent.document.application.DocumentWebhookAutoRetryService;
import com.songhg.veri.agent.notification.application.NotificationStreamService;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.log.XxlJobFileAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CoreMaintenanceJobHandlerTest {

    @AfterEach
    void clearContext() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void notificationHeartbeatJobDelegatesToStreamService() throws Exception {
        NotificationStreamService notificationStreamService = mock(NotificationStreamService.class);
        CoreMaintenanceJobHandler handler = new CoreMaintenanceJobHandler(
                mock(AuthSessionCleanupService.class),
                mock(AuditRetentionCleanupService.class),
                mock(DocumentInputEventRecoveryService.class),
                mock(DocumentWebhookAutoRetryService.class),
                mock(DocumentInputRetentionCleanupService.class),
                notificationStreamService
        );
        XxlJobContext context = new XxlJobContext(2001L, "", logFileName("heartbeat"), 0, 2);
        XxlJobContext.setXxlJobContext(context);

        handler.notificationStreamHeartbeatJob();

        verify(notificationStreamService).heartbeat();
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    }

    private String logFileName(String testName) throws Exception {
        Path logDir = Files.createTempDirectory("xxl-job-handler-test");
        XxlJobFileAppender.initLogPath(logDir.toString());
        return logDir.resolve(testName + ".log").toString();
    }
}
