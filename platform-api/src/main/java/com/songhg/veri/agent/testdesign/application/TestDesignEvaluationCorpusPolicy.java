package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationCorpusPolicyResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 evaluation-corpus operating boundary.
 *
 * <p>The current slice has a project-scoped sample maintenance console, reproducible golden-set baseline metadata,
 * long-term calibration run history and an opt-in AI evaluation script. This aggregate snapshot keeps diagnostics,
 * model payloads and reports aligned on that boundary while avoiding corpus rows, candidate bodies, review comments or
 * prompt text in exported artifacts.
 */
public final class TestDesignEvaluationCorpusPolicy {

    public static final String POLICY_VERSION = "wp5-evaluation-corpus-policy-v1";
    public static final String CORPUS_MODE = "GOLDEN_SET_BASELINE";
    public static final String QUALITY_GATE_MODE = "MANUAL_OPT_IN_AI_EVAL";
    public static final String THRESHOLD_SOURCE = "DEPLOY_CONFIG";

    private TestDesignEvaluationCorpusPolicy() {
    }

    public static TestDesignEvaluationCorpusPolicyResponse response() {
        return new TestDesignEvaluationCorpusPolicyResponse(
                POLICY_VERSION,
                CORPUS_MODE,
                QUALITY_GATE_MODE,
                THRESHOLD_SOURCE,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                true
        );
    }

    public static Map<String, Object> snapshot() {
        TestDesignEvaluationCorpusPolicyResponse response = response();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("corpusMode", response.corpusMode());
        snapshot.put("qualityGateMode", response.qualityGateMode());
        snapshot.put("thresholdSource", response.thresholdSource());
        snapshot.put("projectScopeRequired", response.projectScopeRequired());
        snapshot.put("goldenSetBaselineRequired", response.goldenSetBaselineRequired());
        snapshot.put("qualityEvalScriptReady", response.qualityEvalScriptReady());
        snapshot.put("qualityGateIntegrated", response.qualityGateIntegrated());
        snapshot.put("readinessDistributionTracked", response.readinessDistributionTracked());
        snapshot.put("promptVersionTracked", response.promptVersionTracked());
        snapshot.put("evaluationCorpusProjectIsolated", response.evaluationCorpusProjectIsolated());
        snapshot.put("sampleMaintenanceReady", response.sampleMaintenanceReady());
        snapshot.put("longTermCalibrationReady", response.longTermCalibrationReady());
        snapshot.put("operationsConsoleReady", response.operationsConsoleReady());
        snapshot.put("corpusRowExported", response.corpusRowExported());
        snapshot.put("candidateBodyExported", response.candidateBodyExported());
        snapshot.put("reviewCommentExported", response.reviewCommentExported());
        snapshot.put("promptBodyExported", response.promptBodyExported());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
