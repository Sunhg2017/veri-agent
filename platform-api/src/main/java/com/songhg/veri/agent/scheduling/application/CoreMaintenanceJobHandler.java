package com.songhg.veri.agent.scheduling.application;

import com.songhg.veri.agent.auth.application.AuthSessionCleanupService;
import com.songhg.veri.agent.common.audit.AuditRetentionCleanupService;
import com.songhg.veri.agent.document.application.DocumentInputEventRecoveryService;
import com.songhg.veri.agent.document.application.DocumentInputRetentionCleanupService;
import com.songhg.veri.agent.document.application.DocumentWebhookAutoRetryService;
import com.songhg.veri.agent.notification.application.NotificationStreamService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB handlers for cross-cutting platform maintenance and notification stream upkeep.
 */
@Component
@ConditionalOnProperty(prefix = "veri-agent.xxl-job", name = "enabled", havingValue = "true")
public class CoreMaintenanceJobHandler {

    private final AuthSessionCleanupService authSessionCleanupService;
    private final AuditRetentionCleanupService auditRetentionCleanupService;
    private final DocumentInputEventRecoveryService documentInputEventRecoveryService;
    private final DocumentWebhookAutoRetryService documentWebhookAutoRetryService;
    private final DocumentInputRetentionCleanupService documentInputRetentionCleanupService;
    private final NotificationStreamService notificationStreamService;

    public CoreMaintenanceJobHandler(
            AuthSessionCleanupService authSessionCleanupService,
            AuditRetentionCleanupService auditRetentionCleanupService,
            DocumentInputEventRecoveryService documentInputEventRecoveryService,
            DocumentWebhookAutoRetryService documentWebhookAutoRetryService,
            DocumentInputRetentionCleanupService documentInputRetentionCleanupService,
            NotificationStreamService notificationStreamService
    ) {
        this.authSessionCleanupService = authSessionCleanupService;
        this.auditRetentionCleanupService = auditRetentionCleanupService;
        this.documentInputEventRecoveryService = documentInputEventRecoveryService;
        this.documentWebhookAutoRetryService = documentWebhookAutoRetryService;
        this.documentInputRetentionCleanupService = documentInputRetentionCleanupService;
        this.notificationStreamService = notificationStreamService;
    }

    @XxlJob("authSessionCleanupJob")
    public void authSessionCleanupJob() throws Exception {
        XxlJobTraceSupport.execute("authSessionCleanupJob", () -> {
            authSessionCleanupService.cleanupExpiredSessions();
            return null;
        });
    }

    @XxlJob("auditRetentionCleanupJob")
    public void auditRetentionCleanupJob() throws Exception {
        XxlJobTraceSupport.execute("auditRetentionCleanupJob", () -> {
            auditRetentionCleanupService.cleanupByRetentionPolicy();
            return null;
        });
    }

    @XxlJob("documentInputEventRecoveryJob")
    public void documentInputEventRecoveryJob() throws Exception {
        XxlJobTraceSupport.execute("documentInputEventRecoveryJob", () -> {
            documentInputEventRecoveryService.recoverQueuedEvents("xxl-job");
            return null;
        });
    }

    @XxlJob("documentWebhookAutoRetryJob")
    public void documentWebhookAutoRetryJob() throws Exception {
        XxlJobTraceSupport.execute("documentWebhookAutoRetryJob", () -> {
            documentWebhookAutoRetryService.retryBySchedule();
            return null;
        });
    }

    @XxlJob("documentInputRetentionCleanupJob")
    public void documentInputRetentionCleanupJob() throws Exception {
        XxlJobTraceSupport.execute("documentInputRetentionCleanupJob", () -> {
            documentInputRetentionCleanupService.cleanupByRetentionPolicy();
            return null;
        });
    }

    /**
     * This job should be configured with broadcast routing so every executor instance heartbeats its own in-memory SSE
     * subscribers instead of only the instance selected by the default sharding strategy.
     */
    @XxlJob("notificationStreamHeartbeatJob")
    public void notificationStreamHeartbeatJob() throws Exception {
        XxlJobTraceSupport.execute("notificationStreamHeartbeatJob", () -> {
            notificationStreamService.heartbeat();
            return null;
        });
    }
}
