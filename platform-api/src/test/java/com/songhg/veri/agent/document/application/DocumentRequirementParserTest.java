package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.document.domain.DocumentFieldMapping;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.ParsedRequirementDraft;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRequirementParserTest {

    private final DocumentRequirementParser parser = new DocumentRequirementParser(new ObjectMapper());

    @Test
    void parsesJsonWithConfiguredMappingPaths() {
        DocumentFieldMapping mapping = new DocumentFieldMapping(
                UUID.randomUUID(),
                "default",
                "Default",
                "items",
                "name",
                "body",
                "level",
                "checks",
                "labels",
                Instant.now(),
                Instant.now()
        );

        List<ParsedRequirementDraft> result = parser.parse(
                DocumentSourceType.CUSTOM_API,
                null,
                """
                        {"items":[{"name":"注册需求","body":"支持手机号注册","level":"P0","checks":["成功注册"],"labels":["user","register"]}]}
                        """,
                mapping
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("注册需求");
        assertThat(result.getFirst().priority()).isEqualTo("CRITICAL");
        assertThat(result.getFirst().acceptanceCriteria()).isEqualTo("成功注册");
        assertThat(result.getFirst().tags()).isEqualTo("user,register");
    }

    @Test
    void parsesMarkdownHeadingsAsSeparateRequirementDrafts() {
        List<ParsedRequirementDraft> result = parser.parse(
                DocumentSourceType.MARKDOWN,
                null,
                """
                        ## 登录
                        Priority: HIGH
                        Acceptance Criteria:
                        - 登录成功

                        ## 登出
                        Priority: LOW
                        """,
                defaultMapping()
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("登录");
        assertThat(result.get(0).priority()).isEqualTo("HIGH");
        assertThat(result.get(0).acceptanceCriteria()).contains("登录成功");
        assertThat(result.get(1).title()).isEqualTo("登出");
    }

    private DocumentFieldMapping defaultMapping() {
        return new DocumentFieldMapping(
                UUID.randomUUID(),
                "default",
                "Default",
                "requirements",
                "title",
                "description",
                "priority",
                "acceptanceCriteria",
                "tags",
                Instant.now(),
                Instant.now()
        );
    }
}
