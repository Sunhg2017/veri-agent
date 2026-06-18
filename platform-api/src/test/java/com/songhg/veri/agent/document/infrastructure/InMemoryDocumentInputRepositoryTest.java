package com.songhg.veri.agent.document.infrastructure;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.application.query.DocumentCandidateQuery;
import com.songhg.veri.agent.document.application.query.DocumentImportQuery;
import com.songhg.veri.agent.document.application.query.DocumentSourceQuery;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDocumentInputRepositoryTest {

    private final InMemoryDocumentInputRepository repository = new InMemoryDocumentInputRepository();

    @Test
    void storesSourcesAndFindsByCodeCaseInsensitively() {
        UUID id = UUID.randomUUID();
        repository.saveSource(new DocumentSourceConfig(
                id,
                "Custom-Reqs",
                "Custom Reqs",
                DocumentSourceType.CUSTOM_API,
                DocumentSourceStatus.ENABLED,
                "https://example.test",
                "project-wp4",
                repository.defaultFieldMapping().id(),
                "wp4-webhook-default",
                "1.0",
                "default",
                null,
                Instant.now(),
                Instant.now()
        ));

        assertThat(repository.sourceByCode("custom-reqs")).isPresent();
        assertThat(repository.countSources(new DocumentSourceQuery(
                DocumentSourceType.CUSTOM_API,
                DocumentSourceStatus.ENABLED,
                PageQuery.of(0, 20)
        ))).isEqualTo(1);
    }

    @Test
    void storesImportRecordsWithPagedFiltering() {
        UUID importId = UUID.randomUUID();
        repository.saveImport(new DocumentImportRecord(
                importId,
                "project-wp4",
                null,
                null,
                DocumentSourceType.TEXT,
                "REQ-1",
                null,
                "Import",
                DocumentImportStatus.SUCCEEDED,
                1,
                1,
                "[]",
                null,
                "digest",
                null,
                Instant.now(),
                Instant.now()
        ));

        DocumentImportQuery query = new DocumentImportQuery(
                "project-wp4",
                null,
                DocumentSourceType.TEXT,
                DocumentImportStatus.SUCCEEDED,
                PageQuery.of(0, 10)
        );
        assertThat(repository.countImports(query)).isEqualTo(1);
        assertThat(repository.imports(query)).extracting(DocumentImportRecord::id).containsExactly(importId);
    }

    @Test
    void filtersCandidatesByStatusSourceRefAndKeyword() {
        UUID importId = UUID.randomUUID();
        repository.saveImport(new DocumentImportRecord(
                importId,
                "project-wp4",
                null,
                null,
                DocumentSourceType.MARKDOWN,
                "REQ-BATCH",
                null,
                "Import",
                DocumentImportStatus.SUCCEEDED,
                2,
                0,
                "[]",
                null,
                "digest",
                null,
                Instant.now(),
                Instant.now()
        ));
        Instant now = Instant.now();
        repository.saveCandidate(new DocumentRequirementCandidate(
                UUID.randomUUID(),
                importId,
                "project-wp4",
                "登录需求",
                "支持账号密码登录",
                "HIGH",
                "登录成功",
                "auth",
                DocumentCandidateStatus.CONFIRMED,
                "REQ-BATCH",
                "## 登录需求",
                "REQ-BATCH-0",
                0.9,
                null,
                null,
                null,
                "pm",
                now,
                1,
                now,
                now
        ));
        repository.saveCandidate(new DocumentRequirementCandidate(
                UUID.randomUUID(),
                importId,
                "project-wp4",
                "退出需求",
                "退出登录",
                "LOW",
                null,
                "auth",
                DocumentCandidateStatus.PENDING,
                "REQ-BATCH",
                "## 退出需求",
                "REQ-BATCH-1",
                0.8,
                null,
                null,
                null,
                null,
                null,
                0,
                now.plusSeconds(1),
                now.plusSeconds(1)
        ));

        DocumentCandidateQuery query = new DocumentCandidateQuery(
                importId,
                DocumentCandidateStatus.PENDING,
                "req-batch",
                "退出",
                PageQuery.of(0, 10)
        );

        assertThat(repository.countCandidates(query)).isEqualTo(1);
        assertThat(repository.candidates(query)).extracting(DocumentRequirementCandidate::title).containsExactly("退出需求");
    }

    @Test
    void findsOnlyRetryableFailedWebhookEventsWithinAttemptLimit() {
        Instant now = Instant.parse("2026-05-20T00:00:00Z");
        DocumentWebhookEvent retryable = webhookEvent("evt-retryable", WebhookEventStatus.FAILED, WebhookSignatureStatus.VALID, 1, "{}", now);
        DocumentWebhookEvent deadLetter = webhookEvent("evt-dead-letter", WebhookEventStatus.DEAD_LETTER, WebhookSignatureStatus.VALID, 2, "{}", now);
        DocumentWebhookEvent invalidSignature = webhookEvent("evt-invalid", WebhookEventStatus.FAILED, WebhookSignatureStatus.INVALID, 0, "{}", now);
        DocumentWebhookEvent attemptLimitReached = webhookEvent("evt-limit", WebhookEventStatus.FAILED, WebhookSignatureStatus.VALID, 3, "{}", now);
        DocumentWebhookEvent missingPayload = webhookEvent("evt-missing-payload", WebhookEventStatus.FAILED, WebhookSignatureStatus.VALID, 0, null, now);
        repository.saveWebhookEvent(deadLetter);
        repository.saveWebhookEvent(invalidSignature);
        repository.saveWebhookEvent(attemptLimitReached);
        repository.saveWebhookEvent(missingPayload);
        repository.saveWebhookEvent(retryable);

        assertThat(repository.retryableWebhookEvents(3, 10))
                .extracting(DocumentWebhookEvent::eventId)
                .containsExactly("evt-retryable");
    }

    private DocumentWebhookEvent webhookEvent(
            String eventId,
            WebhookEventStatus status,
            WebhookSignatureStatus signatureStatus,
            int retryCount,
            String rawPayload,
            Instant receivedAt
    ) {
        return new DocumentWebhookEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "custom-reqs",
                eventId,
                "idem-" + eventId,
                "requirement.created",
                "1.0",
                signatureStatus,
                status,
                "digest",
                rawPayload,
                null,
                retryCount,
                null,
                null,
                null,
                receivedAt,
                receivedAt
        );
    }
}
