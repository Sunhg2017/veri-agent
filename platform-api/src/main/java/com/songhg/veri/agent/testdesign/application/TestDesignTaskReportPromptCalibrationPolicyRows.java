package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;

final class TestDesignTaskReportPromptCalibrationPolicyRows {

    private static final String POLICY_VERSION = "wp5-prompt-calibration-policy-v1";
    private static final String SAMPLE_SOURCE = "HUMAN_FEEDBACK_AGGREGATE";
    private static final String CALIBRATION_STATUS = "AGGREGATE_SIGNALS_ONLY";

    private TestDesignTaskReportPromptCalibrationPolicyRows() {
    }

    /**
     * Exposes prompt calibration readiness without copying review comments, sample rows or candidate identifiers.
     *
     * <p>WP5 already aggregates human corrections, rejections and ignored candidates as tuning signals. These policy
     * rows make the current operating boundary explicit: reports can prove that aggregate signals exist, but the real
     * sample-set maintenance workflow and long-running calibration baseline are still pending dedicated storage and UI.
     */
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            long promptTuningSignalCount,
            long promptTuningCandidateCount,
            long promptTuningCommentCount
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "policyVersion", null,
                POLICY_VERSION, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "sampleSource", null,
                SAMPLE_SOURCE, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "calibrationStatus", null,
                CALIBRATION_STATUS, null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "promptCalibrationPolicy", "metric", "feedbackSignalsTracked",
                promptTuningSignalCount, null, promptTuningSignalCount > 0L ? "info" : "neutral", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "promptCalibrationPolicy", "metric", "sampleCandidatesTracked",
                promptTuningCandidateCount, null, promptTuningCandidateCount > 0L ? "info" : "neutral", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "promptCalibrationPolicy", "metric", "sampleExplanationCount",
                promptTuningCommentCount, null, promptTuningCommentCount > 0L ? "info" : "neutral", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "sampleSetMaintenanceWorkflowReady", null,
                false, null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "longTermCalibrationBaselineReady", null,
                false, null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "sampleDetailRowsExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "candidateBodyExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "reviewTextExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "promptCalibrationPolicy", "aggregateOnly", null,
                true, null, "success", "fullTask", null);
    }
}
