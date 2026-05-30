package com.songhg.veri.agent.testdesign.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventPublisher;
import com.songhg.veri.agent.testdesign.application.TestDesignTaskService;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.asset.service-token=test-asset-token",
        "veri-agent.test-design.service-token=test-design-token",
        "veri-agent.test-design.event-recovery-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TestDesignAsyncGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private TestDesignRepository testDesignRepository;

    @Autowired
    private TestDesignTaskService testDesignTaskService;

    @MockitoBean
    private PlatformEventPublisher platformEventPublisher;

    @Test
    void createsQueuedTaskAndConsumesGenerationEventIdempotently() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken);

        MvcResult accepted = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "异步生成任务",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE", "EXCEPTION"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(0))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)))
                .andReturn();

        String taskId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.data.task.id");
        UUID taskUuid = UUID.fromString(taskId);

        testDesignTaskService.processQueuedTask(taskUuid);
        mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(2))
                .andExpect(jsonPath("$.data.candidates", hasSize(2)));
        assertThat(testDesignRepository.candidatesByTask(taskUuid)).hasSize(2);

        testDesignTaskService.processQueuedTask(taskUuid);
        assertThat(testDesignRepository.candidatesByTask(taskUuid)).hasSize(2);
    }

    @Test
    void manuallyReplaysQueuedGenerationEventWithoutChangingTaskState() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken);
        String taskId = UUID.randomUUID().toString();
        saveQueuedTask(taskId, requirementId);

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/replay-queued-event", taskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.id").value(taskId))
                .andExpect(jsonPath("$.data.task.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.task.generationOrchestrationPolicy.manualQueuedEventReplayReady")
                        .value(true))
                .andExpect(jsonPath("$.data.task.generationOrchestrationPolicy.queueMessageBodyExported")
                        .value(false));

        assertThat(testDesignRepository.task(UUID.fromString(taskId))).get()
                .extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.QUEUED.name());
        verify(platformEventPublisher).publish(
                eq("veri-agent.test-design-generation-requested"),
                any(PlatformEventEnvelope.class),
                eq(Duration.ZERO)
        );
    }

    @Test
    void rejectsManualReplayForNonQueuedTasks() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken);
        String taskId = UUID.randomUUID().toString();
        saveQueuedTask(taskId, requirementId);
        testDesignTaskService.processQueuedTask(UUID.fromString(taskId));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/replay-queued-event", taskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        verify(platformEventPublisher, never()).publish(
                eq("veri-agent.test-design-generation-requested"),
                any(PlatformEventEnvelope.class),
                eq(Duration.ZERO)
        );
    }

    private String createRequirement(String userToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "异步生成需求",
                                  "description": "WP5 异步生成测试需求",
                                  "priority": "HIGH",
                                  "projectId": "project-wp5",
                                  "acceptanceCriteria": "后台事件生成候选后可评审"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void saveQueuedTask(String taskId, String requirementId) {
        UUID id = UUID.fromString(taskId);
        Instant now = Instant.now();
        testDesignRepository.saveTask(new TestDesignTask(
                id,
                "project-wp5",
                "人工重发排队事件任务",
                TestDesignTaskStatus.QUEUED.name(),
                requirementId,
                "SMOKE",
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                1,
                0,
                0,
                0,
                null,
                "wp5_async_user",
                null,
                null,
                "digest-" + id,
                "{}",
                now,
                now
        ));
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp5_async_user",
                "WP5 Async User",
                "wp5-async@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }
}
