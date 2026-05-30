package com.songhg.veri.agent.testdesign.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.asset.service-token=test-asset-token",
        "veri-agent.test-design.service-token=test-design-token",
        "veri-agent.test-design.async-generation-enabled=false",
        "veri-agent.test-design.context-linked-assets-per-requirement=2",
        "veri-agent.test-design.context-explicit-assets-per-type=2",
        "veri-agent.test-design.context-existing-cases-per-requirement=2",
        "veri-agent.test-design.context-requirement-description-chars=180",
        "veri-agent.test-design.context-acceptance-criteria-chars=180",
        "veri-agent.test-design.context-asset-schema-chars=120",
        "veri-agent.test-design.conflict-title-similarity-threshold=1.0",
        "veri-agent.test-design.conflict-content-similarity-threshold=1.0"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TestDesignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private TestDesignRepository testDesignRepository;

    @Autowired
    private ModelAccessRepository modelAccessRepository;

    @Autowired
    private ModelInvocationJobRepository modelInvocationJobRepository;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/test-design/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("test-design"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.generationEnabled").value(true))
                .andExpect(jsonPath("$.data.generationMode").value("RULE_TEMPLATE"))
                .andExpect(jsonPath("$.data.contextLimits.linkedAssetsPerRequirement").value(2))
                .andExpect(jsonPath("$.data.contextLimits.explicitAssetsPerType").value(2))
                .andExpect(jsonPath("$.data.contextLimits.existingCasesPerRequirement").value(2))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.policyVersion")
                        .value("wp5-context-assembly-policy-v2"))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.assemblyMode").value("SNAPSHOT_DIGEST_ONLY"))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.digestStrategy").value("SHA256_CONTEXT_SUMMARY"))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.inputDigestRequired").value(true))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.persistedContextSummaryOnly").value(true))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.wp3ApplicationServiceOnly").value(true))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.rawContextBodyStored").value(false))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.modelPayloadStored").value(false))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.digestValueExported").value(false))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.explicitAssetIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.contextAssemblyPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.policyVersion").value("wp5-context-policy-v1"))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.policySource").value("PLATFORM_DEFAULT"))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.governanceStatus").value("PLATFORM_DEFAULT_ONLY"))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.projectOverrideSupported").value(false))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.environmentOverrideSupported").value(false))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.changeApprovalRequired").value(true))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.changeApprovalWorkflowReady").value(false))
                .andExpect(jsonPath("$.data.contextPolicyGovernance.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.contextPolicyOperations.policyVersion")
                        .value("wp5-context-policy-operations-v2"))
                .andExpect(jsonPath("$.data.contextPolicyOperations.operationMode").value("PLATFORM_DEFAULT_ONLY"))
                .andExpect(jsonPath("$.data.contextPolicyOperations.policyResolutionOrder")
                        .value("PLATFORM_DEFAULT_ONLY"))
                .andExpect(jsonPath("$.data.contextPolicyOperations.policyFallbackBehavior")
                        .value("DEPLOY_CONFIG_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.data.contextPolicyOperations.approvalStatus").value("WORKFLOW_NOT_READY"))
                .andExpect(jsonPath("$.data.contextPolicyOperations.projectOverrideStoreReady").value(false))
                .andExpect(jsonPath("$.data.contextPolicyOperations.environmentOverrideStoreReady").value(false))
                .andExpect(jsonPath("$.data.contextPolicyOperations.changeApprovalWorkflowReady").value(false))
                .andExpect(jsonPath("$.data.contextPolicyOperations.effectivePolicySnapshotMaterialized").value(true))
                .andExpect(jsonPath("$.data.contextPolicyOperations.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.policyVersion").value("wp5-scope-policy-v1"))
                .andExpect(jsonPath("$.data.scopePolicy.scopeModel").value("PROJECT_RESOURCE_SCOPE"))
                .andExpect(jsonPath("$.data.scopePolicy.listFallbackScope")
                        .value("PLATFORM_WHEN_PROJECT_FILTER_ABSENT"))
                .andExpect(jsonPath("$.data.scopePolicy.taskProjectScopeRequired").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.candidateProjectScopeRequired").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.batchCandidateProjectScopeRequired").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.publishProjectScopeRequired").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.asyncTaskProjectScopeRecovered").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.smokeProjectScopeRequired").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.evaluationCorpusProjectIsolated").value(true))
                .andExpect(jsonPath("$.data.scopePolicy.evaluationCorpusOperationsReady").value(false))
                .andExpect(jsonPath("$.data.scopePolicy.crossWpScopeDashboardReady").value(false))
                .andExpect(jsonPath("$.data.scopePolicy.candidateIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.scopePolicy.roleRuleDetailExported").value(false))
                .andExpect(jsonPath("$.data.scopePolicy.serviceTokenValueExported").value(false))
                .andExpect(jsonPath("$.data.scopePolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.policyVersion")
                        .value("wp5-evaluation-corpus-policy-v1"))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.corpusMode")
                        .value("GOLDEN_SET_BASELINE"))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.qualityGateMode")
                        .value("MANUAL_OPT_IN_AI_EVAL"))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.thresholdSource").value("DEPLOY_CONFIG"))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.projectScopeRequired").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.goldenSetBaselineRequired").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.qualityEvalScriptReady").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.qualityGateIntegrated").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.readinessDistributionTracked").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.promptVersionTracked").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.evaluationCorpusProjectIsolated").value(true))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.sampleMaintenanceReady").value(false))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.longTermCalibrationReady").value(false))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.operationsConsoleReady").value(false))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.corpusRowExported").value(false))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.candidateBodyExported").value(false))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.reviewCommentExported").value(false))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.promptBodyExported").value(false))
                .andExpect(jsonPath("$.data.evaluationCorpusPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.policyVersion")
                        .value("wp5-release-readiness-policy-v1"))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.decisionMode")
                        .value("ADVISORY_QUALITY_GATE"))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.thresholdSource").value("DEPLOY_CONFIG"))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.qualityThresholdEvaluated").value(true))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.advisoryOnly").value(true))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.publishBlockingEnabled").value(false))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.manualApprovalRequired").value(true))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.approvalWorkflowReady").value(false))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.autoPublishAllowed").value(false))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.confirmedCandidateRequired").value(true))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.qualityGateOverrideSupported").value(false))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.candidateEvidenceExported").value(false))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.approvalNotesExported").value(false))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.thresholdRuleDetailExported").value(false))
                .andExpect(jsonPath("$.data.releaseReadinessPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.auditChainPolicy.policyVersion")
                        .value("wp5-audit-chain-policy-v1"))
                .andExpect(jsonPath("$.data.auditChainPolicy.chainMode")
                        .value("WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT"))
                .andExpect(jsonPath("$.data.auditChainPolicy.eventSource")
                        .value("TASK_REVIEW_PUBLISH_MODEL_REFERENCES"))
                .andExpect(jsonPath("$.data.auditChainPolicy.wp1AuditEventWritten").value(true))
                .andExpect(jsonPath("$.data.auditChainPolicy.wp2InvocationReferenceTracked").value(true))
                .andExpect(jsonPath("$.data.auditChainPolicy.wp3PublishReferenceTracked").value(true))
                .andExpect(jsonPath("$.data.auditChainPolicy.wp5DomainEventsTracked").value(true))
                .andExpect(jsonPath("$.data.auditChainPolicy.projectScopeRequired").value(true))
                .andExpect(jsonPath("$.data.auditChainPolicy.traceSignalTracked").value(true))
                .andExpect(jsonPath("$.data.auditChainPolicy.crossWpAuditDashboardReady").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.auditOutboxReplayDashboardReady").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.auditEventDetailExported").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.candidateIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.platformAuditIdentifierExported").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.traceIdValueExported").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.modelInvocationIdValueExported").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.publishIdentifierValueExported").value(false))
                .andExpect(jsonPath("$.data.auditChainPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.archivePolicy.policyVersion")
                        .value("wp5-archive-policy-v1"))
                .andExpect(jsonPath("$.data.archivePolicy.retentionDays").value(180))
                .andExpect(jsonPath("$.data.archivePolicy.storagePolicy").value("platformManaged"))
                .andExpect(jsonPath("$.data.archivePolicy.approvalRequired").value(true))
                .andExpect(jsonPath("$.data.archivePolicy.archiveApprovalWorkflowReady").value(false))
                .andExpect(jsonPath("$.data.archivePolicy.externalSharingAllowed").value(false))
                .andExpect(jsonPath("$.data.archivePolicy.retentionPolicyTracked").value(true))
                .andExpect(jsonPath("$.data.archivePolicy.archiveStorageReady").value(false))
                .andExpect(jsonPath("$.data.archivePolicy.archivePathExported").value(false))
                .andExpect(jsonPath("$.data.archivePolicy.archiveNotesExported").value(false))
                .andExpect(jsonPath("$.data.archivePolicy.approvalNotesExported").value(false))
                .andExpect(jsonPath("$.data.archivePolicy.ticketUrlExported").value(false))
                .andExpect(jsonPath("$.data.archivePolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.reportManifestPolicy.policyVersion")
                        .value("wp5-report-manifest-policy-v1"))
                .andExpect(jsonPath("$.data.reportManifestPolicy.schemaVersion")
                        .value("wp5-task-report-v1"))
                .andExpect(jsonPath("$.data.reportManifestPolicy.fieldSetVersion")
                        .value("aggregate-only-v1"))
                .andExpect(jsonPath("$.data.reportManifestPolicy.manifestMode")
                        .value("AGGREGATE_RECONCILIATION"))
                .andExpect(jsonPath("$.data.reportManifestPolicy.rowCountTracked").value(true))
                .andExpect(jsonPath("$.data.reportManifestPolicy.completionStatusTracked").value(true))
                .andExpect(jsonPath("$.data.reportManifestPolicy.archiveReconciliationReady").value(true))
                .andExpect(jsonPath("$.data.reportManifestPolicy.detailRowsExported").value(false))
                .andExpect(jsonPath("$.data.reportManifestPolicy.rowIntegrityValueExported").value(false))
                .andExpect(jsonPath("$.data.reportManifestPolicy.rowContentSummaryExported").value(false))
                .andExpect(jsonPath("$.data.reportManifestPolicy.candidateIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.reportManifestPolicy.traceIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.reportManifestPolicy.auditIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.reportManifestPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.supportedCoverageTypes", hasSize(6)));
    }

    @Test
    void rejectsUnauthenticatedCalls() throws Exception {
        mockMvc.perform(get("/api/v1/test-design/tasks"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/test-design/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsReviewsDryRunsAndPublishesCandidatesToWp3Assets() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken, "WP5 登录需求", "登录成功后进入工作台", "project-wp5");

        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "登录需求用例生成",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE", "EXCEPTION"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(2))
                .andExpect(jsonPath("$.data.candidates", hasSize(2)))
                .andExpect(jsonPath("$.data.candidates[0].status").value("GENERATED"))
                .andExpect(jsonPath("$.data.candidates[0].steps", hasSize(3)))
                .andReturn();

        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/summary", taskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.generatedCount").value(2))
                .andExpect(jsonPath("$.data.candidates").doesNotExist());

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/candidates", taskId)
                        .header("Authorization", "Bearer " + userToken)
                        .param("index", "0")
                        .param("size", "1")
                        .param("status", "GENERATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.index").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].status").value("GENERATED"));

        MvcResult updated = mockMvc.perform(put("/api/v1/test-design/candidates/{id}", candidateId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "编辑后的登录冒烟用例",
                                  "description": "人工补充候选用例",
                                  "coverageType": "SMOKE",
                                  "priority": "HIGH",
                                  "expectedResult": "登录成功",
                                  "tags": ["wp5", "review"],
                                  "version": %d,
                                  "steps": [
                                    {"action": "输入账号密码", "expectedResult": "可提交"},
                                    {"action": "点击登录", "expectedResult": "进入工作台"}
                                  ]
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EDITED"))
                .andExpect(jsonPath("$.data.title").value("编辑后的登录冒烟用例"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn();

        Integer updatedVersion = JsonPath.read(updated.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "comment": "可以发布"}
                                """.formatted(updatedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedBy").isString());

        MvcResult otherCandidates = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/candidates", taskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        String secondCandidateId = JsonPath.read(otherCandidates.getResponse().getContentAsString(), "$.data.items[1].id");
        Integer secondVersion = JsonPath.read(otherCandidates.getResponse().getContentAsString(), "$.data.items[1].version");
        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", secondCandidateId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(secondVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.createdCaseIds", hasSize(0)))
                .andExpect(jsonPath("$.data.records[0].result").value("PLANNED"));

        mockMvc.perform(get("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + userToken)
                        .param("projectId", "project-wp5")
                        .param("source", "AI_GENERATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        MvcResult publish = mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.createdCaseIds", hasSize(2)))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCEEDED"))
                .andReturn();

        String caseId = JsonPath.read(publish.getResponse().getContentAsString(), "$.data.createdCaseIds[0]");
        mockMvc.perform(get("/api/v1/asset/test-cases/{id}", caseId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI_GENERATED"))
                .andExpect(jsonPath("$.data.sourceRef", startsWith("wp5:")))
                .andExpect(jsonPath("$.data.requirementId").value(requirementId))
                .andExpect(jsonPath("$.data.steps", hasSize(2)));

        mockMvc.perform(get("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + userToken)
                        .param("requirementId", requirementId)
                        .param("caseId", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].caseId").value(caseId));
    }

    @Test
    void replaysCreateTaskByIdempotencyKeyAndRejectsPayloadReuse() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken, "幂等创建需求", "重复请求只生成一个任务", "project-wp5");
        String requestBody = """
                {
                  "projectId": "project-wp5",
                  "title": "幂等创建任务",
                  "requirementIds": ["%s"],
                  "coverageTypes": ["SMOKE"]
                }
                """.formatted(requirementId);

        MvcResult first = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", "wp5-create-task-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.idempotencyKey").value("wp5-create-task-001"))
                .andExpect(jsonPath("$.data.task.requestDigest").doesNotExist())
                .andExpect(jsonPath("$.data.task.generatedCount").value(1))
                .andReturn();
        String firstTaskId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.task.id");

        mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", "wp5-create-task-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.id").value(firstTaskId))
                .andExpect(jsonPath("$.data.task.generatedCount").value(1))
                .andExpect(jsonPath("$.data.candidates", hasSize(1)));

        mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", "wp5-create-task-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "复用同一个幂等键但变更标题",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("幂等键")));
    }

    @Test
    void taskSummaryIncludesSanitizedModelObservation() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        UUID taskId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-29T08:00:00Z");

        modelAccessRepository.saveInvocation(new InvocationRecord(
                invocationId,
                "project-wp5",
                null,
                null,
                "INTERNAL",
                "wp5-test-design-v1",
                1,
                providerId,
                "local-echo-primary",
                "local-echo",
                "wp5-cost-aware",
                "default",
                "JSON",
                InvocationStatus.FAILED,
                true,
                "sha256:prompt",
                "apiKey=preview-secret should never leave WP2",
                "Bearer abc.def.ghi should never leave WP2",
                123,
                45,
                new BigDecimal("0.00012345"),
                "MODEL_TIMEOUT",
                "provider token=secret-value timed out",
                875,
                "wp5-test-design",
                "qa.lead",
                now
        ));
        modelInvocationJobRepository.save(new ModelInvocationJobRecord(
                jobId,
                ModelInvocationJobStatus.SUCCEEDED,
                "{}",
                "wp5-test-design",
                "qa.lead",
                "trc_wp5_model_observation",
                now.minusSeconds(2),
                now.minusSeconds(1),
                now,
                invocationId,
                null,
                null,
                "{\"invocationId\":\"%s\"}".formatted(invocationId)
        ));
        testDesignRepository.saveTask(new TestDesignTask(
                taskId,
                "project-wp5",
                "模型观测摘要任务",
                TestDesignTaskStatus.FAILED.name(),
                UUID.randomUUID().toString(),
                "SMOKE",
                "wp5-test-design-v1",
                "1.0.0",
                invocationId,
                "local-echo-primary",
                "local-echo",
                1,
                0,
                0,
                0,
                "生成失败",
                "qa.lead",
                null,
                null,
                "input-digest",
                "{\"contextVersion\":\"wp5-context-v1\"}",
                now.minusSeconds(10),
                now
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/summary", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelObservation.available").value(true))
                .andExpect(jsonPath("$.data.modelObservation.invocationId").value(invocationId.toString()))
                .andExpect(jsonPath("$.data.modelObservation.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.modelObservation.traceId").value("trc_wp5_model_observation"))
                .andExpect(jsonPath("$.data.modelObservation.status").value("FAILED"))
                .andExpect(jsonPath("$.data.modelObservation.providerName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.modelObservation.modelName").value("local-echo"))
                .andExpect(jsonPath("$.data.modelObservation.routingRuleName").value("wp5-cost-aware"))
                .andExpect(jsonPath("$.data.modelObservation.routingGroup").value("default"))
                .andExpect(jsonPath("$.data.modelObservation.modelCapability").value("JSON"))
                .andExpect(jsonPath("$.data.modelObservation.fallbackUsed").value(true))
                .andExpect(jsonPath("$.data.modelObservation.inputTokens").value(123))
                .andExpect(jsonPath("$.data.modelObservation.outputTokens").value(45))
                .andExpect(jsonPath("$.data.modelObservation.totalCost").value(0.00012345))
                .andExpect(jsonPath("$.data.modelObservation.latencyMs").value(875))
                .andExpect(jsonPath("$.data.modelObservation.errorCode").value("MODEL_TIMEOUT"))
                .andExpect(jsonPath("$.data.modelObservation.errorMessage").value("provider [REDACTED] timed out"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(json, not(containsString("preview-secret")));
        MatcherAssert.assertThat(json, not(containsString("abc.def.ghi")));
        MatcherAssert.assertThat(json, not(containsString("secret-value")));
        MatcherAssert.assertThat(json, not(containsString("requestPreview")));
        MatcherAssert.assertThat(json, not(containsString("responsePreview")));
    }

    @Test
    void storesRedactedContextSummaryAndInputDigest() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(
                userToken,
                "上下文摘要需求",
                "apiKey=sk_live_12345678 登录成功后进入工作台",
                "project-wp5"
        );
        String apiId = createApi(userToken, "project-wp5", "登录接口 token=secret-value", "POST", "/api/wp5/context-login");
        String pageId = createPage(userToken, "project-wp5", "登录页", "/login");
        String flowId = createBusinessFlow(userToken, "project-wp5", "登录主流程");
        String explicitApiId = createApi(userToken, "project-wp5", "密码重置接口 token=explicit-secret", "POST",
                "/api/wp5/context-reset");
        String explicitPageId = createPage(userToken, "project-wp5", "密码重置页", "/reset-password");
        String explicitFlowId = createBusinessFlow(userToken, "project-wp5", "密码重置流程");
        createTraceLink(userToken, requirementId, apiId, pageId, flowId);
        mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "requirementId": "%s",
                                  "title": "历史登录主流程用例",
                                  "description": "人工维护的历史用例",
                                  "source": "MANUAL",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行登录", "expectedResult": "进入工作台"}
                                  ]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated());

        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "上下文摘要任务",
                                  "requirementIds": ["%s"],
                                  "contextApiIds": ["%s"],
                                  "contextPageIds": ["%s"],
                                  "contextFlowIds": ["%s"],
                                  "coverageTypes": ["SMOKE"]
                                }
                                """.formatted(requirementId, explicitApiId, explicitPageId, explicitFlowId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.inputDigest", matchesPattern("[a-f0-9]{64}")))
                .andExpect(jsonPath("$.data.task.requestDigest").doesNotExist())
                .andExpect(jsonPath("$.data.task.contextSummary.contextVersion").value("wp5-context-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.requirements[0].id").value(requirementId))
                .andExpect(jsonPath(
                        "$.data.task.contextSummary.requirements[0].acceptanceCriteriaPreview",
                        containsString("[REDACTED]")
                ))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].requirementId").value(requirementId))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].apiCount").value(1))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].pageCount").value(1))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].flowCount").value(1))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].apis[0].id").value(apiId))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].apis[0].summary",
                        containsString("[REDACTED]")))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].apis[0].method").value("POST"))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].apis[0].path")
                        .value("/api/wp5/context-login"))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].pages[0].id").value(pageId))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].pages[0].urlPattern")
                        .value("/login"))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].flows[0].id").value(flowId))
                .andExpect(jsonPath("$.data.task.contextSummary.linkedAssetsByRequirement[0].flows[0].name")
                        .value("登录主流程"))
                .andExpect(jsonPath("$.data.task.contextSummary.existingCasesByRequirement[0].count").value(1))
                .andExpect(jsonPath(
                        "$.data.task.contextSummary.existingCasesByRequirement[0].cases[0].title"
                ).value("历史登录主流程用例"))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.apiCount").value(1))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.pageCount").value(1))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.flowCount").value(1))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.apiIds[0]").value(explicitApiId))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.pageIds[0]").value(explicitPageId))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.flowIds[0]").value(explicitFlowId))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.apis[0].summary",
                        containsString("[REDACTED]")))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.pages[0].urlPattern")
                        .value("/reset-password"))
                .andExpect(jsonPath("$.data.task.contextSummary.explicitAssets.flows[0].name")
                        .value("密码重置流程"))
                .andExpect(jsonPath("$.data.task.contextSummary.limits.linkedAssetsPerRequirement").value(2))
                .andExpect(jsonPath("$.data.task.contextSummary.limits.explicitAssetsPerType").value(2))
                .andExpect(jsonPath("$.data.task.contextSummary.limits.existingCasesPerRequirement").value(2))
                .andExpect(jsonPath("$.data.task.contextSummary.limits.linkedAssetSchemaChars").value(120))
                .andExpect(jsonPath("$.data.task.contextAssemblyPolicy.policyVersion")
                        .value("wp5-context-assembly-policy-v2"))
                .andExpect(jsonPath("$.data.task.contextAssemblyPolicy.assemblyMode")
                        .value("SNAPSHOT_DIGEST_ONLY"))
                .andExpect(jsonPath("$.data.task.contextAssemblyPolicy.digestStrategy")
                        .value("SHA256_CONTEXT_SUMMARY"))
                .andExpect(jsonPath("$.data.task.contextAssemblyPolicy.inputDigestRequired").value(true))
                .andExpect(jsonPath("$.data.task.contextAssemblyPolicy.rawContextBodyStored").value(false))
                .andExpect(jsonPath("$.data.task.contextAssemblyPolicy.modelPayloadStored").value(false))
                .andExpect(jsonPath("$.data.task.contextAssemblyPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.task.contextPolicyGovernance.policySource").value("PLATFORM_DEFAULT"))
                .andExpect(jsonPath("$.data.task.contextPolicyGovernance.changeApprovalWorkflowReady").value(false))
                .andExpect(jsonPath("$.data.task.contextPolicyOperations.policyVersion")
                        .value("wp5-context-policy-operations-v2"))
                .andExpect(jsonPath("$.data.task.contextPolicyOperations.policyResolutionOrder")
                        .value("PLATFORM_DEFAULT_ONLY"))
                .andExpect(jsonPath("$.data.task.contextPolicyOperations.policyFallbackBehavior")
                        .value("DEPLOY_CONFIG_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.data.task.contextPolicyOperations.approvalStatus")
                        .value("WORKFLOW_NOT_READY"))
                .andExpect(jsonPath("$.data.task.contextPolicyOperations.projectOverrideStoreReady").value(false))
                .andExpect(jsonPath("$.data.task.contextPolicyOperations.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.task.evaluationCorpusPolicy.policyVersion")
                        .value("wp5-evaluation-corpus-policy-v1"))
                .andExpect(jsonPath("$.data.task.evaluationCorpusPolicy.corpusMode")
                        .value("GOLDEN_SET_BASELINE"))
                .andExpect(jsonPath("$.data.task.evaluationCorpusPolicy.qualityGateMode")
                        .value("MANUAL_OPT_IN_AI_EVAL"))
                .andExpect(jsonPath("$.data.task.evaluationCorpusPolicy.qualityEvalScriptReady").value(true))
                .andExpect(jsonPath("$.data.task.evaluationCorpusPolicy.operationsConsoleReady").value(false))
                .andExpect(jsonPath("$.data.task.evaluationCorpusPolicy.corpusRowExported").value(false))
                .andExpect(jsonPath("$.data.task.releaseReadinessPolicy.policyVersion")
                        .value("wp5-release-readiness-policy-v1"))
                .andExpect(jsonPath("$.data.task.releaseReadinessPolicy.decisionMode")
                        .value("ADVISORY_QUALITY_GATE"))
                .andExpect(jsonPath("$.data.task.releaseReadinessPolicy.advisoryOnly").value(true))
                .andExpect(jsonPath("$.data.task.releaseReadinessPolicy.publishBlockingEnabled").value(false))
                .andExpect(jsonPath("$.data.task.releaseReadinessPolicy.approvalWorkflowReady").value(false))
                .andExpect(jsonPath("$.data.task.auditChainPolicy.policyVersion")
                        .value("wp5-audit-chain-policy-v1"))
                .andExpect(jsonPath("$.data.task.auditChainPolicy.chainMode")
                        .value("WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT"))
                .andExpect(jsonPath("$.data.task.auditChainPolicy.wp1AuditEventWritten").value(true))
                .andExpect(jsonPath("$.data.task.auditChainPolicy.crossWpAuditDashboardReady").value(false))
                .andExpect(jsonPath("$.data.task.auditChainPolicy.auditEventDetailExported").value(false))
                .andExpect(jsonPath("$.data.task.auditChainPolicy.traceIdValueExported").value(false))
                .andExpect(jsonPath("$.data.task.archivePolicy.policyVersion")
                        .value("wp5-archive-policy-v1"))
                .andExpect(jsonPath("$.data.task.archivePolicy.retentionDays").value(180))
                .andExpect(jsonPath("$.data.task.archivePolicy.storagePolicy").value("platformManaged"))
                .andExpect(jsonPath("$.data.task.archivePolicy.approvalRequired").value(true))
                .andExpect(jsonPath("$.data.task.archivePolicy.archiveApprovalWorkflowReady").value(false))
                .andExpect(jsonPath("$.data.task.archivePolicy.externalSharingAllowed").value(false))
                .andExpect(jsonPath("$.data.task.archivePolicy.retentionPolicyTracked").value(true))
                .andExpect(jsonPath("$.data.task.archivePolicy.archiveStorageReady").value(false))
                .andExpect(jsonPath("$.data.task.archivePolicy.archivePathExported").value(false))
                .andExpect(jsonPath("$.data.task.archivePolicy.approvalNotesExported").value(false))
                .andExpect(jsonPath("$.data.task.archivePolicy.ticketUrlExported").value(false))
                .andExpect(jsonPath("$.data.task.archivePolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.policyVersion")
                        .value("wp5-report-manifest-policy-v1"))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.manifestMode")
                        .value("AGGREGATE_RECONCILIATION"))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.rowCountTracked").value(true))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.completionStatusTracked").value(true))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.archiveReconciliationReady").value(true))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.detailRowsExported").value(false))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.rowIntegrityValueExported").value(false))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.rowContentSummaryExported").value(false))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.candidateIdentifierListExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.traceIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.auditIdentifierListExported").value(false))
                .andExpect(jsonPath("$.data.task.reportManifestPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.task.contextSummary.policyGovernance.policyVersion")
                        .value("wp5-context-policy-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.policyGovernance.projectOverrideSupported").value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.assemblyPolicy.policyVersion")
                        .value("wp5-context-assembly-policy-v2"))
                .andExpect(jsonPath("$.data.task.contextSummary.assemblyPolicy.assemblyMode")
                        .value("SNAPSHOT_DIGEST_ONLY"))
                .andExpect(jsonPath("$.data.task.contextSummary.assemblyPolicy.digestStrategy")
                        .value("SHA256_CONTEXT_SUMMARY"))
                .andExpect(jsonPath("$.data.task.contextSummary.assemblyPolicy.inputDigestRequired").value(true))
                .andExpect(jsonPath("$.data.task.contextSummary.assemblyPolicy.rawContextBodyStored").value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.assemblyPolicy.modelPayloadStored").value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.assemblyPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.task.contextSummary.policyOperations.policyVersion")
                        .value("wp5-context-policy-operations-v2"))
                .andExpect(jsonPath("$.data.task.contextSummary.policyOperations.policyResolutionOrder")
                        .value("PLATFORM_DEFAULT_ONLY"))
                .andExpect(jsonPath("$.data.task.contextSummary.policyOperations.policyFallbackBehavior")
                        .value("DEPLOY_CONFIG_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.data.task.contextSummary.policyOperations.approvalStatus")
                        .value("WORKFLOW_NOT_READY"))
                .andExpect(jsonPath("$.data.task.contextSummary.policyOperations.projectOverrideStoreReady").value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.policyOperations.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.task.contextSummary.evaluationCorpusPolicy.policyVersion")
                        .value("wp5-evaluation-corpus-policy-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.evaluationCorpusPolicy.qualityGateMode")
                        .value("MANUAL_OPT_IN_AI_EVAL"))
                .andExpect(jsonPath("$.data.task.contextSummary.evaluationCorpusPolicy.candidateBodyExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.releaseReadinessPolicy.policyVersion")
                        .value("wp5-release-readiness-policy-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.releaseReadinessPolicy.decisionMode")
                        .value("ADVISORY_QUALITY_GATE"))
                .andExpect(jsonPath("$.data.task.contextSummary.releaseReadinessPolicy.publishBlockingEnabled")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.releaseReadinessPolicy.candidateEvidenceExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.auditChainPolicy.policyVersion")
                        .value("wp5-audit-chain-policy-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.auditChainPolicy.eventSource")
                        .value("TASK_REVIEW_PUBLISH_MODEL_REFERENCES"))
                .andExpect(jsonPath("$.data.task.contextSummary.auditChainPolicy.auditEventDetailExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.auditChainPolicy.publishIdentifierValueExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.policyVersion")
                        .value("wp5-archive-policy-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.retentionDays").value(180))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.storagePolicy")
                        .value("platformManaged"))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.archiveApprovalWorkflowReady")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.archiveStorageReady")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.archivePathExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.approvalNotesExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.ticketUrlExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.archivePolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.policyVersion")
                        .value("wp5-report-manifest-policy-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.schemaVersion")
                        .value("wp5-task-report-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.fieldSetVersion")
                        .value("aggregate-only-v1"))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.manifestMode")
                        .value("AGGREGATE_RECONCILIATION"))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.rowCountTracked")
                        .value(true))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.detailRowsExported")
                        .value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.rowIntegrityValueExported")
                        .value(false))
                .andExpect(jsonPath(
                        "$.data.task.contextSummary.reportManifestPolicy.candidateIdentifierListExported"
                ).value(false))
                .andExpect(jsonPath("$.data.task.contextSummary.reportManifestPolicy.aggregateOnly").value(true))
                .andReturn();

        MatcherAssert.assertThat(taskResult.getResponse().getContentAsString(), not(containsString("sk_live_12345678")));
        MatcherAssert.assertThat(taskResult.getResponse().getContentAsString(), not(containsString("secret-value")));
        MatcherAssert.assertThat(taskResult.getResponse().getContentAsString(), not(containsString("explicit-secret")));
    }

    @Test
    void rejectsTooManyExplicitContextAssets() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken, "显式上下文上限需求", "显式上下文上限验收", "project-wp5");

        mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "requirementIds": ["%s"],
                                  "contextApiIds": [
                                    "00000000-0000-4000-8000-000000000001",
                                    "00000000-0000-4000-8000-000000000002",
                                    "00000000-0000-4000-8000-000000000003",
                                    "00000000-0000-4000-8000-000000000004",
                                    "00000000-0000-4000-8000-000000000005",
                                    "00000000-0000-4000-8000-000000000006"
                                  ],
                                  "coverageTypes": ["SMOKE"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("contextApiIds 单次最多支持 2 个")));
    }

    @Test
    void enforcesPublishPermissionAndProjectScope() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String developerToken = userAccessToken(List.of("Developer@PROJECT:project-wp5"));
        String deniedToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-other"));
        String requirementId = createRequirement(ownerToken, "权限需求", "权限验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + deniedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportsCandidateCsvByFiltersWithScopeAndRedaction() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:project-wp5"));
        String deniedAuditorToken = userAccessToken(List.of("Auditor@PROJECT:project-other"));
        String requirementId = createRequirement(
                ownerToken,
                "导出安全需求 token=secret-value",
                "验收时不得暴露 apiKey=sk_live_12345678",
                "project-wp5"
        );
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        saveCandidateStatus(candidateId, "FAILED", null, "发布失败 token=secret-value rawPrompt promptPlaintext");

        MvcResult export = mockMvc.perform(get("/api/v1/test-design/candidates/export")
                        .header("Authorization", "Bearer " + auditorToken)
                        .param("taskId", taskId)
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("wp5-candidates.csv")))
                .andReturn();

        String csv = export.getResponse().getContentAsString();
        MatcherAssert.assertThat(csv, startsWith("recordType,metric,value,taskId,projectId,candidateId"));
        MatcherAssert.assertThat(csv, containsString("summary,totalMatched,1,"));
        MatcherAssert.assertThat(csv, containsString("candidate,,,"));
        MatcherAssert.assertThat(csv, containsString(taskId));
        MatcherAssert.assertThat(csv, containsString(candidateId));
        MatcherAssert.assertThat(csv, containsString("project-wp5"));
        MatcherAssert.assertThat(csv, containsString("[REDACTED]"));
        MatcherAssert.assertThat(csv, not(containsString("secret-value")));
        MatcherAssert.assertThat(csv, not(containsString("sk_live_12345678")));
        MatcherAssert.assertThat(csv, not(containsString("rawPrompt")));
        MatcherAssert.assertThat(csv, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(csv, not(containsString("基于 WP3 需求生成")));
        MatcherAssert.assertThat(csv, not(containsString("准备满足前置条件的基础测试数据")));
        MatcherAssert.assertThat(csv, not(containsString("需求验收标准已明确")));

        mockMvc.perform(get("/api/v1/test-design/candidates/export")
                        .header("Authorization", "Bearer " + deniedAuditorToken)
                        .param("taskId", taskId)
                        .param("projectId", "project-other"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/test-design/candidates/export")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("taskId", taskId))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCandidateCsvExportWithoutExplicitScope() throws Exception {
        String adminToken = userAccessToken(List.of("PlatformAdmin@PLATFORM:all"));

        mockMvc.perform(get("/api/v1/test-design/candidates/export")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("taskId 或 projectId")));
    }

    @Test
    void exposesAndExportsReviewRecordsWithScopeAndRedaction() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:project-wp5"));
        String deniedAuditorToken = userAccessToken(List.of("Auditor@PROJECT:project-other"));
        String requirementId = createRequirement(ownerToken, "评审历史需求", "评审历史验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        MvcResult updated = mockMvc.perform(put("/api/v1/test-design/candidates/{id}", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "编辑后的评审历史冒烟用例",
                                  "description": "人工补充候选用例，不应进入评审历史导出",
                                  "coverageType": "SMOKE",
                                  "priority": "HIGH",
                                  "preconditions": "评审历史前置条件不应导出",
                                  "expectedResult": "评审历史结果不应导出",
                                  "tags": ["wp5", "review-record"],
                                  "version": %d,
                                  "steps": [
                                    {"action": "输入评审历史账号", "expectedResult": "账号通过校验"},
                                    {"action": "提交评审历史表单", "expectedResult": "评审历史结果不应导出"}
                                  ]
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EDITED"))
                .andReturn();
        Integer updatedVersion = JsonPath.read(updated.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "comment": "可以发布 token=secret-value rawPrompt promptPlaintext"}
                                """.formatted(updatedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult query = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/review-records", taskId)
                        .header("Authorization", "Bearer " + auditorToken)
                        .param("index", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andReturn();

        String reviewRecordsJson = query.getResponse().getContentAsString();
        List<String> actions = JsonPath.read(reviewRecordsJson, "$.data.items[*].action");
        MatcherAssert.assertThat(actions, containsInAnyOrder("UPDATE", "CONFIRMED"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("\"hasComment\":true"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("[REDACTED]"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("\"versionBefore\":0"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("\"versionAfter\":1"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("\"versionBefore\":1"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("\"versionAfter\":2"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("title"));
        MatcherAssert.assertThat(reviewRecordsJson, containsString("status"));
        MatcherAssert.assertThat(reviewRecordsJson, not(containsString("diffJson")));
        MatcherAssert.assertThat(reviewRecordsJson, not(containsString("secret-value")));
        MatcherAssert.assertThat(reviewRecordsJson, not(containsString("rawPrompt")));
        MatcherAssert.assertThat(reviewRecordsJson, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(reviewRecordsJson, not(containsString("人工补充候选用例")));
        MatcherAssert.assertThat(reviewRecordsJson, not(containsString("输入评审历史账号")));

        MvcResult export = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/review-records/export", taskId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("wp5-review-records.csv")))
                .andReturn();

        String csv = export.getResponse().getContentAsString();
        MatcherAssert.assertThat(csv, startsWith("recordType,metric,value,taskId,projectId,reviewRecordId"));
        MatcherAssert.assertThat(csv, containsString("summary,totalMatched,2,"));
        MatcherAssert.assertThat(csv, containsString("reviewRecord,,,"));
        MatcherAssert.assertThat(csv, containsString(taskId));
        MatcherAssert.assertThat(csv, containsString(candidateId));
        MatcherAssert.assertThat(csv, containsString("project-wp5"));
        MatcherAssert.assertThat(csv, containsString("UPDATE"));
        MatcherAssert.assertThat(csv, containsString("CONFIRMED"));
        MatcherAssert.assertThat(csv, containsString("title|description|preconditions|steps|expectedResult|tags|status|version"));
        MatcherAssert.assertThat(csv, containsString("status|version"));
        MatcherAssert.assertThat(csv, not(containsString("secret-value")));
        MatcherAssert.assertThat(csv, not(containsString("rawPrompt")));
        MatcherAssert.assertThat(csv, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(csv, not(containsString("diffJson")));
        MatcherAssert.assertThat(csv, not(containsString("可以发布")));
        MatcherAssert.assertThat(csv, not(containsString("人工补充候选用例")));
        MatcherAssert.assertThat(csv, not(containsString("评审历史前置条件")));
        MatcherAssert.assertThat(csv, not(containsString("输入评审历史账号")));
        MatcherAssert.assertThat(csv, not(containsString("评审历史结果不应导出")));

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/review-records", taskId)
                        .header("Authorization", "Bearer " + deniedAuditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/review-records/export", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportsFullTaskReportWithScopeAndAggregateRedaction() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:project-wp5"));
        String deniedAuditorToken = userAccessToken(List.of("Auditor@PROJECT:project-other"));
        UUID invocationId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant modelObservedAt = Instant.parse("2026-05-29T09:00:00Z");
        String requirementId = createRequirement(
                ownerToken,
                "任务报告需求 token=secret-value",
                "任务报告验收 apiKey=sk_live_12345678",
                "project-wp5"
        );
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE","EXCEPTION"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        modelAccessRepository.saveInvocation(new InvocationRecord(
                invocationId,
                "project-wp5",
                null,
                null,
                "INTERNAL",
                "wp5-test-design-v1",
                1,
                providerId,
                "local-echo-primary",
                "local-echo",
                "wp5-cost-aware",
                "default",
                "JSON",
                InvocationStatus.FAILED,
                true,
                "sha256:prompt",
                "apiKey=preview-secret should never leave WP2",
                "Bearer abc.def.ghi should never leave WP2",
                123,
                45,
                new BigDecimal("0.00012345"),
                "MODEL_TIMEOUT",
                "provider token=secret-value rawPrompt timed out",
                875,
                "wp5-test-design",
                "qa.lead",
                modelObservedAt
        ));
        modelInvocationJobRepository.save(new ModelInvocationJobRecord(
                jobId,
                ModelInvocationJobStatus.FAILED,
                "{}",
                "wp5-test-design",
                "qa.lead",
                "trc_wp5_task_report",
                modelObservedAt.minusSeconds(2),
                modelObservedAt.minusSeconds(1),
                modelObservedAt,
                invocationId,
                "MODEL_TIMEOUT",
                "job token=secret-value should stay inside WP2",
                "{\"invocationId\":\"%s\"}".formatted(invocationId)
        ));
        TestDesignTask currentTask = testDesignRepository.task(UUID.fromString(taskId)).orElseThrow();
        testDesignRepository.saveTask(new TestDesignTask(
                currentTask.id(),
                currentTask.projectId(),
                currentTask.title(),
                currentTask.status(),
                currentTask.requirementIds(),
                currentTask.coverageTypes(),
                currentTask.promptKey(),
                currentTask.promptVersion(),
                invocationId,
                "local-echo-primary",
                "local-echo",
                currentTask.totalRequirements(),
                currentTask.generatedCount(),
                currentTask.confirmedCount(),
                currentTask.publishedCount(),
                currentTask.errorMessage(),
                currentTask.requestedBy(),
                currentTask.idempotencyKey(),
                currentTask.requestDigest(),
                currentTask.inputDigest(),
                currentTask.contextSummaryJson(),
                currentTask.createdAt(),
                currentTask.updatedAt()
        ));
        String firstCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer firstVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");
        String secondCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].id");
        Integer secondVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].version");

        MvcResult updated = mockMvc.perform(put("/api/v1/test-design/candidates/{id}", firstCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "任务报告聚合用例",
                                  "description": "报告不应导出候选正文",
                                  "coverageType": "SMOKE",
                                  "priority": "HIGH",
                                  "preconditions": "任务报告前置条件不应导出",
                                  "expectedResult": "任务报告结果不应导出",
                                  "tags": ["wp5", "task-report"],
                                  "version": %d,
                                  "steps": [
                                    {"action": "任务报告步骤不应导出", "expectedResult": "步骤结果不应导出"},
                                    {"action": "提交任务报告表单", "expectedResult": "表单提交成功"}
                                  ]
                                }
                                """.formatted(firstVersion)))
                .andExpect(status().isOk())
                .andReturn();
        Integer updatedVersion = JsonPath.read(updated.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", firstCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "comment": "确认意见 token=secret-value rawPrompt promptPlaintext"}
                                """.formatted(updatedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/reject", secondCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "reason": "驳回原因 token=secret-value", "comment": "驳回评论 rawPrompt"}
                                """.formatted(secondVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        MvcResult export = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/report/export", taskId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("wp5-task-report.csv")))
                .andReturn();

        String csv = export.getResponse().getContentAsString();
        MatcherAssert.assertThat(csv, startsWith("recordType,section,metric,label,value,percent,tone,taskId"));
        MatcherAssert.assertThat(csv, containsString("WP5_TASK_REPORT_FULL"));
        MatcherAssert.assertThat(csv, containsString("fullTask"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,policyVersion,,wp5-generation-orchestration-policy-v1"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,orchestrationMode,,SYNC_INLINE_GENERATION"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,asyncGenerationEnabled,,false"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,conditionalRunClaimSupported,,true"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,idempotentCreateReplaySupported,,true"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,duplicateEventReplaySafe,,true"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,eventRecoveryEnabled,,true"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,queuedEventReplaySupported,,false"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,runningTimeoutRecoveryEnabled,,true"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,explicitRetryRequiredAfterTimeout,,true"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,manualTaskRetrySupported,,true"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,manualQueuedEventReplayReady,,false"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,queueLagMetricReady,,false"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,timeoutAlertReady,,false"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,multiInstanceLoadTestEvidenceReady,,false"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,eventPayloadExported,,false"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,eventIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,queueMessageBodyExported,,false"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,recoveryDetailRowsExported,,false"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,metric,effectiveRecoveryBatchSize,100,,info"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,metric,runningTimeoutSeconds,600,,info"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,metric,queuedStatusSignal,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,metric,runningStatusSignal,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString(
                "generationOrchestrationPolicy,metric,timeoutFailureSignal,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString("generationOrchestrationPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("candidateQuality,distribution:status,PUBLISHED"));
        MatcherAssert.assertThat(csv, containsString("candidateQuality,distribution:status,REJECTED"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,policyVersion,,wp5-quality-readiness-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,thresholdSource,,DEPLOY_CONFIG"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,advisoryOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,publishBlockingEnabled,,false"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,readinessStatus,,PASSED"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,metric,blockingCount,0"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,metric,warningCount,0"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,checkStatus,stepComplete,PASSED"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,currentValue,stepComplete,100.0"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,thresholdValue,stepComplete,100.0"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,unit,stepComplete,PERCENT"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,severity,stepComplete,BLOCKING"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,checkStatus,lowConfidence,PASSED"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,thresholdValue,lowConfidence,20.0"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,thresholdValue,duplicateKeyCollision,0.0"));
        MatcherAssert.assertThat(csv, containsString("readinessPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString(
                "releaseReadinessPolicy,policyVersion,,wp5-release-readiness-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,decisionMode,,ADVISORY_QUALITY_GATE"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,thresholdSource,,DEPLOY_CONFIG"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,qualityThresholdEvaluated,,true"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,advisoryOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,publishBlockingEnabled,,false"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,approvalWorkflowReady,,false"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,autoPublishAllowed,,false"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,confirmedCandidateRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,candidateEvidenceExported,,false"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,approvalNotesExported,,false"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,thresholdRuleDetailExported,,false"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,metric,readinessStatus,PASSED"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,metric,blockingCount,0"));
        MatcherAssert.assertThat(csv, containsString("releaseReadinessPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("reviewHistory,distribution:action,UPDATE"));
        MatcherAssert.assertThat(csv, containsString("reviewHistory,distribution:action,CONFIRMED"));
        MatcherAssert.assertThat(csv, containsString("feedbackLoop,metric,promptTuningSignals,2,66.67,info"));
        MatcherAssert.assertThat(csv, containsString("feedbackLoop,metric,sampleCandidates,2,66.67"));
        MatcherAssert.assertThat(csv, containsString("feedbackLoop,distribution:signal,correction,1,33.33,info"));
        MatcherAssert.assertThat(csv, containsString("feedbackLoop,distribution:signal,rejected,1,33.33,warning"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,policyVersion,,wp5-prompt-calibration-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,sampleSource,,HUMAN_FEEDBACK_AGGREGATE"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,calibrationStatus,,AGGREGATE_SIGNALS_ONLY"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,metric,feedbackSignalsTracked,2,,info"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,metric,sampleCandidatesTracked,2,,info"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,metric,sampleExplanationCount,1,,info"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,sampleSetMaintenanceWorkflowReady,,false"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,longTermCalibrationBaselineReady,,false"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,sampleDetailRowsExported,,false"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,candidateBodyExported,,false"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,reviewTextExported,,false"));
        MatcherAssert.assertThat(csv, containsString("promptCalibrationPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("publish,distribution:result,SUCCEEDED"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,policyVersion,,wp5-publish-compensation-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,replayKeyFamily,,AI_GENERATED_CASE_KEY"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,idempotentReplaySupported,,true"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,partialTraceLinkRepairSupported,,true"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,failedCandidateRetrySupported,,true"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,manualConflictLinkSupported,,true"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,asyncCompensationBackendReady,,false"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,crossWpTransactionOrchestrationReady,,false"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,candidateEvidenceExported,,false"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,errorTextExported,,false"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,caseIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,traceDetailListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,metric,retryLinkExistingCount,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,metric,linkExistingCount,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,metric,manualLinkExistingCount,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,metric,conflictCount,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,metric,failedCount,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString("publishCompensationPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,traceIdTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,jobIdTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,status,,FAILED"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,providerName,,local-echo-primary"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,modelName,,local-echo"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,routingRuleName,,wp5-cost-aware"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,routingGroup,,default"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,modelCapability,,JSON"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,fallbackUsed,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,inputTokens,,123"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,outputTokens,,45"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,totalCost,,0.00012345"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,latencyMs,,875"));
        MatcherAssert.assertThat(csv, containsString("modelObservation,errorCode,,MODEL_TIMEOUT"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,policyVersion,,wp5-model-observation-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,observationMode,,ROUTING_COST_LATENCY_AGGREGATE"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,wp2InvocationReferenceTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,traceIdTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,jobIdTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,routingMetadataTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,tokenUsageTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,latencyTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,costTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,fallbackTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,promptPayloadStored,,false"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,payloadPreviewExported,,false"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,traceIdValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,jobIdValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,invocationIdValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,providerErrorTextExported,,false"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,metric,routingMetadataFieldCount,5,,info"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,metric,tokenUsageMetricCount,2,,info"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,metric,costMetricCount,1,,info"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,metric,latencyMetricCount,1,,info"));
        MatcherAssert.assertThat(csv, containsString("modelObservationPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("context,contextVersion,,wp5-context-v1"));
        MatcherAssert.assertThat(csv, containsString("context,requirementCount,,1"));
        MatcherAssert.assertThat(csv, containsString("contextPolicy,linkedAssetsPerRequirement,,2"));
        MatcherAssert.assertThat(csv, containsString("contextPolicy,explicitAssetsPerType,,2"));
        MatcherAssert.assertThat(csv, containsString("contextPolicy,requirementDescriptionChars,,180"));
        MatcherAssert.assertThat(csv, containsString("contextPolicy,linkedAssetSchemaChars,,120"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,policyVersion,,wp5-context-assembly-policy-v2"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,assemblyMode,,SNAPSHOT_DIGEST_ONLY"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,digestStrategy,,SHA256_CONTEXT_SUMMARY"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,inputDigestRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,inputDigestTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,persistedContextSummaryOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,wp3ApplicationServiceOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,rawContextBodyStored,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,modelPayloadStored,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,digestValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,requirementBodyExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,assetSchemaExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,pageTreeExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,flowJsonExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,explicitAssetIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,historicalCaseStepExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,metric,requirementSnapshotCount,1,,info"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,metric,linkedAssetSnapshotGroupCount,1,,info"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,metric,existingCaseSnapshotGroupCount,1,,info"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,metric,explicitAssetTypeCount,0,,neutral"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,metric,clippingLimitCount,6,,info"));
        MatcherAssert.assertThat(csv, containsString("contextAssemblyPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,policyVersion,,wp5-context-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,policySource,,PLATFORM_DEFAULT"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,governanceStatus,,PLATFORM_DEFAULT_ONLY"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,projectOverrideSupported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,environmentOverrideSupported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,changeApprovalRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,changeApprovalWorkflowReady,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyGovernance,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,policyVersion,,wp5-context-policy-operations-v2"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,operationMode,,PLATFORM_DEFAULT_ONLY"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,policyResolutionOrder,,PLATFORM_DEFAULT_ONLY"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,policyFallbackBehavior,,DEPLOY_CONFIG_CHANGE_REQUIRED"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,approvalStatus,,WORKFLOW_NOT_READY"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,projectOverrideStoreReady,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,environmentOverrideStoreReady,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,changeApprovalWorkflowReady,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,effectivePolicySnapshotMaterialized,,true"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,policyDiffPreviewExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,approvalNotesExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,ticketUrlExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,projectOverrideRulesExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,environmentOverrideRulesExported,,false"));
        MatcherAssert.assertThat(csv, containsString("contextPolicyOperations,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,policyVersion,,wp5-scope-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,scopeModel,,PROJECT_RESOURCE_SCOPE"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,listFallbackScope,,PLATFORM_WHEN_PROJECT_FILTER_ABSENT"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,taskProjectScopeRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,candidateProjectScopeRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,batchCandidateProjectScopeRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,publishProjectScopeRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,asyncTaskProjectScopeRecovered,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,smokeProjectScopeRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,evaluationCorpusProjectIsolated,,true"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,evaluationCorpusOperationsReady,,false"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,crossWpScopeDashboardReady,,false"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,candidateIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,roleRuleDetailExported,,false"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,serviceTokenValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("scopePolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString(
                "evaluationCorpusPolicy,policyVersion,,wp5-evaluation-corpus-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,corpusMode,,GOLDEN_SET_BASELINE"));
        MatcherAssert.assertThat(csv, containsString(
                "evaluationCorpusPolicy,qualityGateMode,,MANUAL_OPT_IN_AI_EVAL"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,thresholdSource,,DEPLOY_CONFIG"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,projectScopeRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,goldenSetBaselineRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,qualityEvalScriptReady,,true"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,qualityGateIntegrated,,true"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,readinessDistributionTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,promptVersionTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,evaluationCorpusProjectIsolated,,true"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,sampleMaintenanceReady,,false"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,longTermCalibrationReady,,false"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,operationsConsoleReady,,false"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,corpusRowExported,,false"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,candidateBodyExported,,false"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,reviewCommentExported,,false"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,promptBodyExported,,false"));
        MatcherAssert.assertThat(csv, containsString("evaluationCorpusPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,policyVersion,,wp5-audit-chain-policy-v1"));
        MatcherAssert.assertThat(csv, containsString(
                "auditChainPolicy,chainMode,,WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT"));
        MatcherAssert.assertThat(csv, containsString(
                "auditChainPolicy,eventSource,,TASK_REVIEW_PUBLISH_MODEL_REFERENCES"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,wp1AuditEventWritten,,true"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,wp2InvocationReferenceTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,wp3PublishReferenceTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,wp5DomainEventsTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,projectScopeRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,crossWpAuditDashboardReady,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,auditOutboxReplayDashboardReady,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,auditEventDetailExported,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,candidateIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,platformAuditIdentifierExported,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,traceIdValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,modelInvocationIdValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,publishIdentifierValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,metric,taskEventCount,1"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,metric,reviewEventCount,3"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,metric,publishEventCount,1"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,metric,noteCoverageCount,2"));
        MatcherAssert.assertThat(csv, containsString("auditChainPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("exportGovernance,fieldPolicy,,aggregateOnly"));
        MatcherAssert.assertThat(csv, containsString("exportGovernance,candidateBodyAllowed,,false"));
        MatcherAssert.assertThat(csv, containsString("exportGovernance,reviewCommentAllowed,,false"));
        MatcherAssert.assertThat(csv, containsString("exportGovernance,safetyScan,,PASSED"));
        MatcherAssert.assertThat(csv, containsString("auditPolicy,exportAction,,EXPORT"));
        MatcherAssert.assertThat(csv, containsString("auditPolicy,resourceType,,TEST_DESIGN_TASK_REPORT"));
        MatcherAssert.assertThat(csv, containsString("auditPolicy,scopeType,,PROJECT"));
        MatcherAssert.assertThat(csv, containsString("auditPolicy,auditEventWritten,,true"));
        MatcherAssert.assertThat(csv, containsString("auditPolicy,auditDetailsExported,,false"));
        MatcherAssert.assertThat(csv, containsString("safetyScanPolicy,mode,,failClosed"));
        MatcherAssert.assertThat(csv, containsString("safetyScanPolicy,sensitiveTextPatternScan,,true"));
        MatcherAssert.assertThat(csv, containsString("safetyScanPolicy,rawPayloadMarkerScan,,true"));
        MatcherAssert.assertThat(csv, containsString("safetyScanPolicy,requestResponsePreviewScan,,true"));
        MatcherAssert.assertThat(csv, containsString("safetyScanPolicy,findingDetailsExported,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,retentionDays,,180"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,storagePolicy,,platformManaged"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,approvalRequired,,true"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,archiveApprovalWorkflowReady,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,externalSharingAllowed,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,retentionPolicyTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,archiveStorageReady,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,archivePathExported,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,archiveNotesExported,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,approvalNotesExported,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,ticketUrlExported,,false"));
        MatcherAssert.assertThat(csv, containsString("archivePolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString(
                "reportManifestPolicy,policyVersion,,wp5-report-manifest-policy-v1"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,schemaVersion,,wp5-task-report-v1"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,fieldSetVersion,,aggregate-only-v1"));
        MatcherAssert.assertThat(csv, containsString(
                "reportManifestPolicy,manifestMode,,AGGREGATE_RECONCILIATION"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,rowCountTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,completionStatusTracked,,true"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,archiveReconciliationReady,,true"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,detailRowsExported,,false"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,rowIntegrityValueExported,,false"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,rowContentSummaryExported,,false"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,candidateIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,traceIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,auditIdentifierListExported,,false"));
        MatcherAssert.assertThat(csv, containsString("reportManifestPolicy,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("reportManifest,schemaVersion,,wp5-task-report-v1"));
        MatcherAssert.assertThat(csv, containsString("reportManifest,fieldSetVersion,,aggregate-only-v1"));
        MatcherAssert.assertThat(csv, containsString("reportManifest,rowCountBeforeManifest,,"));
        MatcherAssert.assertThat(csv, containsString("reportManifest,aggregateOnly,,true"));
        MatcherAssert.assertThat(csv, containsString("reportManifest,detailRowsExported,,false"));
        MatcherAssert.assertThat(csv, containsString("reportManifest,manifestStatus,,COMPLETE"));
        MatcherAssert.assertThat(csv, containsString(taskId));
        MatcherAssert.assertThat(csv, containsString("project-wp5"));
        MatcherAssert.assertThat(csv, not(containsString("secret-value")));
        MatcherAssert.assertThat(csv, not(containsString("sk_live_12345678")));
        MatcherAssert.assertThat(csv, not(containsString("rawPrompt")));
        MatcherAssert.assertThat(csv, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(csv, not(containsString(invocationId.toString())));
        MatcherAssert.assertThat(csv, not(containsString(jobId.toString())));
        MatcherAssert.assertThat(csv, not(containsString("trc_wp5_task_report")));
        MatcherAssert.assertThat(csv, not(containsString("preview-secret")));
        MatcherAssert.assertThat(csv, not(containsString("abc.def.ghi")));
        MatcherAssert.assertThat(csv, not(containsString("provider token")));
        MatcherAssert.assertThat(csv, not(containsString("job token")));
        MatcherAssert.assertThat(csv, not(containsString("auditLogId")));
        MatcherAssert.assertThat(csv, not(containsString("afterJson")));
        MatcherAssert.assertThat(csv, not(containsString("candidateIds")));
        MatcherAssert.assertThat(csv, not(containsString("assetCaseIds")));
        MatcherAssert.assertThat(csv, not(containsString("sourceRef")));
        MatcherAssert.assertThat(csv, not(containsString("apiIds")));
        MatcherAssert.assertThat(csv, not(containsString("componentTreePreview")));
        MatcherAssert.assertThat(csv, not(containsString("flowJsonPreview")));
        MatcherAssert.assertThat(csv, not(containsString("requestSchemaPreview")));
        MatcherAssert.assertThat(csv, not(containsString("sampleCandidateIds")));
        MatcherAssert.assertThat(csv, not(containsString("reviewComments")));
        MatcherAssert.assertThat(csv, not(containsString("traceIds")));
        MatcherAssert.assertThat(csv, not(containsString("rowDigest")));
        MatcherAssert.assertThat(csv, not(containsString("projectOverrideRuleBody")));
        MatcherAssert.assertThat(csv, not(containsString("environmentOverrideRuleBody")));
        MatcherAssert.assertThat(csv, not(containsString("approval-note-text")));
        MatcherAssert.assertThat(csv, not(containsString("https://ticket.example")));
        MatcherAssert.assertThat(csv, not(containsString("policyDocument")));
        MatcherAssert.assertThat(csv, not(containsString("任务报告步骤不应导出")));
        MatcherAssert.assertThat(csv, not(containsString("步骤结果不应导出")));
        MatcherAssert.assertThat(csv, not(containsString("任务报告前置条件不应导出")));
        MatcherAssert.assertThat(csv, not(containsString("任务报告结果不应导出")));
        MatcherAssert.assertThat(csv, not(containsString("确认意见")));
        MatcherAssert.assertThat(csv, not(containsString("驳回原因")));

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/report/export", taskId)
                        .header("Authorization", "Bearer " + deniedAuditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/report/export", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void summarizesTaskAuditChainWithoutRawText() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:project-wp5"));
        String deniedAuditorToken = userAccessToken(List.of("Auditor@PROJECT:project-other"));
        String requirementId = createRequirement(
                ownerToken,
                "审计链需求 token=secret-value",
                "审计链验收 rawPrompt",
                "project-wp5"
        );
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE","EXCEPTION"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String firstCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer firstVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");
        String secondCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].id");
        Integer secondVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].version");

        MvcResult updated = mockMvc.perform(put("/api/v1/test-design/candidates/{id}", firstCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "审计链人工修正用例",
                                  "description": "候选正文不应出现在审计链摘要",
                                  "coverageType": "SMOKE",
                                  "priority": "HIGH",
                                  "expectedResult": "审计链结果不应导出",
                                  "tags": ["wp5", "audit-summary"],
                                  "version": %d,
                                  "steps": [
                                    {"action": "审计链步骤不应导出", "expectedResult": "步骤结果不应导出"},
                                    {"action": "提交审计链表单", "expectedResult": "审计链提交成功"}
                                  ]
                                }
                                """.formatted(firstVersion)))
                .andExpect(status().isOk())
                .andReturn();
        Integer updatedVersion = JsonPath.read(updated.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", firstCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "comment": "审计链确认说明 token=secret-value rawPrompt"}
                                """.formatted(updatedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/reject", secondCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "reason": "审计链驳回原因 secret-value", "comment": "审计链驳回说明 promptPlaintext"}
                                """.formatted(secondVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].result").value("PLANNED"));

        MvcResult summary = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/report/audit-summary", taskId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.projectId").value("project-wp5"))
                .andExpect(jsonPath("$.data.reviewRecordCount").value(3))
                .andExpect(jsonPath("$.data.publishRecordCount").value(0))
                .andExpect(jsonPath("$.data.eventCount").value(4))
                .andExpect(jsonPath("$.data.noteCoverageCount").value(2))
                .andExpect(jsonPath("$.data.metrics[0].code").value("eventCount"))
                .andExpect(jsonPath("$.data.recentEvents[0].source").value("REVIEW"))
                .andReturn();

        String json = summary.getResponse().getContentAsString();
        MatcherAssert.assertThat(json, containsString("UPDATE"));
        MatcherAssert.assertThat(json, containsString("CONFIRMED"));
        MatcherAssert.assertThat(json, containsString("REJECTED"));
        MatcherAssert.assertThat(json, containsString(firstCandidateId));
        MatcherAssert.assertThat(json, not(containsString("secret-value")));
        MatcherAssert.assertThat(json, not(containsString("rawPrompt")));
        MatcherAssert.assertThat(json, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(json, not(containsString("审计链确认说明")));
        MatcherAssert.assertThat(json, not(containsString("审计链驳回原因")));
        MatcherAssert.assertThat(json, not(containsString("候选正文不应出现在审计链摘要")));
        MatcherAssert.assertThat(json, not(containsString("审计链步骤不应导出")));

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/report/audit-summary", taskId)
                        .header("Authorization", "Bearer " + deniedAuditorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesFullTaskQualitySummaryWithScopeAndAggregateRedaction() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:project-wp5"));
        String deniedAuditorToken = userAccessToken(List.of("Auditor@PROJECT:project-other"));
        String requirementId = createRequirement(
                ownerToken,
                "质量摘要需求 token=secret-value",
                "质量摘要验收 apiKey=sk_live_12345678",
                "project-wp5"
        );
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE","BOUNDARY"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String firstCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer firstVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");
        String secondCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].id");
        Integer secondVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", firstCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "comment": "确认评论 token=secret-value rawPrompt promptPlaintext"}
                                """.formatted(firstVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/reject", secondCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "reason": "驳回原因 token=secret-value", "comment": "驳回评论 rawPrompt"}
                                """.formatted(secondVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        MvcResult result = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/quality/summary", taskId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.projectId").value("project-wp5"))
                .andExpect(jsonPath("$.data.scope").value("fullTask"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.publishableCount").value(1))
                .andExpect(jsonPath("$.data.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.publishedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.stepCompleteCount").value(2))
                .andExpect(jsonPath("$.data.expectedCompleteCount").value(2))
                .andExpect(jsonPath("$.data.lowConfidenceCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.readiness.status").value("WARNING"))
                .andExpect(jsonPath("$.data.readiness.blockingCount").value(0))
                .andExpect(jsonPath("$.data.readiness.warningCount").value(1))
                .andExpect(jsonPath("$.data.readiness.checks[?(@.code == 'lowConfidence')].status").value(contains("FAILED")))
                .andExpect(jsonPath("$.data.readiness.checks[?(@.code == 'lowConfidence')].severity").value(contains("WARNING")))
                .andExpect(jsonPath("$.data.readiness.checks[?(@.code == 'stepComplete')].thresholdValue").value(contains(100.0)))
                .andExpect(jsonPath("$.data.metrics[?(@.code == 'publishable')].count").value(hasSize(1)))
                .andExpect(jsonPath("$.data.distributions.status[?(@.label == 'CONFIRMED')].count").value(hasSize(1)))
                .andExpect(jsonPath("$.data.distributions.status[?(@.label == 'REJECTED')].count").value(hasSize(1)))
                .andExpect(jsonPath("$.data.distributions.coverageType[?(@.label == 'SMOKE')].percent").value(hasSize(1)))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(json, not(containsString("secret-value")));
        MatcherAssert.assertThat(json, not(containsString("sk_live_12345678")));
        MatcherAssert.assertThat(json, not(containsString("rawPrompt")));
        MatcherAssert.assertThat(json, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(json, not(containsString("确认评论")));
        MatcherAssert.assertThat(json, not(containsString("驳回原因")));
        MatcherAssert.assertThat(json, not(containsString("基于 WP3 需求生成")));
        MatcherAssert.assertThat(json, not(containsString("准备满足前置条件的基础测试数据")));

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}/quality/summary", taskId)
                        .header("Authorization", "Bearer " + deniedAuditorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void summarizesPromptVersionTrendWithoutRawText() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:project-wp5"));
        String deniedAuditorToken = userAccessToken(List.of("Auditor@PROJECT:project-other"));
        String requirementId = createRequirement(
                ownerToken,
                "Prompt 趋势需求 token=secret-value",
                "趋势验收 apiKey=sk_live_12345678",
                "project-wp5"
        );
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE","BOUNDARY"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String firstCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer firstVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");
        String secondCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].id");
        Integer secondVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].version");

        mockMvc.perform(put("/api/v1/test-design/candidates/{id}", firstCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Prompt 趋势人工修正",
                                  "coverageType": "SMOKE",
                                  "priority": "HIGH",
                                  "expectedResult": "趋势结果可比较",
                                  "version": %d,
                                  "steps": [
                                    {"action": "执行生成", "expectedResult": "候选质量可计算"},
                                    {"action": "核对趋势摘要", "expectedResult": "版本指标可比较"}
                                  ]
                                }
                                """.formatted(firstVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EDITED"));
        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/reject", secondCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "reason": "质量不足 token=secret-value", "comment": "rawPrompt 不应出现在趋势"}
                                """.formatted(secondVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        MvcResult trend = mockMvc.perform(get("/api/v1/test-design/quality/prompt-trend")
                        .header("Authorization", "Bearer " + auditorToken)
                        .param("projectId", "project-wp5")
                        .param("promptKey", "wp5-test-design-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value("project-wp5"))
                .andExpect(jsonPath("$.data.promptKey").value("wp5-test-design-v1"))
                .andExpect(jsonPath("$.data.taskCount").value(1))
                .andExpect(jsonPath("$.data.candidateCount").value(2))
                .andExpect(jsonPath("$.data.readinessDistribution[0].label").value("WARNING"))
                .andExpect(jsonPath("$.data.readinessDistribution[0].count").value(1))
                .andExpect(jsonPath("$.data.readinessDistribution[0].percent").value(100.0))
                .andExpect(jsonPath("$.data.buckets[0].promptKey").value("wp5-test-design-v1"))
                .andExpect(jsonPath("$.data.buckets[0].promptVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.buckets[0].taskCount").value(1))
                .andExpect(jsonPath("$.data.buckets[0].candidateCount").value(2))
                .andExpect(jsonPath("$.data.buckets[0].stepCompletePercent").value(100.0))
                .andExpect(jsonPath("$.data.buckets[0].expectedCompletePercent").value(100.0))
                .andExpect(jsonPath("$.data.buckets[0].lowConfidenceCount").value(1))
                .andExpect(jsonPath("$.data.buckets[0].correctionCount").value(1))
                .andExpect(jsonPath("$.data.buckets[0].rejectedCount").value(1))
                .andExpect(jsonPath("$.data.buckets[0].readiness.status").value("WARNING"))
                .andExpect(jsonPath("$.data.buckets[0].readiness.blockingCount").value(0))
                .andExpect(jsonPath("$.data.buckets[0].readiness.warningCount").value(1))
                .andExpect(jsonPath("$.data.buckets[0].readiness.checks[?(@.code == 'lowConfidence')].status").value(contains("FAILED")))
                .andExpect(jsonPath("$.data.buckets[0].readiness.checks[?(@.code == 'errorPresent')].status").value(contains("PASSED")))
                .andReturn();

        String json = trend.getResponse().getContentAsString();
        MatcherAssert.assertThat(json, not(containsString(taskId)));
        MatcherAssert.assertThat(json, not(containsString(firstCandidateId)));
        MatcherAssert.assertThat(json, not(containsString(secondCandidateId)));
        MatcherAssert.assertThat(json, not(containsString("secret-value")));
        MatcherAssert.assertThat(json, not(containsString("sk_live_12345678")));
        MatcherAssert.assertThat(json, not(containsString("rawPrompt")));
        MatcherAssert.assertThat(json, not(containsString("Prompt 趋势人工修正")));

        mockMvc.perform(get("/api/v1/test-design/quality/prompt-trend")
                        .header("Authorization", "Bearer " + deniedAuditorToken)
                        .param("projectId", "project-wp5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void linksExistingWp3CaseBySourceRefDuringPublish() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "幂等发布需求", "发布幂等验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult existingCase = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "已存在的 WP5 用例",
                                  "description": "模拟上次发布已创建资产",
                                  "source": "AI_GENERATED",
                                  "sourceRef": "wp5:%s",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行已有用例", "expectedResult": "已有用例可复用"}
                                  ]
                                }
                                """.formatted(candidateId)))
                .andExpect(status().isCreated())
                .andReturn();
        String existingCaseId = JsonPath.read(existingCase.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.records[0].action").value("LINK_EXISTING"))
                .andExpect(jsonPath("$.data.records[0].result").value("PLANNED"))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.createdCaseIds", hasSize(0)))
                .andExpect(jsonPath("$.data.records[0].action").value("LINK_EXISTING"))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId));

        mockMvc.perform(get("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("projectId", "project-wp5")
                        .param("source", "AI_GENERATED")
                        .param("keyword", "wp5:" + candidateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void repairsMissingTraceLinkWhenRetryingPartialPublish() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "补偿发布需求", "补偿发布验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult existingCase = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "部分成功遗留的 WP5 用例",
                                  "description": "模拟上次发布已创建用例但未创建追踪链接",
                                  "source": "AI_GENERATED",
                                  "sourceRef": "wp5:%s",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行补偿用例", "expectedResult": "补偿用例可复用"}
                                  ]
                                }
                                """.formatted(candidateId)))
                .andExpect(status().isCreated())
                .andReturn();
        String existingCaseId = JsonPath.read(existingCase.getResponse().getContentAsString(), "$.data.id");
        saveCandidateStatus(candidateId, "FAILED", existingCaseId, "追踪链接创建失败");

        mockMvc.perform(get("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("requirementId", requirementId)
                        .param("caseId", existingCaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.records[0].action").value("RETRY_LINK_EXISTING"))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId));

        mockMvc.perform(get("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("requirementId", requirementId)
                        .param("caseId", existingCaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.candidates[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.candidates[0].assetCaseId").value(existingCaseId))
                .andExpect(jsonPath("$.data.candidates[0].errorMessage").doesNotExist());
    }

    @Test
    void blocksHighSimilarRequirementCaseDuringPublish() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "重复冲突需求", "重复冲突验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        String candidateTitle = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].title");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult existingCase = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "%s",
                                  "description": "人工已维护的同需求高相似用例",
                                  "source": "MANUAL",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行重复冲突需求核心流程", "expectedResult": "核心流程通过"}
                                  ]
                                }
                                """.formatted(candidateTitle)))
                .andExpect(status().isCreated())
                .andReturn();
        String existingCaseId = JsonPath.read(existingCase.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirementId":"%s","caseId":"%s"}
                                """.formatted(requirementId, existingCaseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.records[0].action").value("DUPLICATE_REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.records[0].result").value("CONFLICT"))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId))
                .andExpect(jsonPath("$.data.records[0].errorMessage", containsString("高相似测试用例")));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.createdCaseIds", hasSize(0)))
                .andExpect(jsonPath("$.data.records[0].action").value("DUPLICATE_REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.records[0].result").value("CONFLICT"))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId));

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.candidates[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("projectId", "project-wp5")
                        .param("source", "AI_GENERATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void resolvesHighSimilarConflictByLinkingExistingRequirementCase() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "人工冲突处理需求", "人工冲突处理验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        String candidateTitle = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].title");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        MvcResult confirmed = mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();
        Integer confirmedVersion = JsonPath.read(confirmed.getResponse().getContentAsString(), "$.data.version");

        MvcResult existingCase = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "%s",
                                  "description": "人工确认可复用的同需求测试用例",
                                  "source": "MANUAL",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行人工冲突处理需求核心流程", "expectedResult": "核心流程通过"}
                                  ]
                                }
                                """.formatted(candidateTitle)))
                .andExpect(status().isCreated())
                .andReturn();
        String existingCaseId = JsonPath.read(existingCase.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirementId":"%s","caseId":"%s"}
                                """.formatted(requirementId, existingCaseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].action").value("DUPLICATE_REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.records[0].result").value("CONFLICT"))
                .andExpect(jsonPath("$.data.records[0].candidateStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.records[0].candidateVersion").value(confirmedVersion))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId));

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/resolve-conflict", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"caseId":"%s","reason":"人工确认复用","comment":"已比对步骤"}
                                """.formatted(confirmedVersion, existingCaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateId").value(candidateId))
                .andExpect(jsonPath("$.data.action").value("MANUAL_LINK_EXISTING"))
                .andExpect(jsonPath("$.data.result").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.candidateStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.candidateVersion").value(confirmedVersion + 1))
                .andExpect(jsonPath("$.data.assetCaseId").value(existingCaseId))
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.candidates[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.candidates[0].assetCaseId").value(existingCaseId))
                .andExpect(jsonPath("$.data.publishRecords[0].action").value("MANUAL_LINK_EXISTING"));

        mockMvc.perform(get("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("requirementId", requirementId)
                        .param("caseId", existingCaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void rejectsConflictResolutionWhenCaseIsNotLinkedToCandidateRequirement() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "冲突拒绝需求", "冲突拒绝验收", "project-wp5");
        String otherRequirementId = createRequirement(ownerToken, "其他需求", "其他验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        MvcResult confirmed = mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andReturn();
        Integer confirmedVersion = JsonPath.read(confirmed.getResponse().getContentAsString(), "$.data.version");

        MvcResult otherCase = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "其他需求下的测试用例",
                                  "description": "不应被链接到当前候选",
                                  "source": "MANUAL",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行其他需求", "expectedResult": "其他需求通过"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String otherCaseId = JsonPath.read(otherCase.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirementId":"%s","caseId":"%s"}
                                """.formatted(otherRequirementId, otherCaseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/resolve-conflict", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"caseId":"%s","reason":"误选用例"}
                                """.formatted(confirmedVersion, otherCaseId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("未关联候选需求")));
    }

    @Test
    void batchResolvesConflictsWithItemResultsAndProjectScope() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String deniedToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-other"));
        String requirementId = createRequirement(ownerToken, "批量冲突需求A", "批量冲突验收A", "project-wp5");
        String otherRequirementId = createRequirement(ownerToken, "批量冲突需求B", "批量冲突验收B", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s","%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId, otherRequirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        String secondCandidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].id");
        Integer firstVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");
        Integer secondVersion = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[1].version");
        String firstRequirementId = JsonPath.read(
                taskResult.getResponse().getContentAsString(),
                "$.data.candidates[0].requirementId"
        );
        String secondRequirementId = JsonPath.read(
                taskResult.getResponse().getContentAsString(),
                "$.data.candidates[1].requirementId"
        );

        String linkedCandidateId;
        String unlinkedCandidateId;
        Integer linkedVersion;
        Integer unlinkedVersion;
        if (requirementId.equals(firstRequirementId)) {
            linkedCandidateId = firstCandidateId;
            linkedVersion = firstVersion;
            unlinkedCandidateId = secondCandidateId;
            unlinkedVersion = secondVersion;
        } else {
            linkedCandidateId = secondCandidateId;
            linkedVersion = secondVersion;
            unlinkedCandidateId = firstCandidateId;
            unlinkedVersion = firstVersion;
        }
        MatcherAssert.assertThat(
                List.of(firstRequirementId, secondRequirementId),
                containsInAnyOrder(requirementId, otherRequirementId)
        );

        MvcResult firstConfirmed = mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", linkedCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(linkedVersion)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondConfirmed = mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", unlinkedCandidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(unlinkedVersion)))
                .andExpect(status().isOk())
                .andReturn();
        Integer firstConfirmedVersion = JsonPath.read(firstConfirmed.getResponse().getContentAsString(), "$.data.version");
        Integer secondConfirmedVersion = JsonPath.read(secondConfirmed.getResponse().getContentAsString(), "$.data.version");

        String existingCaseId = createManualTestCase(ownerToken, "project-wp5", "批量冲突既有用例A");
        linkRequirementToCase(ownerToken, requirementId, existingCaseId);

        mockMvc.perform(post("/api/v1/test-design/candidates/batch-resolve-conflicts")
                        .header("Authorization", "Bearer " + deniedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"candidateId":"%s","version":%d,"caseId":"%s"}
                                  ],
                                  "reason": "批量复用既有覆盖"
                                }
                                """.formatted(linkedCandidateId, firstConfirmedVersion, existingCaseId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/test-design/candidates/batch-resolve-conflicts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"candidateId":"%s","version":%d,"caseId":"%s"},
                                    {"candidateId":"%s","version":%d,"caseId":"%s"}
                                  ],
                                  "reason": "批量复用既有覆盖",
                                  "comment": "已人工比对"
                                }
                                """.formatted(
                                        linkedCandidateId, firstConfirmedVersion, existingCaseId,
                                        unlinkedCandidateId, secondConfirmedVersion, existingCaseId
                                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("MANUAL_LINK_EXISTING"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].candidateId").value(linkedCandidateId))
                .andExpect(jsonPath("$.data.items[0].result").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].record.action").value("MANUAL_LINK_EXISTING"))
                .andExpect(jsonPath("$.data.items[0].record.candidateStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.items[0].record.assetCaseId").value(existingCaseId))
                .andExpect(jsonPath("$.data.items[1].candidateId").value(unlinkedCandidateId))
                .andExpect(jsonPath("$.data.items[1].result").value("FAILED"))
                .andExpect(jsonPath("$.data.items[1].errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.items[1].errorMessage", containsString("未关联候选需求")));
    }

    @Test
    void allowsNearSimilarRequirementCaseWhenConflictThresholdIsStrict() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "严格阈值需求", "严格阈值验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        String candidateTitle = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].title");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult existingCase = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "%s 扩展",
                                  "description": "人工已维护的同需求近似用例",
                                  "source": "MANUAL",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行严格阈值需求扩展流程", "expectedResult": "扩展流程通过"}
                                  ]
                                }
                                """.formatted(candidateTitle)))
                .andExpect(status().isCreated())
                .andReturn();
        String existingCaseId = JsonPath.read(existingCase.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirementId":"%s","caseId":"%s"}
                                """.formatted(requirementId, existingCaseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.records[0].result").value("PLANNED"));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCEEDED"));
    }

    @Test
    void enforcesBatchReviewPermissionByCandidateProjectScope() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String deniedToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-other"));
        String requirementId = createRequirement(ownerToken, "批量评审权限需求", "批量评审验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/batch-action")
                        .header("Authorization", "Bearer " + deniedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"CONFIRM","candidates":[{"id":"%s","version":%d}]}
                                """.formatted(candidateId, version)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/test-design/candidates/batch-action")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"CONFIRM","candidates":[{"id":"%s","version":%d}]}
                                """.formatted(candidateId, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.items[0].candidate.status").value("CONFIRMED"));
    }

    @Test
    void retriesAndCancelsFailedGenerationTasks() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "任务状态需求", "状态流验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");

        saveTaskStatus(taskId, TestDesignTaskStatus.FAILED, "模型输出非法");
        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/cancel", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.task.errorMessage").value("用户取消生成任务"));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/retry", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(1))
                .andExpect(jsonPath("$.data.candidates", hasSize(1)));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/retry", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void contractDoesNotExposeRawPromptOrSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(openApi, containsString("/api/v1/test-design/tasks"));
        MatcherAssert.assertThat(openApi, containsString("promptKey"));
        MatcherAssert.assertThat(openApi, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(openApi, not(containsString("secretValue")));
    }

    private String createRequirement(
            String userToken,
            String title,
            String acceptanceCriteria,
            String projectId
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "WP5 测试需求",
                                  "priority": "HIGH",
                                  "projectId": "%s",
                                  "acceptanceCriteria": "%s"
                                }
                                """.formatted(title, projectId, acceptanceCriteria)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createManualTestCase(String userToken, String projectId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "title": "%s",
                                  "description": "人工确认可复用的同需求测试用例",
                                  "source": "MANUAL",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行核心流程", "expectedResult": "核心流程通过"}
                                  ]
                                }
                                """.formatted(projectId, title)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void linkRequirementToCase(String userToken, String requirementId, String caseId) throws Exception {
        mockMvc.perform(post("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirementId":"%s","caseId":"%s"}
                                """.formatted(requirementId, caseId)))
                .andExpect(status().isCreated());
    }

    private String createApi(
            String userToken,
            String projectId,
            String summary,
            String method,
            String path
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/apis")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "summary": "%s",
                                  "description": "上下文关联 API",
                                  "httpMethod": "%s",
                                  "path": "%s",
                                  "requestSchema": "{\\"username\\":\\"string\\",\\"password\\":\\"token=secret-value\\"}",
                                  "responseSchema": "{\\"redirectUrl\\":\\"string\\"}",
                                  "status": "ACTIVE"
                                }
                                """.formatted(projectId, summary, method, path)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createPage(
            String userToken,
            String projectId,
            String name,
            String urlPattern
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/pages")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "name": "%s",
                                  "urlPattern": "%s",
                                  "source": "FIGMA",
                                  "sourceRef": "figma-login",
                                  "sourceVersion": "v1",
                                  "componentTree": {"form": "login", "note": "apiKey=page-secret"},
                                  "screenshotUrl": "https://example.test/login.png",
                                  "status": "ACTIVE"
                                }
                                """.formatted(projectId, name, urlPattern)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createBusinessFlow(
            String userToken,
            String projectId,
            String name
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/business-flows")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "name": "%s",
                                  "description": "覆盖登录成功主路径",
                                  "flowJson": {"nodes": ["openLogin", "submit"], "secret": "token=flow-secret"},
                                  "priority": "HIGH",
                                  "status": "ACTIVE"
                                }
                                """.formatted(projectId, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void createTraceLink(
            String userToken,
            String requirementId,
            String apiId,
            String pageId,
            String flowId
    ) throws Exception {
        mockMvc.perform(post("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirementId": "%s",
                                  "apiId": "%s",
                                  "pageId": "%s",
                                  "flowId": "%s"
                                }
                                """.formatted(requirementId, apiId, pageId, flowId)))
                .andExpect(status().isCreated());
    }

    private void saveTaskStatus(String taskId, TestDesignTaskStatus status, String errorMessage) {
        TestDesignTask task = testDesignRepository.task(UUID.fromString(taskId)).orElseThrow();
        testDesignRepository.saveTask(new TestDesignTask(
                task.id(),
                task.projectId(),
                task.title(),
                status.name(),
                task.requirementIds(),
                task.coverageTypes(),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                task.totalRequirements(),
                task.generatedCount(),
                task.confirmedCount(),
                task.publishedCount(),
                errorMessage,
                task.requestedBy(),
                task.idempotencyKey(),
                task.requestDigest(),
                task.inputDigest(),
                task.contextSummaryJson(),
                task.createdAt(),
                Instant.now()
        ));
    }

    private void saveCandidateStatus(String candidateId, String status, String assetCaseId, String errorMessage) {
        TestDesignCandidate candidate = testDesignRepository.candidate(UUID.fromString(candidateId)).orElseThrow();
        testDesignRepository.saveCandidate(new TestDesignCandidate(
                candidate.id(),
                candidate.taskId(),
                candidate.projectId(),
                candidate.requirementId(),
                candidate.apiId(),
                candidate.title(),
                candidate.description(),
                candidate.coverageType(),
                candidate.priority(),
                status,
                candidate.preconditions(),
                candidate.stepsJson(),
                candidate.expectedResult(),
                candidate.tags(),
                candidate.duplicateKey(),
                candidate.confidence(),
                candidate.promptKey(),
                candidate.promptVersion(),
                candidate.modelInvocationId(),
                candidate.modelProviderName(),
                candidate.modelName(),
                assetCaseId == null ? null : UUID.fromString(assetCaseId),
                candidate.reviewComment(),
                candidate.rejectedReason(),
                candidate.ignoredReason(),
                errorMessage,
                candidate.confirmedBy(),
                candidate.confirmedAt(),
                candidate.version() + 1,
                candidate.createdAt(),
                Instant.now()
        ));
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp5_user",
                "WP5 用户",
                "wp5@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }
}
