package com.songhg.veri.agent.auth.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.bootstrap.token=init-token",
        "veri-agent.auth.token-secret=test-auth-secret"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void logsInAndReadsCurrentUser() throws Exception {
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
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.accessToken", not("")))
                .andExpect(jsonPath("$.data.refreshToken", not("")))
                .andExpect(jsonPath("$.data.sessionId", not("")))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("admin_user"))
                .andExpect(jsonPath("$.data.roles[0]").value("SuperAdmin"))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken"
        );

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value("admin_user"))
                .andExpect(jsonPath("$.data.displayName").value("平台管理员"))
                .andExpect(jsonPath("$.data.roles[0]").value("SuperAdmin"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void refreshesAndRevokesSession() throws Exception {
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

        String accessToken = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken"
        );
        String refreshToken = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.refreshToken"
        );

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not("")))
                .andExpect(jsonPath("$.data.refreshToken", not("")))
                .andReturn();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        String refreshedAccessToken = com.jayway.jsonpath.JsonPath.read(
                refreshResult.getResponse().getContentAsString(),
                "$.data.accessToken"
        );

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + refreshedAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"测试登出\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revoked").value(true));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void changesCurrentUserPasswordAndInvalidatesCurrentSession() throws Exception {
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

        String accessToken = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken"
        );

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldPassword": "PlainPassword123",
                                  "newPassword": "ChangedPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChanged").value(true))
                .andExpect(jsonPath("$.data.sessionInvalidated").value(true));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin_user",
                                  "password": "PlainPassword123"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin_user",
                                  "password": "ChangedPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void rejectsBadPassword() throws Exception {
        bootstrapSuperAdmin();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin_user",
                                  "password": "WrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));

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

        String token = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken"
        );

        mockMvc.perform(get("/api/v1/management/audit-logs")
                        .param("action", "登录失败")
                        .param("result", "FAILED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].target").value("admin_user"))
                .andExpect(jsonPath("$.data.items[0].result").value("失败"));
    }

    @Test
    void rejectsMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isForbidden());
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
