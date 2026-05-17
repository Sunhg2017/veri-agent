package com.songhg.veri.agent.bootstrap.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "veri-agent.bootstrap.token=init-token")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SuperAdminBootstrapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapsSuperAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap/super-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bootstrap_token": "init-token",
                                  "username": "admin_user",
                                  "password": "PlainPassword123",
                                  "display_name": "平台管理员",
                                  "email": "admin@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.trace_id", startsWith("trc_")))
                .andExpect(jsonPath("$.data.user_id", not("")))
                .andExpect(jsonPath("$.data.role").value("SuperAdmin"))
                .andExpect(jsonPath("$.data.must_change_password").value(true));
    }

    @Test
    void rejectsRepeatedInitialization() throws Exception {
        String payload = """
                {
                  "bootstrap_token": "init-token",
                  "username": "admin_user",
                  "password": "PlainPassword123",
                  "display_name": "平台管理员",
                  "email": "admin@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/bootstrap/super-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bootstrap/super-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("超级管理员已初始化"));
    }

    @Test
    void rejectsInvalidBootstrapToken() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap/super-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bootstrap_token": "wrong-token",
                                  "username": "admin_user",
                                  "password": "PlainPassword123",
                                  "display_name": "平台管理员",
                                  "email": "admin@example.com"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("初始化令牌无效"));
    }

    @Test
    void validatesUsernameAndPassword() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap/super-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bootstrap_token": "init-token",
                                  "username": "admin user",
                                  "password": "short",
                                  "display_name": "平台管理员",
                                  "email": "admin@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.field_errors").isArray());
    }
}
