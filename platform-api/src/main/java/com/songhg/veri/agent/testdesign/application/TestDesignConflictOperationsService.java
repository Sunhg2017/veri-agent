package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.query.TestDesignConflictOperationQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignConflictOperationItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignConflictOperationsResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignConflictOperationsSummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Provides the read side for WP5 asset conflict operations without changing publish write semantics.
 */
@Service
public class TestDesignConflictOperationsService {

    private static final TestDesignConflictOperationSummary EMPTY_SUMMARY =
            new TestDesignConflictOperationSummary(0, 0, 0, 0, null);

    private final TestDesignRepository repository;
    private final TestDesignResponseMapper responseMapper;

    public TestDesignConflictOperationsService(
            TestDesignRepository repository,
            TestDesignResponseMapper responseMapper
    ) {
        this.repository = repository;
        this.responseMapper = responseMapper;
    }

    /**
     * Returns only formal publish conflict rows for a single project.
     *
     * <p>Dry-run conflict previews are intentionally excluded: they are already visible in the publish panel and do not
     * represent durable operational work. A conflict is considered resolved once the candidate is published or a later
     * formal publish record succeeds for the same candidate.
     */
    public TestDesignConflictOperationsResponse conflictOperations(TestDesignConflictOperationQuery query) {
        if (query == null || !StringUtils.hasText(query.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产冲突运营台必须指定项目 ID");
        }
        validateResolutionStatus(query.resolutionStatus());
        List<TestDesignConflictOperationRecord> records = repository.conflictOperations(query);
        long total = repository.countConflictOperations(query);
        TestDesignConflictOperationSummary summary = repository.conflictOperationSummary(query.withoutResolutionStatus());
        if (summary == null) {
            summary = EMPTY_SUMMARY;
        }
        return new TestDesignConflictOperationsResponse(
                records.stream().map(this::toItemResponse).toList(),
                query.index(),
                query.size(),
                total,
                new TestDesignConflictOperationsSummaryResponse(
                        summary.totalCount(),
                        summary.openCount(),
                        summary.resolvedCount(),
                        summary.duplicateReviewCount(),
                        summary.latestConflictAt()
                )
        );
    }

    private TestDesignConflictOperationItemResponse toItemResponse(TestDesignConflictOperationRecord record) {
        TestDesignPublishRecord publishRecord = new TestDesignPublishRecord(
                record.publishRecordId(),
                record.taskId(),
                record.candidateId(),
                record.projectId(),
                record.requirementId(),
                record.assetCaseId(),
                record.dryRun(),
                record.action(),
                record.result(),
                record.errorMessage(),
                record.publishedBy(),
                record.recordCreatedAt()
        );
        TestDesignCandidate candidate = new TestDesignCandidate(
                record.candidateId(),
                record.taskId(),
                record.projectId(),
                record.requirementId(),
                null,
                record.candidateTitle(),
                null,
                null,
                null,
                record.candidateStatus(),
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                record.candidateAssetCaseId(),
                null,
                null,
                null,
                null,
                null,
                null,
                record.candidateVersion(),
                null,
                null
        );
        TestDesignPublishRecordResponse publishResponse = responseMapper.toPublishRecordResponse(publishRecord, candidate);
        return new TestDesignConflictOperationItemResponse(
                record.taskId(),
                record.taskTitle(),
                record.taskStatus(),
                record.candidateId(),
                record.candidateTitle(),
                record.candidateStatus(),
                record.candidateVersion(),
                record.projectId(),
                record.requirementId(),
                record.assetCaseId(),
                publishResponse,
                record.resolved(),
                isResolvable(record),
                record.recordCreatedAt()
        );
    }

    private static boolean isResolvable(TestDesignConflictOperationRecord record) {
        return !record.resolved()
                && record.assetCaseId() != null
                && (TestDesignCandidateStatus.CONFIRMED.name().equals(record.candidateStatus())
                || TestDesignCandidateStatus.FAILED.name().equals(record.candidateStatus()));
    }

    private static void validateResolutionStatus(String resolutionStatus) {
        if (!StringUtils.hasText(resolutionStatus)) {
            return;
        }
        if (!List.of("OPEN", "RESOLVED", "ALL").contains(resolutionStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的冲突处理状态: " + resolutionStatus);
        }
    }
}
