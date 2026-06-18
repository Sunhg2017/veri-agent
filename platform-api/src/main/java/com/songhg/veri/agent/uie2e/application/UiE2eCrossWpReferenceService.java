package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.asset.application.AssetCrossWpReportEvidenceService;
import com.songhg.veri.agent.asset.application.command.AssetReportEvidenceQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpReportEvidenceService;
import com.songhg.veri.agent.testdesign.application.command.TestDesignReportEvidenceQuery;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UiE2eCrossWpReferenceService {

    private final AssetCrossWpReportEvidenceService assetEvidenceService;
    private final TestDesignCrossWpReportEvidenceService testDesignEvidenceService;

    public UiE2eCrossWpReferenceService(
            AssetCrossWpReportEvidenceService assetEvidenceService,
            TestDesignCrossWpReportEvidenceService testDesignEvidenceService
    ) {
        this.assetEvidenceService = assetEvidenceService;
        this.testDesignEvidenceService = testDesignEvidenceService;
    }

    /**
     * Validates that every referenced WP3/WP5 object resolves inside the same project scope.
     * The call intentionally consumes only aggregate evidence adapters and discards the returned details afterwards.
     */
    public void validateSceneSourceSummary(String projectId, Map<String, Object> sourceSummary) {
        if (sourceSummary == null || sourceSummary.isEmpty()) {
            return;
        }
        try {
            List<UUID> pageRefs = uuidRefs(sourceSummary.get("pageRefs"));
            List<UUID> flowRefs = uuidRefs(sourceSummary.get("flowRefs"));
            List<UUID> testCaseRefs = uuidRefs(sourceSummary.get("testCaseRefs"));
            if (!pageRefs.isEmpty() || !flowRefs.isEmpty() || !testCaseRefs.isEmpty()) {
                assetEvidenceService.reportEvidence(new AssetReportEvidenceQuery(
                        projectId,
                        "wp7-scene-source-validation",
                        List.of(),
                        List.of(),
                        pageRefs,
                        flowRefs,
                        testCaseRefs
                ));
            }
            List<UUID> candidateRefs = uuidRefs(sourceSummary.get("candidateRefs"));
            List<UUID> taskRefs = uuidRefs(sourceSummary.get("taskRefs"));
            if (!candidateRefs.isEmpty() || !taskRefs.isEmpty()) {
                testDesignEvidenceService.reportEvidence(new TestDesignReportEvidenceQuery(
                        projectId,
                        "wp7-scene-source-validation",
                        taskRefs,
                        candidateRefs
                ));
            }
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.NOT_FOUND || exception.getErrorCode() == ErrorCode.VALIDATION_ERROR) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
    }

    private List<UUID> uuidRefs(Object rawValue) {
        if (!(rawValue instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        Set<UUID> refs = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            refs.add(UUID.fromString(value.toString().trim()));
        }
        return List.copyOf(refs);
    }
}
