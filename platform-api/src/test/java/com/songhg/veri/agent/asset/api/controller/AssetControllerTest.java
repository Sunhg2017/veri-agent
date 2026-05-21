package com.songhg.veri.agent.asset.api.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.bootstrap.token=init-token",
        "veri-agent.auth.token-secret=test-auth-secret",
        "veri-agent.asset.service-token=test-asset-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("登录接口V2"))
                .andExpect(jsonPath("$.data.path").value("/api/v2/login"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/asset/apis")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
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
                                  "componentTree": {"form": "login"},
                                  "screenshotUrl": "https://example.test/login.png",
                                  "status": "DEPRECATED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("登录页V2"))
                .andExpect(jsonPath("$.data.source").value("FIGMA"))
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
    void managesTraceLinks() throws Exception {
        String reqId = createRequirement("需求", "测试", "MEDIUM");
        String apiId = createApi("API", "GET", "/api/test");
        String caseId = createTestCase("用例", reqId);

        String linkId = createTraceLink(reqId, apiId, caseId);

        mockMvc.perform(get("/api/v1/asset/links")
                        .headers(authHeaders())
                        .param("requirementId", reqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].requirementId").value(reqId))
                .andExpect(jsonPath("$.data.items[0].apiId").value(apiId))
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
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
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
        MvcResult result = mockMvc.perform(post("/api/v1/asset/links")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirementId": "%s",
                                  "apiId": "%s",
                                  "caseId": "%s"
                                }
                                """.formatted(reqId, apiId, caseId)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
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

    private String userAccessToken() throws Exception {
        bootstrapSuperAdmin();
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin_user",
                                  "password": "PlainPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private void bootstrapSuperAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap/super-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bootstrapToken": "init-token",
                                  "username": "admin_user",
                                  "password": "PlainPassword123",
                                  "displayName": "平台管理员",
                                  "email": "admin@example.com"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
