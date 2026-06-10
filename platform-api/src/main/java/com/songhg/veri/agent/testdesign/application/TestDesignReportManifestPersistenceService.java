package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchive;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchiveApproval;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchiveLineIntegrity;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchiveNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestDesignReportManifestPersistenceService {

    private static final String STORAGE_BACKEND_DATABASE = "DATABASE";
    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String APPROVAL_NOT_REQUIRED = "NOT_REQUIRED";
    private static final String APPROVAL_NOT_REQUESTED = "NOT_REQUESTED";
    private static final String APPROVAL_PENDING = TestDesignApprovalWorkflowSupport.STATUS_PENDING;
    private static final String APPROVAL_TYPE_ARCHIVE = "ARCHIVE";
    private static final String REASON_RETENTION_POLICY = "RETENTION_POLICY";

    private final TestDesignRepository repository;
    private final TestDesignProperties properties;
    private final TestDesignActorResolver actorResolver;

    public TestDesignReportManifestPersistenceService(
            TestDesignRepository repository,
            TestDesignProperties properties,
            TestDesignActorResolver actorResolver
    ) {
        this.repository = repository;
        this.properties = properties;
        this.actorResolver = actorResolver;
    }

    /**
     * Persists aggregate manifest facts, managed archive content and line-level integrity indexes.
     *
     * <p>The digest is calculated from the exact CSV returned after the final safety scan, but the raw report content,
     * storage key and row digest values are never returned by metadata APIs. Line indexes store only row numbers and
     * digests so operators can prove completeness without exposing row bodies or business identifiers.
     */
    public TestDesignReportManifest save(
            TestDesignTask task,
            TestDesignTaskReportManifestRows.ManifestSnapshot snapshot,
            String reportCsv,
            Instant generatedAt
    ) {
        String contentDigest = sha256(reportCsv);
        Instant now = Instant.now();
        TestDesignReportManifest manifest = repository.saveReportManifest(new TestDesignReportManifest(
                UUID.randomUUID(),
                task.id(),
                task.projectId(),
                snapshot.schemaVersion(),
                snapshot.fieldSetVersion(),
                snapshot.manifestMode(),
                snapshot.rowCountBeforeManifest(),
                snapshot.reportRowCount(),
                snapshot.aggregateOnly(),
                snapshot.detailRowsExported(),
                snapshot.manifestStatus(),
                contentDigest,
                generatedAt,
                now
        ));
        TestDesignReportArchive archive = saveArchive(task, manifest, reportCsv, contentDigest, generatedAt, now);
        createInitialArchiveApprovalIfRequired(archive, now);
        return manifest;
    }

    private TestDesignReportArchive saveArchive(
            TestDesignTask task,
            TestDesignReportManifest manifest,
            String reportCsv,
            String contentDigest,
            Instant generatedAt,
            Instant now
    ) {
        UUID archiveId = UUID.randomUUID();
        List<TestDesignReportArchiveLineIntegrity> lines = lineIntegrity(archiveId, reportCsv, now);
        String archiveApprovalStatus = properties.reportArchiveApprovalRequired() ? APPROVAL_PENDING : APPROVAL_NOT_REQUIRED;
        String archiveStatus = properties.reportArchiveApprovalRequired() ? STATUS_PENDING_APPROVAL : STATUS_ARCHIVED;
        byte[] contentBytes = reportCsv.getBytes(StandardCharsets.UTF_8);
        TestDesignReportArchive archive = repository.saveReportArchive(new TestDesignReportArchive(
                archiveId,
                manifest.id(),
                task.id(),
                task.projectId(),
                STORAGE_BACKEND_DATABASE,
                storageKey(task.id(), contentDigest),
                contentDigest,
                contentBytes.length,
                manifest.reportRowCount(),
                lines.size(),
                archiveStatus,
                archiveApprovalStatus,
                APPROVAL_NOT_REQUESTED,
                generatedAt.plus(properties.effectiveReportArchiveRetentionDays(), ChronoUnit.DAYS),
                contentBytes,
                actorResolver.currentActor(),
                now,
                now
        ));
        repository.saveReportArchiveLineIntegrity(rebindArchiveId(lines, archive.id()));
        return archive;
    }

    private void createInitialArchiveApprovalIfRequired(TestDesignReportArchive archive, Instant now) {
        if (!properties.reportArchiveApprovalRequired()) {
            return;
        }
        if (repository.latestReportArchiveApproval(archive.id(), APPROVAL_TYPE_ARCHIVE).isPresent()) {
            return;
        }
        UUID approvalId = UUID.randomUUID();
        String summary = "Archive aggregate WP5 task report after export safety scan.";
        String actor = actorResolver.currentActor();
        TestDesignReportArchiveApproval approval = new TestDesignReportArchiveApproval(
                approvalId,
                archive.id(),
                archive.taskId(),
                archive.projectId(),
                APPROVAL_TYPE_ARCHIVE,
                APPROVAL_PENDING,
                REASON_RETENTION_POLICY,
                null,
                TestDesignApprovalWorkflowSupport.workOrderKey(null, approvalId, "WP5-ARCH"),
                "WP5 report archive approval",
                null,
                "OPEN",
                summary,
                TestDesignApprovalWorkflowSupport.sha256OrNull(summary),
                null,
                null,
                actor,
                null,
                null,
                now,
                now
        );
        repository.saveReportArchiveApproval(approval);
        repository.saveReportArchiveNote(new TestDesignReportArchiveNote(
                UUID.randomUUID(),
                approval.id(),
                TestDesignApprovalWorkflowSupport.NOTE_TYPE_REQUEST,
                summary,
                actor,
                now
        ));
    }

    private static List<TestDesignReportArchiveLineIntegrity> lineIntegrity(UUID archiveId, String reportCsv, Instant now) {
        List<TestDesignReportArchiveLineIntegrity> lines = new ArrayList<>();
        List<String> nonBlankRows = reportCsv.lines().filter(StringUtils::hasText).toList();
        int startIndex = !nonBlankRows.isEmpty() && nonBlankRows.getFirst().startsWith("recordType,") ? 1 : 0;
        String previousDigest = null;
        for (int index = startIndex; index < nonBlankRows.size(); index++) {
            String row = nonBlankRows.get(index);
            String rowDigest = sha256(row);
            String chainDigest = sha256((previousDigest == null ? "" : previousDigest) + ":" + rowDigest);
            String[] metadata = row.split(",", 4);
            lines.add(new TestDesignReportArchiveLineIntegrity(
                    archiveId,
                    lines.size() + 1,
                    rowDigest,
                    previousDigest,
                    chainDigest,
                    safeMetadata(metadata, 0),
                    safeMetadata(metadata, 1),
                    safeMetadata(metadata, 2),
                    now
            ));
            previousDigest = rowDigest;
        }
        return lines;
    }

    private static List<TestDesignReportArchiveLineIntegrity> rebindArchiveId(
            List<TestDesignReportArchiveLineIntegrity> lines,
            UUID archiveId
    ) {
        return lines.stream()
                .map(line -> new TestDesignReportArchiveLineIntegrity(
                        archiveId,
                        line.rowNumber(),
                        line.rowDigest(),
                        line.previousRowDigest(),
                        line.chainDigest(),
                        line.recordType(),
                        line.section(),
                        line.metric(),
                        line.createdAt()
                ))
                .toList();
    }

    private static String safeMetadata(String[] metadata, int index) {
        if (metadata.length <= index || !StringUtils.hasText(metadata[index])) {
            return null;
        }
        String normalized = metadata[index].replaceAll("[^A-Za-z0-9_.:-]", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private static String storageKey(UUID taskId, String contentDigest) {
        return "wp5-report-archive/" + taskId + "/" + contentDigest + ".csv";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
