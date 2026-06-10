package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignReportArchiveApprovalCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignReportArchiveApprovalCommand;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportArchiveApprovalResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportArchive;
import com.songhg.veri.agent.testdesign.infrastructure.InMemoryTestDesignRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestDesignReportArchiveServiceTest {

    @Test
    void requestsAndApprovesExternalShareApprovalWhenSharingIsEnabled() {
        InMemoryTestDesignRepository repository = new InMemoryTestDesignRepository();
        UUID archiveId = UUID.fromString("00000000-0000-4000-8000-000000000501");
        UUID taskId = UUID.fromString("00000000-0000-4000-8000-000000000502");
        repository.saveReportArchive(new TestDesignReportArchive(
                archiveId,
                UUID.fromString("00000000-0000-4000-8000-000000000503"),
                taskId,
                "project-wp5",
                "DATABASE",
                "wp5-report-archive/" + taskId + "/" + "a".repeat(64) + ".csv",
                "a".repeat(64),
                256,
                3,
                3,
                "ARCHIVED",
                TestDesignApprovalWorkflowSupport.STATUS_APPROVED,
                "NOT_REQUESTED",
                Instant.parse("2026-12-31T00:00:00Z"),
                "recordType,section,metric\nmetadata,a,b\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "system",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        ));
        TestDesignActorResolver actorResolver = mock(TestDesignActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("archive-operator");
        TestDesignPlatformContextClient contextClient = mock(TestDesignPlatformContextClient.class);
        TestDesignReportArchiveService service = new TestDesignReportArchiveService(
                repository,
                properties(true, true),
                actorResolver,
                contextClient
        );

        TestDesignReportArchiveApprovalResponse requested = service.requestExternalShareApproval(
                archiveId,
                new RequestTestDesignReportArchiveApprovalCommand(
                        "CUSTOMER_REQUEST",
                        "Customer asked for controlled archive sharing.",
                        "WP5-SHARE-1",
                        "Controlled external share",
                        "https://ticket.example/wp5/share-1",
                        "External share request note"
                )
        );

        assertThat(requested.approvalType()).isEqualTo("EXTERNAL_SHARE");
        assertThat(requested.status()).isEqualTo(TestDesignApprovalWorkflowSupport.STATUS_PENDING);
        assertThat(requested.noteCount()).isEqualTo(1);
        assertThat(repository.reportArchive(archiveId).orElseThrow().externalApprovalStatus())
                .isEqualTo(TestDesignApprovalWorkflowSupport.STATUS_PENDING);

        TestDesignReportArchiveApprovalResponse approved = service.approveApproval(
                requested.id(),
                new ReviewTestDesignReportArchiveApprovalCommand(
                        "CUSTOMER_REQUEST",
                        "External share approved after archive finalization.",
                        "APPROVED"
                )
        );

        assertThat(approved.status()).isEqualTo(TestDesignApprovalWorkflowSupport.STATUS_APPROVED);
        assertThat(approved.approvalReasonCodeCaptured()).isTrue();
        assertThat(approved.noteCount()).isEqualTo(2);
        TestDesignReportArchive archive = repository.reportArchive(archiveId).orElseThrow();
        assertThat(archive.status()).isEqualTo("ARCHIVED");
        assertThat(archive.archiveApprovalStatus()).isEqualTo(TestDesignApprovalWorkflowSupport.STATUS_APPROVED);
        assertThat(archive.externalApprovalStatus()).isEqualTo(TestDesignApprovalWorkflowSupport.STATUS_APPROVED);
        verify(contextClient).writeAuditEvent(
                eq("REPORT_ARCHIVE_APPROVAL_REQUEST"),
                eq("TEST_DESIGN_REPORT_ARCHIVE_APPROVAL"),
                eq(requested.id().toString()),
                eq("project-wp5"),
                eq("SUCCEEDED"),
                any()
        );
    }

    private static TestDesignProperties properties(boolean externalSharingAllowed, boolean approvalRequired) {
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
                true,
                100,
                600,
                120,
                true,
                100,
                600,
                120,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                false,
                0.86D,
                0.90D,
                true,
                50,
                180,
                externalSharingAllowed,
                approvalRequired
        );
    }
}
