package com.songhg.veri.agent.documentinput.infrastructure.mapper;

import com.songhg.veri.agent.documentinput.application.query.DocumentCandidateQuery;
import com.songhg.veri.agent.documentinput.application.query.DocumentImportQuery;
import com.songhg.veri.agent.documentinput.application.query.DocumentParseFeedbackQuery;
import com.songhg.veri.agent.documentinput.application.query.DocumentSourceQuery;
import com.songhg.veri.agent.documentinput.application.query.DocumentWebhookEventQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentParseFeedbackSample;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentWebhookEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentInputMapper {

    List<DocumentSourceConfig> sources(@Param("query") DocumentSourceQuery query);

    long countSources(@Param("query") DocumentSourceQuery query);

    DocumentSourceConfig source(@Param("id") UUID id);

    DocumentSourceConfig sourceByCode(@Param("sourceCode") String sourceCode);

    void upsertSource(DocumentSourceConfig source);

    DocumentFieldMapping defaultFieldMapping();

    DocumentFieldMapping fieldMapping(@Param("id") UUID id);

    void upsertFieldMapping(DocumentFieldMapping mapping);

    List<DocumentImportRecord> imports(@Param("query") DocumentImportQuery query);

    long countImports(@Param("query") DocumentImportQuery query);

    DocumentImportRecord importRecord(@Param("id") UUID id);

    void upsertImport(DocumentImportRecord record);

    List<DocumentRequirementCandidate> candidates(
            @Param("importId") UUID importId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    long countCandidates(@Param("importId") UUID importId);

    List<DocumentRequirementCandidate> candidatesByQuery(@Param("query") DocumentCandidateQuery query);

    long countCandidatesByQuery(@Param("query") DocumentCandidateQuery query);

    DocumentRequirementCandidate candidate(@Param("id") UUID id);

    DocumentRequirementCandidate candidateByExternalId(
            @Param("projectId") String projectId,
            @Param("externalRequirementId") String externalRequirementId
    );

    void upsertCandidate(DocumentRequirementCandidate candidate);

    List<DocumentParseFeedbackSample> parseFeedbackSamples(@Param("query") DocumentParseFeedbackQuery query);

    long countParseFeedbackSamples(@Param("query") DocumentParseFeedbackQuery query);

    void insertParseFeedbackSample(DocumentParseFeedbackSample sample);

    List<DocumentWebhookEvent> webhookEvents(@Param("query") DocumentWebhookEventQuery query);

    long countWebhookEvents(@Param("query") DocumentWebhookEventQuery query);

    DocumentWebhookEvent webhookEvent(@Param("id") UUID id);

    DocumentWebhookEvent webhookEventByIdentity(
            @Param("sourceCode") String sourceCode,
            @Param("eventId") String eventId,
            @Param("idempotencyKey") String idempotencyKey
    );

    List<DocumentWebhookEvent> retryableWebhookEvents(
            @Param("maxAttempts") int maxAttempts,
            @Param("limit") int limit
    );

    void upsertWebhookEvent(DocumentWebhookEvent event);

    int archiveImportsBefore(@Param("before") Instant before);

    int archiveCandidatesByImportCreatedBefore(@Param("before") Instant before);

    int archiveWebhookEventsBefore(@Param("before") Instant before);

    int softDeleteCandidatesByImportCreatedBefore(@Param("before") Instant before);

    int softDeleteImportsBefore(@Param("before") Instant before);

    int softDeleteWebhookEventsBefore(@Param("before") Instant before);
}
