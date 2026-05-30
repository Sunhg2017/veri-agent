package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDesignTaskReportServiceTest {

    @Test
    void allowsAggregateOnlyTaskReportRows() {
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety("""
                recordType,section,metric,label,value
                metadata,exportGovernance,fieldPolicy,,aggregateOnly
                metadata,auditPolicy,auditEventWritten,,true
                metadata,safetyScanPolicy,mode,,failClosed
                metadata,archivePolicy,retentionDays,,180
                metadata,task,promptKey,,wp5-test-design-v1
                summary,candidateQuality,metric,publishable,2
                """));
    }

    @Test
    void appendsBoundedArchivePolicyRowsWithoutFreeText() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "归档报告 token=secret-value",
                "SUCCEEDED",
                List.of(),
                List.of("SMOKE"),
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                null,
                "auditor",
                null,
                "digest",
                null,
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportExportGovernance.appendRows(csv, task, Instant.parse("2026-05-30T00:00:00Z"),
                properties(9999, true, false));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("auditPolicy,exportAction,,EXPORT")
                .contains("auditPolicy,resourceType,,TEST_DESIGN_TASK_REPORT")
                .contains("auditPolicy,scopeType,,PROJECT")
                .contains("auditPolicy,auditEventWritten,,true,,success")
                .contains("auditPolicy,auditDetailsExported,,false")
                .contains("safetyScanPolicy,mode,,failClosed,,success")
                .contains("safetyScanPolicy,sensitiveTextPatternScan,,true")
                .contains("safetyScanPolicy,rawPayloadMarkerScan,,true")
                .contains("safetyScanPolicy,requestResponsePreviewScan,,true")
                .contains("safetyScanPolicy,findingDetailsExported,,false")
                .contains("archivePolicy,retentionDays,,3650")
                .contains("archivePolicy,storagePolicy,,platformManaged")
                .contains("archivePolicy,approvalRequired,,false")
                .contains("archivePolicy,externalSharingAllowed,,true")
                .contains("archivePolicy,retentionPolicyTracked,,true,,success")
                .doesNotContain("auditLogId")
                .doesNotContain("afterJson")
                .doesNotContain("secret-value");
    }

    @Test
    void blocksRawPromptMarkersAndUnredactedSecrets() {
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,task,rawPrompt,,secret"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,publish,error,,Bearer abcdefgh123"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,model,error,,sk_live_12345678"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,model,error,,token=secret-value"));
    }

    private static TestDesignProperties properties(
            int reportArchiveRetentionDays,
            boolean externalSharingAllowed,
            boolean approvalRequired
    ) {
        return new TestDesignProperties(
                "test-design-token",
                true,
                "RULE_TEMPLATE",
                "wp5-test-design-v1",
                "1.0.0",
                false,
                20,
                3,
                5,
                5,
                5,
                240,
                240,
                240,
                100,
                true,
                true,
                100,
                600,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                0.86D,
                0.90D,
                reportArchiveRetentionDays,
                externalSharingAllowed,
                approvalRequired
        );
    }
}
