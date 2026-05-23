package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret",
        "veri-agent.management.environment-connectivity-check-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Test
    void readsManagementWorkspaceWithBearerToken() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(get("/api/v1/management/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.items[0].name").value("质量工程中心"))
                .andExpect(jsonPath("$.data.index").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(3)));

        mockMvc.perform(get("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(3)));

        mockMvc.perform(get("/api/v1/management/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("密码最小长度"));

        mockMvc.perform(get("/api/v1/management/users?index=0&size=1&search=shao")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("shao.min"));
    }

    @Test
    void createsCoreManagementResourcesAndWritesAuditLog() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"安全测试组\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("安全测试组"))
                .andExpect(jsonPath("$.data.lead").value("平台管理员"));

        mockMvc.perform(post("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 验收项目\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("WP1 验收项目"))
                .andExpect(jsonPath("$.data.status").value("规划中"));

        mockMvc.perform(post("/api/v1/management/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"admin-console\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("admin-console"))
                .andExpect(jsonPath("$.data.status").value("接入中"));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "登记应用")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actor").value("admin_user"))
                .andExpect(jsonPath("$.data.items[0].action").value("登记应用"))
                .andExpect(jsonPath("$.data.items[0].target").value("admin-console"));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("actor", "admin_user")
                        .param("action", "登记应用")
                        .param("resourceType", "application")
                        .param("result", "SUCCESS")
                        .param("startTime", "2000-01-01T00:00:00Z")
                        .param("endTime", "2999-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("登记应用"))
                .andExpect(jsonPath("$.data.items[0].target").value("admin-console"));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "登记应用")
                        .param("endTime", "2000-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void exportsFilteredAuditLogsAsCsvAndWritesAuditLog() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CSV 审计组\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/management/audit-logs/export")
                        .param("action", "创建部门")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"wp1-audit-logs.csv\""))
                .andExpect(content().string(containsString("time,actor,action,target,result")))
                .andExpect(content().string(containsString("CSV 审计组")));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "导出审计")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("导出审计"));
    }

    @Test
    void readsUpdatesAndChangesDepartmentStatus() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(get("/api/v1/management/departments/质量工程中心")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("质量工程中心"))
                .andExpect(jsonPath("$.data.status").value("同步正常"));

        mockMvc.perform(patch("/api/v1/management/departments/质量工程中心")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"质量保障中心\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("质量保障中心"));

        mockMvc.perform(patch("/api/v1/management/departments/质量保障中心/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(patch("/api/v1/management/departments/质量保障中心/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("同步正常"));
    }

    @Test
    void managesIntegrationConfigurationLifecycle() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/integrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "zentao",
                                  "name": "禅道",
                                  "category": "缺陷系统",
                                  "scope": "项目级"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key").value("zentao"))
                .andExpect(jsonPath("$.data.name").value("禅道"))
                .andExpect(jsonPath("$.data.status").value("已启用"));

        mockMvc.perform(get("/api/v1/management/integrations/zentao")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("缺陷系统"));

        mockMvc.perform(patch("/api/v1/management/integrations/zentao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"禅道缺陷平台\",\"scope\":\"平台级\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("禅道缺陷平台"))
                .andExpect(jsonPath("$.data.scope").value("平台级"));

        mockMvc.perform(patch("/api/v1/management/integrations/zentao/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(get("/api/v1/management/integrations?search=禅道")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].key").value("zentao"));
    }

    @Test
    void managesSettingLifecycle() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "account.failed_login_limit",
                                  "name": "失败登录阈值",
                                  "value": "5",
                                  "scopeType": "SYSTEM"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key").value("account.failed_login_limit"))
                .andExpect(jsonPath("$.data.name").value("失败登录阈值"))
                .andExpect(jsonPath("$.data.value").value("5"))
                .andExpect(jsonPath("$.data.scope").value("平台级"))
                .andExpect(jsonPath("$.data.status").value("已启用"));

        mockMvc.perform(get("/api/v1/management/settings/account.failed_login_limit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("失败登录阈值"));

        mockMvc.perform(patch("/api/v1/management/settings/account.failed_login_limit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"登录失败锁定阈值\",\"value\":\"6\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("登录失败锁定阈值"))
                .andExpect(jsonPath("$.data.value").value("6"));

        mockMvc.perform(get("/api/v1/management/settings?search=锁定阈值")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].key").value("account.failed_login_limit"));

        mockMvc.perform(patch("/api/v1/management/settings/account.failed_login_limit/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(get("/api/v1/management/settings?search=锁定阈值")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "停用设置")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].target").value("登录失败锁定阈值"));

        mockMvc.perform(post("/api/v1/management/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "integration.api_token",
                                  "name": "集成 API Token",
                                  "value": "real-token-value",
                                  "scopeType": "SYSTEM"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECRET_POLICY_VIOLATION"));
    }

    @Test
    void managesSecretReferencesWithoutLeakingPlaintext() throws Exception {
        String token = superAdminToken();
        String scopeId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/management/secrets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "secretRef": "secret://wp1/webhook-signing",
                                  "providerCode": "local",
                                  "purpose": "WEBHOOK_SIGNING",
                                  "scopeType": "CONFIG",
                                  "scopeId": "%s",
                                  "secretValue": "PlainSecret123",
                                  "secretVersion": "v1"
                                }
                                """.formatted(scopeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.secretRef").value("secret://wp1/webhook-signing"))
                .andExpect(jsonPath("$.data.providerCode").value("local"))
                .andExpect(jsonPath("$.data.maskedValue").value("********"))
                .andExpect(jsonPath("$.data.secretVersion").value("v1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(content().string(not(containsString("PlainSecret123"))));

        mockMvc.perform(get("/api/v1/management/secrets")
                        .param("search", "webhook")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].secretRef").value("secret://wp1/webhook-signing"))
                .andExpect(content().string(not(containsString("PlainSecret123"))));

        mockMvc.perform(post("/api/v1/management/secrets/rotate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "secretRef": "secret://wp1/webhook-signing",
                                  "secretValue": "RotatedSecret456",
                                  "secretVersion": "v2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secretVersion").value("v2"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(content().string(not(containsString("RotatedSecret456"))));

        mockMvc.perform(post("/api/v1/management/secrets/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secretRef\":\"secret://wp1/webhook-signing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(content().string(not(containsString("RotatedSecret456"))));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "轮换密钥引用")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].target").value("secret://wp1/webhook-signing"))
                .andExpect(content().string(not(containsString("PlainSecret123"))))
                .andExpect(content().string(not(containsString("RotatedSecret456"))));
    }

    @Test
    void readsAndUpdatesUserProfile() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"profile.user\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("profile.user"))
                .andExpect(jsonPath("$.data.displayName").value("profile.user"));

        mockMvc.perform(patch("/api/v1/management/users/profile.user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"资料用户\",\"email\":\"profile.user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("profile.user"))
                .andExpect(jsonPath("$.data.displayName").value("资料用户"))
                .andExpect(jsonPath("$.data.email").value("profile.user@example.com"));

        mockMvc.perform(get("/api/v1/management/users/profile.user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("资料用户"))
                .andExpect(jsonPath("$.data.email").value("profile.user@example.com"));
    }

    @Test
    void acceptsFormalProjectApplicationAndEnvironmentCreatePayloads() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "wp1-formal-project",
                                  "name": "WP1 正式项目",
                                  "sensitivityLevel": "CONFIDENTIAL",
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("WP1 正式项目"))
                .andExpect(jsonPath("$.data.status").value("规划中"));

        mockMvc.perform(post("/api/v1/management/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "wp1-formal-app",
                                  "name": "WP1 正式应用",
                                  "project": "WP1 正式项目",
                                  "appType": "Backend",
                                  "defaultWebUrl": "https://formal.example.test",
                                  "defaultApiBaseUrl": "https://formal.example.test/api",
                                  "sensitivityLevel": "STRICT",
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("WP1 正式应用"))
                .andExpect(jsonPath("$.data.type").value("Backend"));

        mockMvc.perform(post("/api/v1/management/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "wp1-formal-env",
                                  "name": "WP1 正式环境",
                                  "project": "WP1 正式项目",
                                  "scopeType": "PROJECT",
                                  "envType": "STAGING",
                                  "webUrl": "https://staging.example.test",
                                  "apiBaseUrl": "https://staging.example.test/api"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("WP1 正式环境"))
                .andExpect(jsonPath("$.data.endpoint").value("https://staging.example.test/api"));
    }

    @Test
    void managesProjectApplicationAndEnvironmentDetailUpdateAndStatus() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态项目\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/management/projects/WP1 状态项目")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("WP1 状态项目"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态项目")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态项目更新\",\"sensitivityLevel\":\"STRICT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("WP1 状态项目更新"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态项目更新/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("进行中"));

        mockMvc.perform(post("/api/v1/management/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态应用\",\"project\":\"WP1 状态项目更新\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/management/applications/WP1 状态应用")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态应用更新\",\"appType\":\"API\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("WP1 状态应用更新"))
                .andExpect(jsonPath("$.data.type").value("API"));

        mockMvc.perform(patch("/api/v1/management/applications/WP1 状态应用更新/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(post("/api/v1/management/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态环境\",\"project\":\"WP1 状态项目更新\",\"apiBaseUrl\":\"https://env.old.example.test\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/management/environments/WP1 状态环境")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态环境更新\",\"apiBaseUrl\":\"https://env.new.example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("WP1 状态环境更新"))
                .andExpect(jsonPath("$.data.endpoint").value("https://env.new.example.test"));

        mockMvc.perform(patch("/api/v1/management/environments/WP1 状态环境更新/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));
    }

    @Test
    void checksEnvironmentConnectivityAndRejectsDisabledEnvironment() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "WP1 探活环境",
                                  "webUrl": "https://web.example.test",
                                  "apiBaseUrl": "https://api.example.test"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/management/environments/WP1 探活环境/connectivity-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.environment").value("WP1 探活环境"))
                .andExpect(jsonPath("$.data.status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.message").value("尚未执行连通性检查"));

        mockMvc.perform(post("/api/v1/management/environments/WP1 探活环境/connectivity-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.endpoints.length()").value(1))
                .andExpect(jsonPath("$.data.endpoints[0].target").value("API"))
                .andExpect(jsonPath("$.data.endpoints[0].status").value("SKIPPED"));

        mockMvc.perform(patch("/api/v1/management/environments/WP1 探活环境/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/management/environments/WP1 探活环境/connectivity-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("停用环境不可执行连通性检查"));
    }

    @Test
    void rejectsIllegalStatusTransitionsAndWritesStableErrors() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态拒绝项目\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("规划中"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态拒绝项目/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "项目状态拒绝")
                        .param("result", "DENIED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("项目状态拒绝"))
                .andExpect(jsonPath("$.data.items[0].target").value("WP1 状态拒绝项目"))
                .andExpect(jsonPath("$.data.items[0].result").value("拒绝"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态拒绝项目/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REMOVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态拒绝项目/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("进行中"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态拒绝项目/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("进行中"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态拒绝项目/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态拒绝项目/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(patch("/api/v1/management/projects/WP1 状态拒绝项目")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 停用后编辑\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/management/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态拒绝应用\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/management/applications/WP1 状态拒绝应用/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/management/applications/WP1 状态拒绝应用/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(patch("/api/v1/management/applications/WP1 状态拒绝应用")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 停用后应用编辑\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/management/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 状态拒绝环境\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/management/environments/WP1 状态拒绝环境/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/management/environments/WP1 状态拒绝环境/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(patch("/api/v1/management/environments/WP1 状态拒绝环境")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 停用后环境编辑\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void managesProjectMembersAndScopedRoles() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester.project\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 成员项目\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/management/projects/WP1 成员项目/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester.project\",\"roleCode\":\"Tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester.project"))
                .andExpect(jsonPath("$.data.role").value("Tester"))
                .andExpect(jsonPath("$.data.memberType").value("MEMBER"))
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(get("/api/v1/management/projects/WP1 成员项目/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].username").value("tester.project"))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/v1/management/projects/WP1 成员项目/members/tester.project/remove")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester.project"))
                .andExpect(jsonPath("$.data.status").value("已移除"));
    }

    @Test
    void managesApplicationOwnersAndEnvironmentUsers() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester.scope\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/management/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 作用域应用\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/management/applications/WP1 作用域应用/owners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester.scope\",\"roleCode\":\"AppOwner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester.scope"))
                .andExpect(jsonPath("$.data.role").value("AppOwner"))
                .andExpect(jsonPath("$.data.scopeType").value("APPLICATION"))
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(get("/api/v1/management/applications/WP1 作用域应用/owners")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].username").value("tester.scope"))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/v1/management/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WP1 作用域环境\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/management/environments/WP1 作用域环境/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester.scope\",\"roleCode\":\"Tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester.scope"))
                .andExpect(jsonPath("$.data.role").value("Tester"))
                .andExpect(jsonPath("$.data.scopeType").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(get("/api/v1/management/environments/WP1 作用域环境/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].username").value("tester.scope"))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/v1/management/environments/WP1 作用域环境/users/tester.scope/remove")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester.scope"))
                .andExpect(jsonPath("$.data.status").value("已移除"));

        mockMvc.perform(post("/api/v1/management/applications/WP1 作用域应用/owners/tester.scope/remove")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester.scope"))
                .andExpect(jsonPath("$.data.status").value("已移除"));
    }

    @Test
    void validatesAuditLogTimeRange() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("startTime", "not-a-time")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("startTime", "2026-05-18T00:00:00Z")
                        .param("endTime", "2026-05-17T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void readsAuditOutboxWithStatusAndTraceFilters() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(get("/api/v1/management/audit-outbox")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.items[0].traceId").value("trc_outbox_pending"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].eventAction").value("创建部门"));

        mockMvc.perform(get("/api/v1/management/audit-outbox")
                        .param("status", "FAILED")
                        .param("traceId", "trc_outbox_failed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].lastError").value("insert audit_log timeout"));

        mockMvc.perform(get("/api/v1/management/audit-outbox")
                        .param("status", "RETRYING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void managesUserLifecycleAndWritesAuditLog() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester.lifecycle\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("tester.lifecycle"))
                .andExpect(jsonPath("$.data.status").value("待激活"));

        mockMvc.perform(post("/api/v1/management/users/tester.lifecycle/enable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(get("/api/v1/management/users/tester.lifecycle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester.lifecycle"))
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(post("/api/v1/management/users/tester.lifecycle/lock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已锁定"));

        mockMvc.perform(post("/api/v1/management/users/tester.lifecycle/unlock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(post("/api/v1/management/users/tester.lifecycle/disable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(post("/api/v1/management/users/tester.lifecycle/reset-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "重置密码")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("重置密码"))
                .andExpect(jsonPath("$.data.items[0].target").value("tester.lifecycle"));

        mockMvc.perform(post("/api/v1/management/users/admin_user/lock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void managesRoleCatalogAndUserRoleBindings() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(get("/api/v1/management/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].code").value("SuperAdmin"))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(3)));

        mockMvc.perform(get("/api/v1/management/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].code", hasItems("role:read")))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(10)));

        mockMvc.perform(post("/api/v1/management/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "QaReviewer",
                                  "name": "QA 评审员",
                                  "scopeType": "PROJECT",
                                  "description": "项目内需求和审计评审",
                                  "permissionCodes": ["role:read", "audit:read"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("QaReviewer"))
                .andExpect(jsonPath("$.data.builtin").value(false))
                .andExpect(jsonPath("$.data.permissionCodes", hasItems("role:read", "audit:read")));

        mockMvc.perform(get("/api/v1/management/roles/QaReviewer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("QA 评审员"))
                .andExpect(jsonPath("$.data.permissionCodes", hasItems("role:read", "audit:read")));

        mockMvc.perform(patch("/api/v1/management/roles/QaReviewer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "QA 审计评审员",
                                  "permissionCodes": ["role:read", "audit:read", "audit:export"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("QA 审计评审员"))
                .andExpect(jsonPath("$.data.permissionCodes", hasItems("role:read", "audit:read", "audit:export")));

        mockMvc.perform(patch("/api/v1/management/roles/QaReviewer/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已停用"));

        mockMvc.perform(patch("/api/v1/management/roles/QaReviewer/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("启用"));

        mockMvc.perform(patch("/api/v1/management/roles/SuperAdmin/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/management/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "ForbiddenRole",
                                  "name": "越权角色",
                                  "scopeType": "PROJECT",
                                  "permissionCodes": ["not:granted"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/management/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester.role\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("Tester"));

        mockMvc.perform(post("/api/v1/management/users/tester.role/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"PlatformAdmin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("Tester / PlatformAdmin"));

        mockMvc.perform(post("/api/v1/management/users/tester.role/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"QaReviewer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("Tester / PlatformAdmin / QaReviewer"));

        mockMvc.perform(post("/api/v1/management/users/tester.role/roles/unassign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"PlatformAdmin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("Tester / QaReviewer"));
    }

    @Test
    void rejectsAnonymousManagementAccess() throws Exception {
        mockMvc.perform(get("/api/v1/management/departments"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUserWithoutRequiredManagementPermission() throws Exception {
        String developerToken = tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "dev_user",
                "研发用户",
                "dev@example.com",
                "{noop}unused",
                false,
                1,
                List.of("Developer")
        )).accessToken();

        mockMvc.perform(post("/api/v1/management/departments")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权部门\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("权限不足"));

        mockMvc.perform(get("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/v1/management/users/dev_user/reset-password")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPassword123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/management/secrets")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validatesCreateRequest() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(post("/api/v1/management/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors").isArray());

        mockMvc.perform(post("/api/v1/management/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad code\",\"name\":\"非法编码项目\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/management/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"非法类型应用\",\"appType\":\"Desktop\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String superAdminToken() {
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
