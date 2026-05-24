package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentModelRequirementParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentRequirementParser requirementParser = new DocumentRequirementParser(objectMapper);

    @Test
    void delegatesModelParsingToModelInvocationServiceAndPreservesTraceMetadata() {
        ModelInvocationService invocationService = mock(ModelInvocationService.class);
        UUID invocationId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        when(invocationService.invoke(any(ModelInvocationCommand.class), any(ServicePrincipal.class)))
                .thenReturn(new ModelInvocationResult(
                        invocationId,
                        providerId,
                        "local-echo-primary",
                        "test-local-model",
                        false,
                        """
                                ```json
                                {"requirements":[{"title":"AI 登录需求","description":"支持账号密码登录","priority":"HIGH","acceptanceCriteria":["登录成功"],"tags":["ai","login"]}]}
                                ```
                                """,
                        10,
                        6,
                        new BigDecimal("0.01")
                ));
        DocumentModelRequirementParser parser = new DocumentModelRequirementParser(
                invocationService,
                requirementParser,
                objectMapper,
                properties(true, "custom-wp4-parse", "CONFIDENTIAL", true)
        );

        DocumentModelParseResult result = parser.parse(
                "project-ai",
                DocumentSourceType.MARKDOWN,
                "登录 PRD",
                "prd-1",
                "https://example.test/prd-1",
                "## 登录需求\nAcceptance Criteria:\n- 登录成功",
                "user-001"
        );

        assertThat(result.errorCode()).isNull();
        assertThat(result.drafts()).singleElement().satisfies(draft -> {
            assertThat(draft.title()).isEqualTo("AI 登录需求");
            assertThat(draft.priority()).isEqualTo("HIGH");
            assertThat(draft.parseSource()).isEqualTo("MODEL");
            assertThat(draft.modelInvocationId()).isEqualTo(invocationId);
            assertThat(draft.modelProviderName()).isEqualTo("local-echo-primary");
            assertThat(draft.modelName()).isEqualTo("test-local-model");
        });

        ArgumentCaptor<ModelInvocationCommand> requestCaptor = ArgumentCaptor.forClass(ModelInvocationCommand.class);
        ArgumentCaptor<ServicePrincipal> principalCaptor = ArgumentCaptor.forClass(ServicePrincipal.class);
        verify(invocationService).invoke(requestCaptor.capture(), principalCaptor.capture());
        ModelInvocationCommand request = requestCaptor.getValue();
        assertThat(request.projectId()).isEqualTo("project-ai");
        assertThat(request.promptKey()).isEqualTo("custom-wp4-parse");
        assertThat(request.allowPublicModel()).isTrue();
        assertThat(request.sensitivityLevel()).isEqualTo("CONFIDENTIAL");
        assertThat(request.capability()).isEqualTo("REQUIREMENT_PARSE");
        assertThat(request.promptVariables()).isEqualTo(Map.of(
                "schemaMarker",
                DocumentModelRequirementParser.PROMPT_MARKER
        ));
        assertThat(request.messages()).singleElement().satisfies(message -> {
            assertThat(message.role()).isEqualTo("user");
            assertThat(message.content()).contains("\"sourceType\":\"MARKDOWN\"");
            assertThat(message.content()).contains("\"sourceRef\":\"prd-1\"");
            assertThat(message.content()).contains("登录成功");
        });
        assertThat(principalCaptor.getValue().callerService()).isEqualTo("wp4-document-input");
        assertThat(principalCaptor.getValue().delegatedUserId()).isEqualTo("user-001");
    }

    @Test
    void returnsDisabledResultWithoutInvokingModelWhenFeatureFlagIsOff() {
        ModelInvocationService invocationService = mock(ModelInvocationService.class);
        DocumentModelRequirementParser parser = new DocumentModelRequirementParser(
                invocationService,
                requirementParser,
                objectMapper,
                properties(false, null, null, false)
        );

        DocumentModelParseResult result = parser.parse(
                "project-ai",
                DocumentSourceType.TEXT,
                null,
                null,
                null,
                "任意内容",
                null
        );

        assertThat(result.drafts()).isEmpty();
        assertThat(result.errorCode()).isNull();
        verify(invocationService, never()).invoke(any(), any());
    }

    @Test
    void convertsModelInvocationBusinessExceptionToParseFailure() {
        ModelInvocationService invocationService = mock(ModelInvocationService.class);
        when(invocationService.invoke(any(ModelInvocationCommand.class), any(ServicePrincipal.class)))
                .thenThrow(new BusinessException(ErrorCode.SENSITIVE_CONTENT_BLOCKED, "发现敏感内容"));
        DocumentModelRequirementParser parser = new DocumentModelRequirementParser(
                invocationService,
                requirementParser,
                objectMapper,
                properties(true, null, null, false)
        );

        DocumentModelParseResult result = parser.parse(
                "project-ai",
                DocumentSourceType.TEXT,
                "敏感需求",
                null,
                null,
                "password=PlainSecret123",
                null
        );

        assertThat(result.drafts()).isEmpty();
        assertThat(result.errorCode()).isEqualTo("SENSITIVE_CONTENT_BLOCKED");
        assertThat(result.errorMessage()).contains("发现敏感内容");
    }

    private DocumentInputProperties properties(
            boolean modelParseEnabled,
            String promptKey,
            String sensitivityLevel,
            boolean allowPublicModel
    ) {
        return new DocumentInputProperties(
                "test-document-input-token",
                "local-webhook-secret",
                300,
                true,
                true,
                modelParseEnabled,
                promptKey,
                sensitivityLevel,
                allowPublicModel,
                8000,
                16 * 1024 * 1024,
                10 * 1024 * 1024,
                null,
                30,
                20000,
                2,
                true,
                256 * 1024,
                100,
                3,
                false,
                10,
                60,
                300,
                Map.of(),
                "",
                Map.of(),
                "",
                0,
                60,
                true,
                0,
                0,
                "LOCAL_COMMAND",
                null,
                null,
                true,
                null,
                15,
                2,
                2000,
                false,
                90,
                90
        );
    }
}
