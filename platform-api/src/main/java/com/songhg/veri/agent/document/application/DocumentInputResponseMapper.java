package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.document.application.view.DocumentCandidateResponse;
import com.songhg.veri.agent.document.application.view.DocumentImportResponse;
import com.songhg.veri.agent.document.application.view.DocumentParseFeedbackSampleResponse;
import com.songhg.veri.agent.document.application.view.DocumentWebhookEventResponse;
import com.songhg.veri.agent.document.application.view.ParsedRequirementResponse;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentParseFeedbackSample;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import com.songhg.veri.agent.document.domain.DocumentWebhookEvent;
import com.songhg.veri.agent.document.domain.ParsedRequirementDraft;
import java.util.List;
import java.util.UUID;
import org.springframework.util.StringUtils;


final class DocumentInputResponseMapper {

    private final DocumentInputRepository repository;
    private final ObjectMapper objectMapper;

    DocumentInputResponseMapper(DocumentInputRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    DocumentImportResponse toImportResponse(DocumentImportRecord record, List<ParsedRequirementDraft> requirements) {
        List<DocumentRequirementCandidate> candidates = repository.candidates(record.id(), 0, 10000);
        return new DocumentImportResponse(
                record.id(),
                record.projectId(),
                record.sourceId(),
                record.sourceCode(),
                record.sourceType(),
                record.sourceRef(),
                record.sourceUrl(),
                record.title(),
                record.status(),
                record.totalParsed(),
                record.totalCreated(),
                requirementIds(record.createdRequirementIds()),
                requirements.stream().map(DocumentInputResponseMapper::toRequirementResponse).toList(),
                countCandidates(candidates, DocumentCandidateStatus.PENDING),
                countCandidates(candidates, DocumentCandidateStatus.CONFIRMED),
                countCandidates(candidates, DocumentCandidateStatus.PUBLISHED),
                countCandidates(candidates, DocumentCandidateStatus.PUBLISH_FAILED),
                record.errorMessage(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    DocumentCandidateResponse toCandidateResponse(DocumentRequirementCandidate candidate) {
        return new DocumentCandidateResponse(
                candidate.id(),
                candidate.importId(),
                candidate.projectId(),
                candidate.title(),
                candidate.description(),
                candidate.priority(),
                candidate.acceptanceCriteria(),
                candidate.tags(),
                candidate.status(),
                candidate.sourceRef(),
                candidate.sourceFragment(),
                candidate.externalRequirementId(),
                candidate.confidence(),
                candidate.parseSource(),
                candidate.modelInvocationId(),
                candidate.modelProviderName(),
                candidate.modelName(),
                candidate.assetRequirementId(),
                candidate.errorMessage(),
                candidate.ignoredReason(),
                candidate.confirmedBy(),
                candidate.confirmedAt(),
                candidate.version(),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }

    DocumentParseFeedbackSampleResponse toParseFeedbackSampleResponse(DocumentParseFeedbackSample sample) {
        return new DocumentParseFeedbackSampleResponse(
                sample.id(),
                sample.candidateId(),
                sample.importId(),
                sample.projectId(),
                sample.sourceType(),
                sample.inputDigest(),
                sample.sourceRefDigest(),
                sample.sourceFragmentDigest(),
                sample.parseSource(),
                sample.modelInvocationId(),
                sample.modelProviderName(),
                sample.modelName(),
                sample.correctionType(),
                sample.changedFields(),
                feedbackSnapshotNode(sample.beforeSnapshotJson()),
                feedbackSnapshotNode(sample.afterSnapshotJson()),
                sample.curationStatus(),
                sample.createdBy(),
                sample.createdAt(),
                sample.updatedAt()
        );
    }

    DocumentWebhookEventResponse toWebhookEventResponse(DocumentWebhookEvent event) {
        return new DocumentWebhookEventResponse(
                event.id(),
                event.sourceId(),
                event.importId(),
                event.sourceCode(),
                event.eventId(),
                event.idempotencyKey(),
                event.eventType(),
                event.eventVersion(),
                event.signatureStatus(),
                event.status(),
                event.payloadDigest(),
                event.errorMessage(),
                event.retryCount(),
                event.replayBy(),
                event.replayAt(),
                event.replayTraceId(),
                event.receivedAt(),
                event.processedAt()
        );
    }

    DocumentWebhookEvent sanitizeWebhookEvent(DocumentWebhookEvent event) {
        return new DocumentWebhookEvent(
                event.id(),
                event.sourceId(),
                event.importId(),
                event.sourceCode(),
                event.eventId(),
                event.idempotencyKey(),
                event.eventType(),
                event.eventVersion(),
                event.signatureStatus(),
                event.status(),
                event.payloadDigest(),
                null,
                event.errorMessage(),
                event.retryCount(),
                event.replayBy(),
                event.replayAt(),
                event.replayTraceId(),
                event.receivedAt(),
                event.processedAt()
        );
    }

    private List<UUID> requirementIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(UUID.class).readValue(json);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private JsonNode feedbackSnapshotNode(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private static long countCandidates(List<DocumentRequirementCandidate> candidates, DocumentCandidateStatus status) {
        return candidates.stream().filter(candidate -> candidate.status() == status).count();
    }

    private static ParsedRequirementResponse toRequirementResponse(ParsedRequirementDraft draft) {
        return new ParsedRequirementResponse(
                draft.title(),
                draft.description(),
                draft.priority(),
                draft.acceptanceCriteria(),
                draft.tags(),
                draft.assetRequirementId(),
                draft.parseSource(),
                draft.modelInvocationId(),
                draft.modelProviderName(),
                draft.modelName()
        );
    }
}
