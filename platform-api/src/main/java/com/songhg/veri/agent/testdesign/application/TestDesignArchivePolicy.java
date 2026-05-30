package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignArchivePolicyResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 task-report archive governance boundary.
 *
 * <p>The current slice exposes bounded retention, approval and sharing policy as an aggregate snapshot only. It does
 * not persist report archives, run an archive approval workflow or export storage paths, notes, ticket URLs or other
 * free-form archive metadata that can carry tenant identifiers or secrets.
 */
public final class TestDesignArchivePolicy {

    public static final String POLICY_VERSION = "wp5-archive-policy-v1";
    public static final String STORAGE_POLICY = "platformManaged";

    private TestDesignArchivePolicy() {
    }

    public static TestDesignArchivePolicyResponse response(TestDesignProperties properties) {
        return new TestDesignArchivePolicyResponse(
                POLICY_VERSION,
                properties.effectiveReportArchiveRetentionDays(),
                STORAGE_POLICY,
                properties.reportArchiveApprovalRequired(),
                false,
                properties.reportArchiveExternalSharingAllowed(),
                true,
                false,
                false,
                false,
                false,
                false,
                true
        );
    }

    public static Map<String, Object> snapshot(TestDesignProperties properties) {
        TestDesignArchivePolicyResponse response = response(properties);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("retentionDays", response.retentionDays());
        snapshot.put("storagePolicy", response.storagePolicy());
        snapshot.put("approvalRequired", response.approvalRequired());
        snapshot.put("archiveApprovalWorkflowReady", response.archiveApprovalWorkflowReady());
        snapshot.put("externalSharingAllowed", response.externalSharingAllowed());
        snapshot.put("retentionPolicyTracked", response.retentionPolicyTracked());
        snapshot.put("archiveStorageReady", response.archiveStorageReady());
        snapshot.put("archivePathExported", response.archivePathExported());
        snapshot.put("archiveNotesExported", response.archiveNotesExported());
        snapshot.put("approvalNotesExported", response.approvalNotesExported());
        snapshot.put("ticketUrlExported", response.ticketUrlExported());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
