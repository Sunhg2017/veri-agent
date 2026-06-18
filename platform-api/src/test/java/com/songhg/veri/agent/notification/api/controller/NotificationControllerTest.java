package com.songhg.veri.agent.notification.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.infrastructure.InMemoryAuthIdentityStore;
import com.songhg.veri.agent.notification.application.NotificationPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.auth.access-token-ttl-minutes=30",
        "veri-agent.auth.session-cleanup-retention-seconds=86400",
        "veri-agent.audit.retention-days=365",
        "veri-agent.audit.min-retention-days=30",
        "veri-agent.audit.retention-cleanup-batch-size=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryAuthIdentityStore identityStore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationPublisher notificationPublisher;

    @Test
    void listsUnreadNotificationsAndMarksThemRead() throws Exception {
        UUID userId = seedUser("admin_user");
        String token = login("admin_user", "PlainPassword123");
        notificationPublisher.publishToUser(
                userId,
                "SYSTEM_INFO",
                "平台消息",
                "第一条通知",
                "#reports",
                Map.of("source", "test")
        );
        notificationPublisher.publishToUser(
                userId,
                "REPORT_READY",
                "报告已生成",
                "报告快照已经准备完成",
                "#reports",
                Map.of("reportId", "report-1")
        );

        MvcResult listResult = mockMvc.perform(get("/api/v1/notifications?status=UNREAD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].unread").value(true))
                .andReturn();

        String firstNotificationId = JsonPath.read(
                listResult.getResponse().getContentAsString(),
                "$.data.items[0].id"
        );

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        mockMvc.perform(post("/api/v1/notifications/{id}/read", firstNotificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(false))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedCount").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/api/v1/notifications?status=READ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].unread").value(false));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    private UUID seedUser(String username) {
        return identityStore.seedUser(
                username,
                passwordEncoder.encode("PlainPassword123"),
                "平台管理员",
                username + "@example.com",
                false,
                List.of("SuperAdmin")
        );
    }

    private String login(String username, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
