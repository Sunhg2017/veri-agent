package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.reporting.application.view.ReportGenerationWorkerTickResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

@Service
public class ReportGenerationWorkerService implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationWorkerService.class);
    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;

    private final ReportService reportService;
    private final ReportingProperties properties;

    public ReportGenerationWorkerService(ReportService reportService, ReportingProperties properties) {
        this.reportService = reportService;
        this.properties = properties;
    }

    /**
     * Registers a bounded worker loop so persisted QUEUED reports are eventually processed without external callers.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::runBySchedule,
                Duration.ofMillis(scheduledFixedDelayMillis()),
                Duration.ofMillis(scheduledInitialDelayMillis())
        ));
    }

    public void runBySchedule() {
        if (!properties.generationWorkerEnabled()) {
            return;
        }
        String traceId = TraceContext.createTraceId();
        try (TraceContext.TraceScope ignored = TraceContext.open(traceId)) {
            runOnce();
        } catch (RuntimeException exception) {
            log.warn(
                    "WP10 report generation worker tick failed, traceId={}, error={}",
                    traceId,
                    sanitizedSummary(exception.getMessage())
            );
            log.debug("WP10 report generation worker failure details", exception);
        }
    }

    public long scheduledFixedDelayMillis() {
        return properties.effectiveGenerationWorkerIntervalMs();
    }

    public long scheduledInitialDelayMillis() {
        return properties.effectiveGenerationWorkerInitialDelayMs();
    }

    /**
     * Runs one bounded worker tick through stale recovery and queued report claims.
     *
     * <p>The method is synchronized to prevent overlapping manual and scheduled ticks inside one JVM. Database
     * conditional status updates in `ReportService` still protect the same queued row across multiple JVMs.</p>
     */
    public synchronized ReportGenerationWorkerTickResponse runOnce() {
        String traceId = TraceContext.getOrCreateTraceId();
        Instant tickedAt = Instant.now();
        String workerId = properties.effectiveGenerationWorkerId();
        int batchSize = properties.effectiveGenerationWorkerBatchSize();
        if (!properties.generationWorkerEnabled()) {
            return tick(false, workerId, batchSize, 0, 0, 0, 0, 0, traceId, tickedAt);
        }

        int recoveredStaleCount = reportService.recoverStaleGeneratingReports();
        int claimedReportCount = 0;
        int readyReportCount = 0;
        int failedReportCount = 0;
        int skippedCandidateCount = 0;
        for (ReportExecutionReport queued : reportService.queuedReports(batchSize)) {
            Optional<String> outcome = reportService.processQueuedReport(queued.id());
            if (outcome.isEmpty()) {
                skippedCandidateCount++;
                continue;
            }
            claimedReportCount++;
            if ("READY".equals(outcome.get())) {
                readyReportCount++;
            } else if ("FAILED".equals(outcome.get())) {
                failedReportCount++;
            }
        }
        return tick(
                true,
                workerId,
                batchSize,
                recoveredStaleCount,
                claimedReportCount,
                readyReportCount,
                failedReportCount,
                skippedCandidateCount,
                traceId,
                tickedAt
        );
    }

    private ReportGenerationWorkerTickResponse tick(
            boolean workerEnabled,
            String workerId,
            int batchSize,
            int recoveredStaleCount,
            int claimedReportCount,
            int readyReportCount,
            int failedReportCount,
            int skippedCandidateCount,
            String traceId,
            Instant tickedAt
    ) {
        boolean noop = recoveredStaleCount == 0
                && claimedReportCount == 0
                && skippedCandidateCount == 0;
        return new ReportGenerationWorkerTickResponse(
                workerEnabled,
                workerId,
                batchSize,
                recoveredStaleCount,
                claimedReportCount,
                readyReportCount,
                failedReportCount,
                skippedCandidateCount,
                noop,
                traceId,
                tickedAt
        );
    }

    private String sanitizedSummary(String value) {
        return SensitiveTextSanitizer.sanitizedErrorSummary(
                value,
                "Report generation worker failed",
                MAX_ERROR_SUMMARY_LENGTH
        );
    }
}
