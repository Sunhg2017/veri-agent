package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.TestDesignReportEvidenceQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportEvidenceResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDesignCrossWpReportEvidenceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_REPORT_REF_COUNT = 100;

    private final TestDesignRepository repository;
    private final TestDesignPlatformContextClient contextClient;
    private final ObjectMapper objectMapper;

    public TestDesignCrossWpReportEvidenceService(
            TestDesignRepository repository,
            TestDesignPlatformContextClient contextClient,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves WP5 aggregate evidence for WP10 without candidate bodies, prompt text or provider payloads.
     *
     * <p>Task refs return count-only generation and report-manifest signals. Candidate refs return lifecycle and link
     * digests only after WP5 validates that each referenced record belongs to the requested project scope.</p>
     */
    @Transactional(readOnly = true)
    public TestDesignReportEvidenceResponse reportEvidence(TestDesignReportEvidenceQuery query) {
        String projectId = contextClient.projectContext(query.projectId()).resourceId();
        return new TestDesignReportEvidenceResponse(
                projectId,
                boundedNullable(query.reportRef(), 128),
                boundedRefs(query.taskRefs()).stream()
                        .map(ref -> taskEvidence(ref, projectId))
                        .toList(),
                boundedRefs(query.candidateRefs()).stream()
                        .map(ref -> candidateEvidence(ref, projectId))
                        .toList(),
                reportRedactionPolicy()
        );
    }

    private TestDesignReportEvidenceResponse.TaskEvidence taskEvidence(UUID ref, String projectId) {
        TestDesignTask task = repository.task(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "WP5_TASK_NOT_FOUND"));
        assertSameProject(task.projectId(), projectId);
        List<TestDesignCandidate> candidates = repository.candidatesByTask(task.id());
        List<TestDesignReportManifest> manifests = repository.reportManifestsByTask(task.id());
        TestDesignReportManifest latestManifest = manifests.stream()
                .max(Comparator.comparing(TestDesignReportManifest::createdAt))
                .orElse(null);
        Map<String, Long> statusCounts = candidates.stream()
                .collect(Collectors.groupingBy(
                        candidate -> StringUtils.hasText(candidate.status()) ? candidate.status() : "UNKNOWN",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        return new TestDesignReportEvidenceResponse.TaskEvidence(
                task.id(),
                task.status(),
                csvCount(task.requirementIds()),
                csvCount(task.coverageTypes()),
                task.totalRequirements(),
                task.generatedCount(),
                task.confirmedCount(),
                task.publishedCount(),
                task.modelInvocationId() != null,
                boundedNullable(task.requestDigest(), 128),
                boundedNullable(task.inputDigest(), 128),
                readMap(task.contextSummaryJson()).keySet().size(),
                candidates.size(),
                statusCounts,
                manifests.size(),
                manifests.stream().filter(TestDesignReportManifest::aggregateOnly).count(),
                latestManifest == null ? null : latestManifest.manifestStatus(),
                latestManifest == null ? null : latestManifest.contentDigest(),
                latestManifest == null ? null : latestManifest.schemaVersion(),
                task.updatedAt()
        );
    }

    private TestDesignReportEvidenceResponse.CandidateEvidence candidateEvidence(UUID ref, String projectId) {
        TestDesignCandidate candidate = repository.candidate(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "WP5_CANDIDATE_NOT_FOUND"));
        assertSameProject(candidate.projectId(), projectId);
        return new TestDesignReportEvidenceResponse.CandidateEvidence(
                candidate.id(),
                candidate.taskId(),
                candidate.requirementId(),
                candidate.apiId(),
                candidate.assetCaseId(),
                candidate.status(),
                candidate.coverageType(),
                candidate.priority(),
                candidate.confidence(),
                candidate.modelInvocationId() != null,
                candidate.confirmedAt() != null,
                candidate.version(),
                candidate.updatedAt()
        );
    }

    private List<UUID> boundedRefs(List<UUID> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
        refs.stream().filter(ref -> ref != null).forEach(deduplicated::add);
        List<UUID> normalized = deduplicated.stream().toList();
        if (normalized.size() > MAX_REPORT_REF_COUNT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "WP5_REPORT_EVIDENCE_REF_LIMIT_EXCEEDED");
        }
        return normalized;
    }

    private void assertSameProject(String actualProjectId, String expectedProjectId) {
        if (!expectedProjectId.equals(actualProjectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "WP5_REPORT_EVIDENCE_NOT_FOUND");
        }
    }

    private int csvCount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return (int) List.of(value.split(",")).stream()
                .filter(StringUtils::hasText)
                .count();
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("unreadable", true);
        }
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private Map<String, Object> reportRedactionPolicy() {
        return Map.of(
                "aggregateOnly", true,
                "candidateBodyReturned", false,
                "candidateIdentifierListReturned", false,
                "promptReturned", false,
                "modelPayloadReturned", false,
                "auditIdentifierListReturned", false,
                "crossWpTableAccessAllowed", false
        );
    }
}
