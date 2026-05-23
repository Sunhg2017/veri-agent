package com.songhg.veri.agent.asset.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.asset.service-token=test-asset-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AssetControllerTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/asset/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("asset-service"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void rejectsCallsWithoutServiceToken() throws Exception {
        mockMvc.perform(get("/api/v1/asset/requirements"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/asset/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "test"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsUserBearerTokenForAssetWorkbenchRequests() throws Exception {
        String userToken = userAccessToken();

        mockMvc.perform(post("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "用户态需求",
                                  "description": "资产工作台创建",
                                  "priority": "HIGH",
                                  "projectId": "project-wp3"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("用户态需求"));

        mockMvc.perform(get("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .param("projectId", "project-wp3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("用户态需求"));
    }

    @Test
    void managesRequirementsFullLifecycle() throws Exception {
        String reqId = createRequirement("用户登录", "用户登录功能需求", "CRITICAL");

        mockMvc.perform(get("/api/v1/asset/requirements/{id}", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("用户登录"))
                .andExpect(jsonPath("$.data.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/v1/asset/requirements/{id}", reqId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "用户登录V2",
                                  "description": "更新后的需求",
                                  "status": "APPROVED",
                                  "priority": "CRITICAL",
                                  "tags": "auth,login"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("用户登录V2"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.tags").value("auth,login"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/versions", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].changeType").value("UPDATE"))
                .andExpect(jsonPath("$.data[0].actor").value("wp5-test-design:user-001"))
                .andExpect(jsonPath("$.data[0].changedFields", contains("title", "description", "status", "tags")))
                .andExpect(jsonPath("$.data[0].diff.title.before").value("用户登录"))
                .andExpect(jsonPath("$.data[0].diff.title.after").value("用户登录V2"))
                .andExpect(jsonPath("$.data[0].snapshot.version").value(2))
                .andExpect(jsonPath("$.data[0].traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].changeType").value("CREATE"));

        mockMvc.perform(get("/api/v1/asset/requirements")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title").value("用户登录V2"));
    }

    @Test
    void rollsBackRequirementToHistoricalSnapshot() throws Exception {
        String reqId = createRequirement("回滚需求V1", "初始描述", "HIGH");

        mockMvc.perform(put("/api/v1/asset/requirements/{id}", reqId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "回滚需求V2",
                                  "description": "更新描述",
                                  "status": "REVIEWING",
                                  "priority": "MEDIUM",
                                  "tags": "rollback"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/asset/requirements/{id}/versions/1/rollback", reqId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"restore baseline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("回滚需求V1"))
                .andExpect(jsonPath("$.data.description").value("初始描述"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.version").value(3));

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/versions", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].changeType").value("ROLLBACK"))
                .andExpect(jsonPath("$.data[0].changedFields", contains("title", "description", "status", "priority", "tags")));
    }

    @Test
    void archivesDeletesAndRestoresRequirementWithoutBreakingTraceHistory() throws Exception {
        String reqId = createRequirement("生命周期需求", "归档恢复", "HIGH");
        String apiId = createApi("生命周期 API", "GET", "/api/lifecycle-requirement");
        String caseId = createTestCase("生命周期用例", reqId);
        createTraceLink(reqId, apiId, caseId);

        mockMvc.perform(patch("/api/v1/asset/requirements/{id}/lifecycle", reqId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifecycleStatus\":\"ARCHIVED\",\"reason\":\"review done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists())
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(get("/api/v1/asset/requirements")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/asset/requirements")
                        .headers(authHeaders())
                        .param("lifecycleStatus", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(reqId));

        mockMvc.perform(patch("/api/v1/asset/requirements/{id}/lifecycle", reqId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifecycleStatus\":\"DELETED\",\"reason\":\"duplicate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DELETED"))
                .andExpect(jsonPath("$.data.deletedAt").exists())
                .andExpect(jsonPath("$.data.version").value(3));

        mockMvc.perform(get("/api/v1/asset/requirements/{id}", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/lifecycle", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DELETED"));

        mockMvc.perform(get("/api/v1/asset/links")
                        .headers(authHeaders())
                        .param("requirementId", reqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].caseId").value(caseId));

        mockMvc.perform(patch("/api/v1/asset/requirements/{id}/lifecycle", reqId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifecycleStatus\":\"ACTIVE\",\"reason\":\"restore\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(4));

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/versions", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].changeType").value("RESTORE"))
                .andExpect(jsonPath("$.data[1].changeType").value("SOFT_DELETE"))
                .andExpect(jsonPath("$.data[2].changeType").value("ARCHIVE"))
                .andExpect(jsonPath("$.data[0].changedFields", contains("lifecycleStatus", "deletedAt")));
    }

    @Test
    void appliesLifecycleToAllAssetTypes() throws Exception {
        String reqId = createRequirement("多类型需求", "多类型", "MEDIUM");
        String apiId = createApi("多类型 API", "GET", "/api/lifecycle");
        String pageId = createPage("生命周期页", "/lifecycle");
        String flowId = createBusinessFlow("生命周期流程");
        String caseId = createTestCase("生命周期用例", reqId);

        patchLifecycle("/api/v1/asset/apis/{id}/lifecycle", apiId, "ARCHIVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ARCHIVED"));
        patchLifecycle("/api/v1/asset/pages/{id}/lifecycle", pageId, "DELETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DELETED"));
        patchLifecycle("/api/v1/asset/business-flows/{id}/lifecycle", flowId, "ARCHIVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ARCHIVED"));
        patchLifecycle("/api/v1/asset/test-cases/{id}/lifecycle", caseId, "DELETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DELETED"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(get("/api/v1/asset/apis")
                        .headers(authHeaders())
                        .param("lifecycleStatus", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/asset/pages/{id}", pageId)
                        .headers(authHeaders()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/asset/pages/{id}/lifecycle", pageId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DELETED"));
        mockMvc.perform(get("/api/v1/asset/test-cases/{id}/versions", caseId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].changeType").value("SOFT_DELETE"));
    }

    @Test
    void managesApisFullLifecycle() throws Exception {
        String apiId = createApi("登录接口", "POST", "/api/v1/login");

        mockMvc.perform(get("/api/v1/asset/apis/{id}", apiId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("登录接口"))
                .andExpect(jsonPath("$.data.httpMethod").value("POST"))
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.data.path").value("/api/v1/login"));

        mockMvc.perform(put("/api/v1/asset/apis/{id}", apiId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "登录接口V2",
                                  "description": "登录接口优化",
                                  "httpMethod": "POST",
                                  "path": "/api/v2/login",
                                  "version": "2.0.0",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("登录接口V2"))
                .andExpect(jsonPath("$.data.path").value("/api/v2/login"))
                .andExpect(jsonPath("$.data.version").value("2.0.0"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/asset/apis")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void rejectsDuplicateManualApiPathAndMethod() throws Exception {
        createApi("重复 API", "POST", "/api/duplicate");

        mockMvc.perform(post("/api/v1/asset/apis")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "重复 API V2",
                                  "httpMethod": "post",
                                  "path": "/api/duplicate",
                                  "projectId": "project-wp3"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void managesPagesFullLifecycle() throws Exception {
        String pageId = createPage("登录页", "/login");

        mockMvc.perform(get("/api/v1/asset/pages/{id}", pageId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("登录页"))
                .andExpect(jsonPath("$.data.urlPattern").value("/login"))
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(put("/api/v1/asset/pages/{id}", pageId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "登录页V2",
                                  "urlPattern": "/auth/login",
                                  "source": "FIGMA",
                                  "sourceRef": "figma-node-1",
                                  "sourceVersion": "figma-v42",
                                  "componentTree": {"form": "login"},
                                  "screenshotUrl": "https://example.test/login.png",
                                  "status": "DEPRECATED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("登录页V2"))
                .andExpect(jsonPath("$.data.source").value("FIGMA"))
                .andExpect(jsonPath("$.data.sourceVersion").value("figma-v42"))
                .andExpect(jsonPath("$.data.status").value("DEPRECATED"));

        mockMvc.perform(get("/api/v1/asset/pages")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void managesBusinessFlowsFullLifecycle() throws Exception {
        String flowId = createBusinessFlow("登录主流程");

        mockMvc.perform(get("/api/v1/asset/business-flows/{id}", flowId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("登录主流程"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(put("/api/v1/asset/business-flows/{id}", flowId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "登录主流程V2",
                                  "description": "覆盖成功和失败登录",
                                  "flowJson": {"nodes": ["open", "submit"]},
                                  "priority": "CRITICAL",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("登录主流程V2"))
                .andExpect(jsonPath("$.data.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/asset/business-flows")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void managesTestCasesFullLifecycle() throws Exception {
        String reqId = createRequirement("需求A", "测试需求", "HIGH");

        String caseId = createTestCase("登录测试用例", reqId);

        mockMvc.perform(get("/api/v1/asset/test-cases/{id}", caseId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("登录测试用例"))
                .andExpect(jsonPath("$.data.requirementId").value(reqId))
                .andExpect(jsonPath("$.data.steps", hasSize(0)))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/v1/asset/test-cases/{id}/steps", caseId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "steps": [
                                    {"action": "打开登录页面", "expectedResult": "显示登录表单"},
                                    {"action": "输入用户名密码", "expectedResult": "登录成功"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].action").value("打开登录页面"))
                .andExpect(jsonPath("$.data[1].expectedResult").value("登录成功"));

        mockMvc.perform(get("/api/v1/asset/test-cases/{id}/steps", caseId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/v1/asset/test-cases/{id}", caseId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.steps", hasSize(2)));

        mockMvc.perform(get("/api/v1/asset/test-cases/{id}/versions", caseId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].changeType").value("STEPS_UPDATE"))
                .andExpect(jsonPath("$.data[0].changedFields", contains("steps")))
                .andExpect(jsonPath("$.data[0].diff.steps.before", hasSize(0)))
                .andExpect(jsonPath("$.data[0].diff.steps.after", hasSize(2)))
                .andExpect(jsonPath("$.data[0].snapshot.steps", hasSize(2)))
                .andExpect(jsonPath("$.data[1].changeType").value("CREATE"));

        mockMvc.perform(get("/api/v1/asset/test-cases")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void rollsBackTestCaseToHistoricalSnapshot() throws Exception {
        String reqId = createRequirement("回滚用例需求", "测试需求", "HIGH");
        String caseId = createTestCase("回滚用例V1", reqId);

        mockMvc.perform(put("/api/v1/asset/test-cases/{id}/steps", caseId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "steps": [
                                    {"action": "打开页面", "expectedResult": "显示表单"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/asset/test-cases/{id}/versions/1/rollback", caseId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"restore case\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("回滚用例V1"))
                .andExpect(jsonPath("$.data.steps", hasSize(0)))
                .andExpect(jsonPath("$.data.version").value(3));

        mockMvc.perform(get("/api/v1/asset/test-cases/{id}/versions", caseId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].changeType").value("ROLLBACK"))
                .andExpect(jsonPath("$.data[0].changedFields", contains("steps")));
    }

    @Test
    void managesTraceLinks() throws Exception {
        String reqId = createRequirement("需求", "测试", "MEDIUM");
        String apiId = createApi("API", "GET", "/api/test");
        String pageId = createPage("页面", "/trace");
        String flowId = createBusinessFlow("追踪流程");
        String caseId = createTestCase("用例", reqId);

        String linkId = createTraceLink(reqId, apiId, pageId, flowId, caseId);

        mockMvc.perform(get("/api/v1/asset/links")
                        .headers(authHeaders())
                        .param("requirementId", reqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].requirementId").value(reqId))
                .andExpect(jsonPath("$.data.items[0].apiId").value(apiId))
                .andExpect(jsonPath("$.data.items[0].pageId").value(pageId))
                .andExpect(jsonPath("$.data.items[0].flowId").value(flowId))
                .andExpect(jsonPath("$.data.items[0].caseId").value(caseId));

        mockMvc.perform(get("/api/v1/asset/links")
                        .headers(authHeaders())
                        .param("apiId", apiId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/asset/links")
                        .headers(authHeaders())
                        .param("caseId", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/asset/links")
                        .headers(authHeaders())
                        .param("pageId", pageId)
                        .param("flowId", flowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/asset/links")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void syncsPrototypePagesIdempotentlyAndAnalyzesImpact() throws Exception {
        String reqId = createRequirement("原型关联需求", "原型同步", "MEDIUM");
        MvcResult dryRun = mockMvc.perform(post("/api/v1/asset/prototype-sync")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp3",
                                  "source": "FIGMA",
                                  "sourceVersion": "v1",
                                  "dryRun": true,
                                  "pages": [
                                    {
                                      "name": "登录页",
                                      "urlPattern": "/login",
                                      "sourceRef": "figma-node-login",
                                      "componentTree": {"type": "page", "children": []},
                                      "screenshotUrl": "https://cdn.example.test/login.png"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.created").value(1))
                .andReturn();
        String plannedPageId = JsonPath.read(dryRun.getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(get("/api/v1/asset/pages/{id}", plannedPageId)
                        .headers(authHeaders()))
                .andExpect(status().isNotFound());

        MvcResult synced = mockMvc.perform(post("/api/v1/asset/prototype-sync")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp3",
                                  "source": "FIGMA",
                                  "sourceVersion": "v1",
                                  "dryRun": false,
                                  "pages": [
                                    {
                                      "name": "登录页",
                                      "urlPattern": "/login",
                                      "sourceRef": "figma-node-login",
                                      "componentTree": {"type": "page", "children": []},
                                      "screenshotUrl": "https://cdn.example.test/login.png"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andReturn();
        String pageId = JsonPath.read(synced.getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/asset/prototype-sync")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp3",
                                  "source": "FIGMA",
                                  "sourceVersion": "v1",
                                  "dryRun": false,
                                  "pages": [
                                    {
                                      "name": "登录页V2",
                                      "urlPattern": "/login",
                                      "sourceRef": "figma-node-login",
                                      "componentTree": {"type": "page", "children": ["submit"]},
                                      "screenshotUrl": "https://cdn.example.test/login-v2.png"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(pageId));

        createTraceLink(reqId, null, pageId, null, null);

        mockMvc.perform(get("/api/v1/asset/impact")
                        .headers(authHeaders())
                        .param("projectId", "project-wp3")
                        .param("assetType", "REQUIREMENT")
                        .param("assetId", reqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirementCount").value(1))
                .andExpect(jsonPath("$.data.pageCount").value(1))
                .andExpect(jsonPath("$.data.pages[0].id").value(pageId))
                .andExpect(jsonPath("$.data.gaps").isArray());
    }

    @Test
    void dryRunsRequirementCsvImportWithRowErrorsWithoutWritingAssets() throws Exception {
        importAssets(
                        "REQUIREMENT",
                        "CSV",
                        true,
                        """
                                title,description,priority,sourceRef
                                导入需求,CSV dry-run,HIGH,REQ-DRY-1
                                ,缺少标题,LOW,REQ-DRY-2
                                """
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetType").value("REQUIREMENT"))
                .andExpect(jsonPath("$.data.format").value("CSV"))
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.items[0].row").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.items[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.data.items[1].row").value(2))
                .andExpect(jsonPath("$.data.items[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[1].errors[0]").value("title 不能为空"));

        mockMvc.perform(get("/api/v1/asset/requirements")
                        .headers(authHeaders())
                        .param("projectId", "project-wp3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void importsRequirementAndUpdatesExistingDraftBySourceRef() throws Exception {
        MvcResult imported = importAssets(
                        "REQUIREMENT",
                        "CSV",
                        false,
                        """
                                title,description,priority,sourceRef,tags
                                导入需求,初始描述,HIGH,REQ-IMPORT-1,import
                                """
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.items[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andReturn();
        String reqId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.items[0].id");

        importAssets(
                        "REQUIREMENT",
                        "JSON",
                        false,
                        """
                                [
                                  {
                                    "title": "导入需求V2",
                                    "description": "更新描述",
                                    "priority": "CRITICAL",
                                    "sourceRef": "REQ-IMPORT-1",
                                    "tags": "import,updated"
                                  }
                                ]
                                """
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(reqId))
                .andExpect(jsonPath("$.data.items[0].action").value("UPDATE"));
        mockMvc.perform(get("/api/v1/asset/requirements/{id}", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("导入需求V2"))
                .andExpect(jsonPath("$.data.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.data.tags").value("import,updated"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/versions", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].changeType").value("UPSERT"))
                .andExpect(jsonPath("$.data[0].diff.title.after").value("导入需求V2"));
    }

    @Test
    void importsOpenApiApisAndIdempotentlyUpdatesExistingApis() throws Exception {
        String openApi = """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Import API", "version": "1.0.0"},
                  "paths": {
                    "/api/imported/orders": {
                      "get": {
                        "summary": "订单查询",
                        "description": "查询订单列表",
                        "responses": {
                          "200": {
                            "description": "OK",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {"total": {"type": "integer"}}
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/imported/orders/{id}": {
                      "delete": {
                        "summary": "删除订单",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"type": "object", "required": ["reason"]}
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        importAssets("API", "OPENAPI", false, openApi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetType").value("API"))
                .andExpect(jsonPath("$.data.format").value("OPENAPI"))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.items[0].action").value("CREATE"));

        MvcResult listed = mockMvc.perform(get("/api/v1/asset/apis")
                        .headers(authHeaders())
                        .param("projectId", "project-wp3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        String listedJson = listed.getResponse().getContentAsString();
        String ordersApiId = firstJsonPathValue(listedJson, "$.data.items[?(@.path == '/api/imported/orders')].id");
        String ordersVersion = firstJsonPathValue(listedJson, "$.data.items[?(@.path == '/api/imported/orders')].version");
        String ordersSource = firstJsonPathValue(listedJson, "$.data.items[?(@.path == '/api/imported/orders')].source");
        String ordersResponseSchema = firstJsonPathValue(listedJson, "$.data.items[?(@.path == '/api/imported/orders')].responseSchema");
        MatcherAssert.assertThat(ordersVersion, equalTo("1.0.0"));
        MatcherAssert.assertThat(ordersSource, equalTo("OPENAPI"));
        MatcherAssert.assertThat(ordersResponseSchema, containsString("total"));

        importAssets("API", "OPENAPI", true, openApi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.updated").value(0))
                .andExpect(jsonPath("$.data.skipped").value(2))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.items[0].action").value("LINK_EXISTING"))
                .andExpect(jsonPath("$.data.items[0].status").value("PLANNED"));

        String changedOpenApi = """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Import API", "version": "1.1.0"},
                  "paths": {
                    "/api/imported/orders": {
                      "get": {
                        "summary": "订单查询V2",
                        "description": "查询订单列表新版",
                        "responses": {
                          "200": {
                            "description": "OK",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {"items": {"type": "array"}}
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/imported/orders/{id}": {
                      "delete": {"summary": "删除订单"}
                    }
                  }
                }
                """;

        importAssets("API", "OPENAPI", false, changedOpenApi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.updated").value(2))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(ordersApiId))
                .andExpect(jsonPath("$.data.items[0].action").value("UPDATE"));

        MvcResult updated = mockMvc.perform(get("/api/v1/asset/apis")
                        .headers(authHeaders())
                        .param("projectId", "project-wp3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        String updatedJson = updated.getResponse().getContentAsString();
        String updatedOrdersApiId = firstJsonPathValue(updatedJson, "$.data.items[?(@.path == '/api/imported/orders')].id");
        String updatedSummary = firstJsonPathValue(updatedJson, "$.data.items[?(@.path == '/api/imported/orders')].summary");
        String updatedVersion = firstJsonPathValue(updatedJson, "$.data.items[?(@.path == '/api/imported/orders')].version");
        String updatedResponseSchema = firstJsonPathValue(updatedJson, "$.data.items[?(@.path == '/api/imported/orders')].responseSchema");
        MatcherAssert.assertThat(updatedOrdersApiId, equalTo(ordersApiId));
        MatcherAssert.assertThat(updatedSummary, equalTo("订单查询V2"));
        MatcherAssert.assertThat(updatedVersion, equalTo("1.1.0"));
        MatcherAssert.assertThat(updatedResponseSchema, containsString("items"));
    }

    @Test
    void importsTestCaseJsonWithStepsAndExportsSanitizedJson() throws Exception {
        String reqId = createRequirement("导入用例需求", "关联需求", "HIGH");
        String apiId = createApi("导入用例 API", "GET", "/api/imported/test-cases");

        MvcResult imported = importAssets(
                        "TEST_CASE",
                        "JSON",
                        false,
                        """
                                {
                                  "items": [
                                    {
                                      "title": "导入测试用例",
                                      "description": "覆盖导入步骤",
                                      "requirementId": "%s",
                                      "apiId": "%s",
                                      "priority": "HIGH",
                                      "steps": [
                                        {"action": "打开页面", "expectedResult": "页面加载"},
                                        {"action": "提交查询", "expectedResult": "返回结果"}
                                      ]
                                    }
                                  ]
                                }
                                """.formatted(reqId, apiId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetType").value("TEST_CASE"))
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.items[0].action").value("CREATE"))
                .andReturn();
        String caseId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(get("/api/v1/asset/test-cases/{id}", caseId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("导入测试用例"))
                .andExpect(jsonPath("$.data.projectId").value("project-wp3"))
                .andExpect(jsonPath("$.data.requirementId").value(reqId))
                .andExpect(jsonPath("$.data.apiId").value(apiId))
                .andExpect(jsonPath("$.data.steps", hasSize(2)))
                .andExpect(jsonPath("$.data.steps[0].action").value("打开页面"));

        MvcResult exported = mockMvc.perform(get("/api/v1/asset/exports")
                        .headers(authHeaders())
                        .param("assetType", "TEST_CASE")
                        .param("format", "JSON")
                        .param("projectId", "project-wp3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/json")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"wp3-test-case.json\""))
                .andReturn();
        String json = exported.getResponse().getContentAsString(StandardCharsets.UTF_8);
        MatcherAssert.assertThat(json, containsString("导入测试用例"));
        MatcherAssert.assertThat(json, containsString("project-wp3"));
        MatcherAssert.assertThat(json, containsString("打开页面"));
        MatcherAssert.assertThat(json, not(containsString("traceId")));
        MatcherAssert.assertThat(json, not(containsString("snapshot")));
    }

    @Test
    void exportsSanitizedRequirementCsvAndApiOpenApi() throws Exception {
        createRequirement("导出需求", "用于 CSV 导出", "HIGH");
        createApi("导出 API", "POST", "/api/exported/orders");

        MvcResult requirementCsv = mockMvc.perform(get("/api/v1/asset/exports")
                        .headers(authHeaders())
                        .param("assetType", "REQUIREMENT")
                        .param("format", "CSV")
                        .param("projectId", "project-wp3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"wp3-requirement.csv\""))
                .andReturn();
        String csv = requirementCsv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        MatcherAssert.assertThat(csv, containsString("code,title,description,status,priority,projectId"));
        MatcherAssert.assertThat(csv, containsString("导出需求"));
        MatcherAssert.assertThat(csv, not(containsString("traceId")));
        MatcherAssert.assertThat(csv, not(containsString("snapshot")));

        MvcResult apiOpenApi = mockMvc.perform(get("/api/v1/asset/exports")
                        .headers(authHeaders())
                        .param("assetType", "API")
                        .param("format", "OPENAPI")
                        .param("projectId", "project-wp3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/json")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"wp3-api.json\""))
                .andReturn();
        String openApi = apiOpenApi.getResponse().getContentAsString(StandardCharsets.UTF_8);
        MatcherAssert.assertThat(openApi, containsString("\"openapi\":\"3.0.3\""));
        MatcherAssert.assertThat(openApi, containsString("/api/exported/orders"));
        MatcherAssert.assertThat(openApi, containsString("导出 API"));
        MatcherAssert.assertThat(openApi, containsString("responses"));
        MatcherAssert.assertThat(openApi, not(containsString("traceId")));
        MatcherAssert.assertThat(openApi, not(containsString("snapshot")));
    }

    @Test
    void createsRequirementsAndReturnsWithAutoGeneratedStatus() throws Exception {
        String reqId = createRequirement("商品管理", "商品管理功能", "HIGH");

        mockMvc.perform(get("/api/v1/asset/requirements/{id}", reqId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("商品管理"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void returnsNotFoundForNonExistentRequirement() throws Exception {
        mockMvc.perform(get("/api/v1/asset/requirements/00000000-0000-0000-0000-000000000001")
                        .headers(authHeaders()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validatesRequiredFieldsOnCreate() throws Exception {
        mockMvc.perform(post("/api/v1/asset/requirements")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidRequirementStatusBeforePersistence() throws Exception {
        mockMvc.perform(post("/api/v1/asset/requirements")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "非法状态需求",
                                  "status": "ACTIVE",
                                  "projectId": "project-wp3"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsTestCaseWhenRequirementIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/asset/test-cases")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "孤儿用例",
                                  "requirementId": "00000000-0000-0000-0000-000000000001",
                                  "projectId": "project-wp3"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectsTestCaseWhenReferencedAssetBelongsToAnotherProject() throws Exception {
        String reqId = createRequirement("跨项目需求", "不应被另一个项目引用", "HIGH");

        mockMvc.perform(post("/api/v1/asset/test-cases")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "跨项目用例",
                                  "requirementId": "%s",
                                  "projectId": "project-other"
                                }
                                """.formatted(reqId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsTraceLinkWhenCaseBelongsToAnotherProject() throws Exception {
        String reqId = createRequirement("项目A需求", "项目A", "HIGH");
        String otherReqId = createRequirement("项目B需求", "项目B", "HIGH", "project-other");
        String caseId = createTestCase("项目B用例", otherReqId, "project-other");

        mockMvc.perform(post("/api/v1/asset/links")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirementId": "%s",
                                  "caseId": "%s"
                                }
                                """.formatted(reqId, caseId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String createRequirement(String name, String description, String priority) throws Exception {
        return createRequirement(name, description, priority, "project-wp3");
    }

    private org.springframework.test.web.servlet.ResultActions importAssets(
            String assetType,
            String format,
            boolean dryRun,
            String content
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/asset/imports")
                .headers(authHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(Map.of(
                        "assetType", assetType,
                        "format", format,
                        "projectId", "project-wp3",
                        "dryRun", dryRun,
                        "content", content
                ))));
    }

    private static String firstJsonPathValue(String json, String path) {
        List<String> values = JsonPath.read(json, path);
        return values.getFirst();
    }

    private String createRequirement(String name, String description, String priority, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/requirements")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "%s",
                                  "priority": "%s",
                                  "projectId": "%s"
                                }
                                """.formatted(name, description, priority, projectId)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createApi(String name, String method, String path) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/apis")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "%s",
                                  "httpMethod": "%s",
                                  "path": "%s",
                                  "projectId": "project-wp3"
                                }
                                """.formatted(name, method, path)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createPage(String name, String urlPattern) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/pages")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "urlPattern": "%s",
                                  "projectId": "project-wp3"
                                }
                                """.formatted(name, urlPattern)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createBusinessFlow(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/business-flows")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "登录链路",
                                  "flowJson": {"nodes": []},
                                  "priority": "HIGH",
                                  "projectId": "project-wp3"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createTestCase(String name, String requirementId) throws Exception {
        return createTestCase(name, requirementId, "project-wp3");
    }

    private String createTestCase(String name, String requirementId, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "requirementId": "%s",
                                  "projectId": "%s"
                                }
                                """.formatted(name, requirementId, projectId)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createTraceLink(String reqId, String apiId, String caseId) throws Exception {
        return createTraceLink(reqId, apiId, null, null, caseId);
    }

    private String createTraceLink(String reqId, String apiId, String pageId, String flowId, String caseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/links")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirementId": "%s",
                                  "apiId": %s,
                                  "pageId": %s,
                                  "flowId": %s,
                                  "caseId": %s
                                }
                                """.formatted(reqId, jsonStringOrNull(apiId), jsonStringOrNull(pageId), jsonStringOrNull(flowId), jsonStringOrNull(caseId))))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private static String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private org.springframework.test.web.servlet.ResultActions patchLifecycle(
            String urlTemplate,
            String id,
            String lifecycleStatus
    ) throws Exception {
        return mockMvc.perform(patch(urlTemplate, id)
                .headers(authHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lifecycleStatus\":\"%s\"}".formatted(lifecycleStatus)));
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth("test-asset-token");
        headers.set("X-Caller-Service", "wp5-test-design");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }

    private String userAccessToken() {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "admin_user",
                "平台管理员",
                "admin@example.com",
                "$2a$10$test",
                false,
                1,
                List.of("SuperAdmin")
        )).accessToken();
    }
}
