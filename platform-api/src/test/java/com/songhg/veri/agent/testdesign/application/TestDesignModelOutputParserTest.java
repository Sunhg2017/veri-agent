package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.testdesign.application.TestDesignModelOutputParser.ModelGeneratedCase;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDesignModelOutputParserTest {

    private final TestDesignModelOutputParser parser = new TestDesignModelOutputParser(new ObjectMapper());

    @Test
    void parsesValidModelOutputAndNormalizesEnums() {
        List<ModelGeneratedCase> cases = parser.parse("""
                ```json
                {
                  "schemaVersion": "wp5-model-output-v1",
                  "cases": [
                    {
                      "title": "登录成功冒烟验证",
                      "description": "覆盖核心登录路径",
                      "coverageType": "smoke",
                      "priority": "high",
                      "preconditions": "账号已开通",
                      "steps": [
                        {"action": "输入账号密码", "expectedResult": "登录按钮可点击"},
                        {"action": "点击登录", "expectedResult": "进入工作台"}
                      ],
                      "expectedResult": "用户进入工作台且 traceId 可定位",
                      "requirementRef": "REQ-LOGIN",
                      "apiRefs": ["POST /api/login"],
                      "pageRefs": ["登录页"],
                      "flowRefs": ["登录主流程"],
                      "tags": ["wp5", "login"],
                      "rationale": "登录是上线准入核心路径",
                      "riskNotes": "需要覆盖错误提示",
                      "confidence": 0.86
                    }
                  ]
                }
                ```
                """);

        assertThat(cases).hasSize(1);
        ModelGeneratedCase generatedCase = cases.get(0);
        assertThat(generatedCase.coverageType()).isEqualTo("SMOKE");
        assertThat(generatedCase.priority()).isEqualTo("HIGH");
        assertThat(generatedCase.confidence()).isEqualTo(0.86D);
        assertThat(generatedCase.apiRefs()).containsExactly("POST /api/login");
        assertThat(generatedCase.steps()).extracting("stepOrder").containsExactly(0, 1);
    }

    @Test
    void usesDefaultConfidenceWhenModelOmitsIt() {
        List<ModelGeneratedCase> cases = parser.parse("""
                {
                  "cases": [
                    {
                      "title": "权限异常验证",
                      "coverageType": "PERMISSION",
                      "priority": "HIGH",
                      "steps": [
                        {"action": "使用普通成员访问管理页", "expectedResult": "请求被拒绝"},
                        {"action": "查看错误响应", "expectedResult": "返回 403 和 traceId"}
                      ],
                      "expectedResult": "普通成员不能进入管理页"
                    }
                  ]
                }
                """);

        assertThat(cases.get(0).confidence()).isEqualTo(0.5D);
    }

    @Test
    void rejectsUnknownFieldsBeforePersistence() {
        assertThatThrownBy(() -> parser.parse("""
                {
                  "cases": [
                    {
                      "title": "登录成功冒烟验证",
                      "coverageType": "SMOKE",
                      "priority": "HIGH",
                      "unexpected": "raw prompt",
                      "steps": [
                        {"action": "输入账号密码", "expectedResult": "登录按钮可点击"},
                        {"action": "点击登录", "expectedResult": "进入工作台"}
                      ],
                      "expectedResult": "用户进入工作台"
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知字段");
    }

    @Test
    void rejectsSensitiveTextInHumanVisibleFields() {
        assertThatThrownBy(() -> parser.parse("""
                {
                  "cases": [
                    {
                      "title": "登录成功冒烟验证",
                      "coverageType": "SMOKE",
                      "priority": "HIGH",
                      "steps": [
                        {"action": "输入账号密码", "expectedResult": "登录按钮可点击"},
                        {"action": "点击登录", "expectedResult": "token=wp5_secret_123456789"}
                      ],
                      "expectedResult": "用户进入工作台"
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("疑似敏感信息");
    }

    @Test
    void rejectsInvalidEnumsAndIncompleteSteps() {
        assertThatThrownBy(() -> parser.parse("""
                {
                  "cases": [
                    {
                      "title": "登录成功冒烟验证",
                      "coverageType": "HAPPY_PATH",
                      "priority": "P0",
                      "steps": [
                        {"action": "输入账号密码", "expectedResult": ""}
                      ],
                      "expectedResult": "用户进入工作台"
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("coverageType 不支持")
                .hasMessageContaining("priority 不支持")
                .hasMessageContaining("至少需要 2 步")
                .hasMessageContaining("expectedResult 不能为空");
    }

    @Test
    void rejectsNonJsonOutput() {
        assertThatThrownBy(() -> parser.parse("模型解释: 我建议补充登录用例"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是 JSON 对象");
    }
}
