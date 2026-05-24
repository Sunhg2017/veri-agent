package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.application.view.DocumentModelParseResult;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.documentinput.domain.ParsedRequirementDraft;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
public class DocumentModelRequirementParser {

    static final String PROMPT_MARKER = "WP4_REQUIREMENT_EXTRACTION_V1";

    private static final UUID MODEL_MAPPING_ID = UUID.fromString("00000000-0000-0000-0000-0000000004a1");

    private final ModelInvocationService modelInvocationService;
    private final DocumentRequirementParser requirementParser;
    private final ObjectMapper objectMapper;
    private final DocumentInputProperties properties;

    public DocumentModelRequirementParser(
            ModelInvocationService modelInvocationService,
            DocumentRequirementParser requirementParser,
            ObjectMapper objectMapper,
            DocumentInputProperties properties
    ) {
        this.modelInvocationService = modelInvocationService;
        this.requirementParser = requirementParser;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public DocumentModelParseResult parse(
            String projectId,
            DocumentSourceType sourceType,
            String fallbackTitle,
            String sourceRef,
            String sourceUrl,
            String content,
            String delegatedUserId
    ) {
        if (!properties.modelParseEnabled()) {
            return DocumentModelParseResult.disabled();
        }
        ModelInvocationResult response = null;
        try {
            // WP4 depends on the modelaccess application contract, not HTTP DTOs.
            response = modelInvocationService.invoke(new ModelInvocationCommand(
                    projectId,
                    null,
                    null,
                    modelParsePromptKey(),
                    Map.of("schemaMarker", PROMPT_MARKER),
                    List.of(new ChatMessage("user", modelPayload(sourceType, fallbackTitle, sourceRef, sourceUrl, content))),
                    null,
                    null,
                    properties.modelParseAllowPublicModel(),
                    modelParseSensitivityLevel(),
                    "REQUIREMENT_PARSE"
            ), new ServicePrincipal("wp4-document-input", delegatedUserId));
            ModelInvocationResult invokeResponse = response;
            List<ParsedRequirementDraft> drafts = parseModelResponse(invokeResponse)
                    .stream()
                    .map(draft -> draft.withParseMetadata(
                            "MODEL",
                            invokeResponse.invocationId(),
                            invokeResponse.providerName(),
                            invokeResponse.modelName()
                    ))
                    .toList();
            if (drafts.isEmpty()) {
                return DocumentModelParseResult.failed(
                        response.invocationId(),
                        response.providerName(),
                        response.modelName(),
                        "MODEL_RESPONSE_EMPTY",
                        DocumentInputMessages.MODEL_NO_VALID_REQUIREMENTS
                );
            }
            return DocumentModelParseResult.succeeded(
                    drafts,
                    response.invocationId(),
                    response.providerName(),
                    response.modelName()
            );
        } catch (BusinessException exception) {
            return DocumentModelParseResult.failed(
                    response == null ? null : response.invocationId(),
                    response == null ? null : response.providerName(),
                    response == null ? null : response.modelName(),
                    exception.getErrorCode().name(),
                    exception.getMessage()
            );
        } catch (RuntimeException exception) {
            return DocumentModelParseResult.failed(
                    response == null ? null : response.invocationId(),
                    response == null ? null : response.providerName(),
                    response == null ? null : response.modelName(),
                    "MODEL_PARSE_FAILED",
                    StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : DocumentInputMessages.MODEL_PARSE_FAILED
            );
        }
    }

    private List<ParsedRequirementDraft> parseModelResponse(ModelInvocationResult response) {
        String json = extractJson(response.content());
        if (!StringUtils.hasText(json)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.MODEL_RESPONSE_NOT_JSON);
        }
        try {
            var root = objectMapper.readTree(json);
            if (root.path("requirements").isArray() && root.path("requirements").isEmpty()) {
                return List.of();
            }
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.MODEL_RESPONSE_NOT_JSON);
        }
        return requirementParser.parse(DocumentSourceType.CUSTOM_API, null, json, modelMapping());
    }

    private String modelPayload(
            DocumentSourceType sourceType,
            String title,
            String sourceRef,
            String sourceUrl,
            String content
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "schemaMarker", PROMPT_MARKER,
                    "sourceType", sourceType.name(),
                    "title", safe(title),
                    "sourceRef", safe(sourceRef),
                    "sourceUrl", safe(sourceUrl),
                    "content", truncate(content)
            ));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, DocumentInputMessages.MODEL_REQUEST_SERIALIZE_FAILED);
        }
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int start;
        char closing;
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            start = objectStart;
            closing = '}';
        } else if (arrayStart >= 0) {
            start = arrayStart;
            closing = ']';
        } else {
            return null;
        }
        int end = trimmed.lastIndexOf(closing);
        return end > start ? trimmed.substring(start, end + 1) : null;
    }

    private DocumentFieldMapping modelMapping() {
        Instant now = Instant.now();
        return new DocumentFieldMapping(
                MODEL_MAPPING_ID,
                "wp4-model-default",
                "WP4 model response mapping",
                "requirements",
                "title",
                "description",
                "priority",
                "acceptanceCriteria",
                "tags",
                now,
                now
        );
    }

    private String modelParsePromptKey() {
        return StringUtils.hasText(properties.modelParsePromptKey())
                ? properties.modelParsePromptKey().trim()
                : "wp4-document-requirement-parse";
    }

    private String modelParseSensitivityLevel() {
        return StringUtils.hasText(properties.modelParseSensitivityLevel())
                ? properties.modelParseSensitivityLevel().trim()
                : "INTERNAL";
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        int limit = properties.modelParseMaxContentChars() <= 0 ? 8000 : properties.modelParseMaxContentChars();
        return content.length() <= limit ? content : content.substring(0, limit);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
