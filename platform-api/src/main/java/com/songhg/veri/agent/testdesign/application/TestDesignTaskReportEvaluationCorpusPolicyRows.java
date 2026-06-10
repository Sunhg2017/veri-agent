package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationCorpusPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;

final class TestDesignTaskReportEvaluationCorpusPolicyRows {

    private TestDesignTaskReportEvaluationCorpusPolicyRows() {
    }

    /**
     * Appends evaluation-corpus operations boundaries without exporting sample rows or prompt text.
     *
     * <p>The report only proves that WP5 has a project-isolated sample-maintenance workflow, golden-set baseline and
     * calibration run history. Sample rows, prompt text and candidate bodies remain outside report exports.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        TestDesignEvaluationCorpusPolicyResponse policy = task.evaluationCorpusPolicy() == null
                ? TestDesignEvaluationCorpusPolicy.response()
                : task.evaluationCorpusPolicy();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", policy.policyVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "corpusMode", policy.corpusMode(), "success");
        appendMetadataRow(csv, task, generatedAt, "qualityGateMode", policy.qualityGateMode(), "warning");
        appendMetadataRow(csv, task, generatedAt, "thresholdSource", policy.thresholdSource(), null);
        appendMetadataRow(csv, task, generatedAt, "projectScopeRequired",
                policy.projectScopeRequired(), policy.projectScopeRequired() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "goldenSetBaselineRequired",
                policy.goldenSetBaselineRequired(), policy.goldenSetBaselineRequired() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "qualityEvalScriptReady",
                policy.qualityEvalScriptReady(), policy.qualityEvalScriptReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "qualityGateIntegrated",
                policy.qualityGateIntegrated(), policy.qualityGateIntegrated() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "readinessDistributionTracked",
                policy.readinessDistributionTracked(), policy.readinessDistributionTracked() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "promptVersionTracked",
                policy.promptVersionTracked(), policy.promptVersionTracked() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "evaluationCorpusProjectIsolated",
                policy.evaluationCorpusProjectIsolated(),
                policy.evaluationCorpusProjectIsolated() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "sampleMaintenanceReady",
                policy.sampleMaintenanceReady(), policy.sampleMaintenanceReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "longTermCalibrationReady",
                policy.longTermCalibrationReady(), policy.longTermCalibrationReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "operationsConsoleReady",
                policy.operationsConsoleReady(), policy.operationsConsoleReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "corpusRowExported", policy.corpusRowExported(), null);
        appendMetadataRow(csv, task, generatedAt, "candidateBodyExported", policy.candidateBodyExported(), null);
        appendMetadataRow(csv, task, generatedAt, "reviewCommentExported", policy.reviewCommentExported(), null);
        appendMetadataRow(csv, task, generatedAt, "promptBodyExported", policy.promptBodyExported(), null);
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", policy.aggregateOnly(), "success");
    }

    private static void appendMetadataRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String metric,
            Object value,
            String tone
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "evaluationCorpusPolicy", metric, null, value, null, tone, "fullTask", null);
    }
}
