package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.documentinput.application.port.DocumentInputRepository;
import com.songhg.veri.agent.documentinput.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentImportStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentParseFeedbackSample;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



class DocumentParseFeedbackCaptureServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentInputRepository repository = mock(DocumentInputRepository.class);
    private final DocumentInputPlatformContextClient contextClient = mock(DocumentInputPlatformContextClient.class);
    private final DocumentParseFeedbackCaptureService service = new DocumentParseFeedbackCaptureService(
            repository,
            contextClient,
            objectMapper
    );

    @Test
    void capturesSanitizedFeedbackSampleForManualEditsOnModelCandidates() throws Exception {
        UUID importId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        when(repository.importRecord(importId)).thenReturn(Optional.of(importRecord(importId)));
        DocumentRequirementCandidate before = candidate(
                candidateId,
                importId,
                "MODEL",
                invocationId,
                "Login",
                "original acceptance"
        );
        DocumentRequirementCandidate after = candidate(
                candidateId,
                importId,
                "MODEL",
                invocationId,
                "Login v2",
                """
                        password=PlainSecret123 user@example.com https://example.test/path
                        550e8400-e29b-41d4-a716-446655440000 13800138000 123456789012
                        """
        );

        service.captureManualEdit(before, after, "user-001");

        ArgumentCaptor<DocumentParseFeedbackSample> sampleCaptor =
                ArgumentCaptor.forClass(DocumentParseFeedbackSample.class);
        verify(repository).saveParseFeedbackSample(sampleCaptor.capture());
        DocumentParseFeedbackSample sample = sampleCaptor.getValue();
        assertThat(sample.candidateId()).isEqualTo(candidateId);
        assertThat(sample.importId()).isEqualTo(importId);
        assertThat(sample.projectId()).isEqualTo("project-feedback");
        assertThat(sample.sourceType()).isEqualTo("MARKDOWN");
        assertThat(sample.inputDigest()).isEqualTo("raw-digest");
        assertThat(sample.parseSource()).isEqualTo("MODEL");
        assertThat(sample.modelInvocationId()).isEqualTo(invocationId);
        assertThat(sample.correctionType()).isEqualTo("MANUAL_EDIT");
        assertThat(sample.changedFields()).isEqualTo("title,acceptanceCriteria");
        assertThat(sample.curationStatus()).isEqualTo("READY_FOR_CORPUS");
        assertThat(sample.createdBy()).isEqualTo("user-001");

        JsonNode afterSnapshot = objectMapper.readTree(sample.afterSnapshotJson());
        assertThat(afterSnapshot.path("acceptanceCriteria").asText())
                .contains("[SECRET]", "[EMAIL]", "[URL]", "[UUID]", "[PHONE]", "[NUMBER]")
                .doesNotContain("PlainSecret123")
                .doesNotContain("user@example.com")
                .doesNotContain("https://example.test/path");
        assertThat(afterSnapshot.path("sourceRefDigest").asText()).hasSize(64);
        assertThat(afterSnapshot.path("externalRequirementIdDigest").asText()).hasSize(64);

        verify(contextClient).writeAuditEvent(
                eq("CAPTURE_PARSE_FEEDBACK"),
                eq("DOCUMENT_PARSE_FEEDBACK_SAMPLE"),
                eq(sample.id().toString()),
                eq("project-feedback"),
                eq("SUCCEEDED"),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any()
        );
    }

    @Test
    void skipsNonModelCandidates() {
        UUID importId = UUID.randomUUID();
        DocumentRequirementCandidate before = candidate(
                UUID.randomUUID(),
                importId,
                "RULE",
                null,
                "Login",
                "original"
        );
        DocumentRequirementCandidate after = candidate(
                before.id(),
                importId,
                "RULE",
                null,
                "Login v2",
                "updated"
        );

        service.captureManualEdit(before, after, "user-001");

        verify(repository, never()).importRecord(any());
        verify(repository, never()).saveParseFeedbackSample(any());
        verify(contextClient, never()).writeAuditEvent(any(), any(), any(), any(), any(), any());
    }

    private DocumentImportRecord importRecord(UUID importId) {
        Instant now = Instant.parse("2026-05-24T00:00:00Z");
        return new DocumentImportRecord(
                importId,
                "project-feedback",
                null,
                "source-feedback",
                DocumentSourceType.MARKDOWN,
                "source-ref",
                "https://example.test/source",
                "Feedback import",
                DocumentImportStatus.SUCCEEDED,
                1,
                0,
                "[]",
                null,
                "raw-digest",
                now,
                now
        );
    }

    private DocumentRequirementCandidate candidate(
            UUID candidateId,
            UUID importId,
            String parseSource,
            UUID invocationId,
            String title,
            String acceptanceCriteria
    ) {
        Instant now = Instant.parse("2026-05-24T00:00:00Z");
        return new DocumentRequirementCandidate(
                candidateId,
                importId,
                "project-feedback",
                title,
                "description",
                "HIGH",
                acceptanceCriteria,
                "tag-a",
                DocumentCandidateStatus.PENDING,
                "source-ref",
                "fragment",
                "source-ref#1",
                0.86,
                parseSource,
                invocationId,
                invocationId == null ? null : "local-echo-primary",
                invocationId == null ? null : "test-local-model",
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now
        );
    }
}
