package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.query.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.BusinessFlowResponse;
import com.songhg.veri.agent.asset.application.view.PageResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TraceLinkResponse;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.command.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDesignGenerationServiceTest {

    private static final UUID REQUIREMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID API_ID_1 = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID API_ID_2 = UUID.fromString("00000000-0000-4000-8000-000000000202");
    private static final UUID PAGE_ID_1 = UUID.fromString("00000000-0000-4000-8000-000000000301");
    private static final UUID PAGE_ID_2 = UUID.fromString("00000000-0000-4000-8000-000000000302");
    private static final UUID FLOW_ID_1 = UUID.fromString("00000000-0000-4000-8000-000000000401");
    private static final UUID FLOW_ID_2 = UUID.fromString("00000000-0000-4000-8000-000000000402");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void appliesConfiguredContextClippingToPersistedSummary() throws Exception {
        TestDesignProperties properties = properties(1, 2, 1, 24, 26, 32);
        TestDesignGenerationService service = service(properties, mock(AssetService.class));
        AssetService assetService = mock(AssetService.class);
        service = service(properties, assetService);
        when(assetService.listLinks(any(TraceLinkListRequest.class))).thenReturn(com.songhg.veri.agent.common.api.PageResponse.of(
                List.of(
                        traceLink(API_ID_1, PAGE_ID_1, FLOW_ID_1),
                        traceLink(API_ID_2, PAGE_ID_2, FLOW_ID_2)
                ),
                0,
                100,
                2
        ));
        when(assetService.getApi(API_ID_1)).thenReturn(api(API_ID_1, "API-1"));
        when(assetService.getApi(API_ID_2)).thenReturn(api(API_ID_2, "API-2"));
        when(assetService.getPage(PAGE_ID_1)).thenReturn(page(PAGE_ID_1, "PAGE-1"));
        when(assetService.getPage(PAGE_ID_2)).thenReturn(page(PAGE_ID_2, "PAGE-2"));
        when(assetService.getBusinessFlow(FLOW_ID_1)).thenReturn(flow(FLOW_ID_1, "FLOW-1"));
        when(assetService.getBusinessFlow(FLOW_ID_2)).thenReturn(flow(FLOW_ID_2, "FLOW-2"));
        when(assetService.findActiveTestCasesByRequirement("project-wp5", REQUIREMENT_ID)).thenReturn(List.of());

        TestDesignGenerationService.TestDesignGenerationContext context = service.generationContext(
                "project-wp5",
                List.of(requirement()),
                new TestDesignGenerationService.ExplicitContextAssetIds(
                        List.of(API_ID_1, API_ID_2),
                        List.of(PAGE_ID_1, PAGE_ID_2),
                        List.of(FLOW_ID_1, FLOW_ID_2)
                )
        );

        JsonNode summary = objectMapper.readTree(context.contextSummaryJson());
        assertThat(summary.path("limits").path("linkedAssetsPerRequirement").asInt()).isEqualTo(1);
        assertThat(summary.path("limits").path("explicitAssetsPerType").asInt()).isEqualTo(2);
        assertThat(summary.path("limits").path("requirementDescriptionChars").asInt()).isEqualTo(24);
        assertThat(summary.path("limits").path("acceptanceCriteriaChars").asInt()).isEqualTo(26);
        assertThat(summary.path("limits").path("linkedAssetSchemaChars").asInt()).isEqualTo(32);
        assertThat(summary.path("assemblyPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-context-assembly-policy-v2");
        assertThat(summary.path("assemblyPolicy").path("assemblyMode").asText())
                .isEqualTo("SNAPSHOT_DIGEST_ONLY");
        assertThat(summary.path("assemblyPolicy").path("digestStrategy").asText())
                .isEqualTo("SHA256_CONTEXT_SUMMARY");
        assertThat(summary.path("assemblyPolicy").path("inputDigestRequired").asBoolean()).isTrue();
        assertThat(summary.path("assemblyPolicy").path("persistedContextSummaryOnly").asBoolean()).isTrue();
        assertThat(summary.path("assemblyPolicy").path("wp3ApplicationServiceOnly").asBoolean()).isTrue();
        assertThat(summary.path("assemblyPolicy").path("rawContextBodyStored").asBoolean()).isFalse();
        assertThat(summary.path("assemblyPolicy").path("modelPayloadStored").asBoolean()).isFalse();
        assertThat(summary.path("assemblyPolicy").path("digestValueExported").asBoolean()).isFalse();
        assertThat(summary.path("assemblyPolicy").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("policyGovernance").path("policyVersion").asText())
                .isEqualTo("wp5-context-policy-v1");
        assertThat(summary.path("policyGovernance").path("policySource").asText())
                .isEqualTo("PLATFORM_DEFAULT");
        assertThat(summary.path("policyGovernance").path("governanceStatus").asText())
                .isEqualTo("PLATFORM_DEFAULT_ONLY");
        assertThat(summary.path("policyGovernance").path("projectOverrideSupported").asBoolean()).isFalse();
        assertThat(summary.path("policyGovernance").path("environmentOverrideSupported").asBoolean()).isFalse();
        assertThat(summary.path("policyGovernance").path("changeApprovalWorkflowReady").asBoolean()).isFalse();
        assertThat(summary.path("policyGovernance").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("policyOperations").path("policyVersion").asText())
                .isEqualTo("wp5-context-policy-operations-v2");
        assertThat(summary.path("policyOperations").path("operationMode").asText())
                .isEqualTo("PLATFORM_DEFAULT_ONLY");
        assertThat(summary.path("policyOperations").path("policyResolutionOrder").asText())
                .isEqualTo("PLATFORM_DEFAULT_ONLY");
        assertThat(summary.path("policyOperations").path("policyFallbackBehavior").asText())
                .isEqualTo("DEPLOY_CONFIG_CHANGE_REQUIRED");
        assertThat(summary.path("policyOperations").path("approvalStatus").asText())
                .isEqualTo("WORKFLOW_NOT_READY");
        assertThat(summary.path("policyOperations").path("projectOverrideStoreReady").asBoolean()).isFalse();
        assertThat(summary.path("policyOperations").path("environmentOverrideStoreReady").asBoolean()).isFalse();
        assertThat(summary.path("policyOperations").path("changeApprovalWorkflowReady").asBoolean()).isFalse();
        assertThat(summary.path("policyOperations").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("policyVersion").asText()).isEqualTo("wp5-scope-policy-v1");
        assertThat(summary.path("scopePolicy").path("scopeModel").asText()).isEqualTo("PROJECT_RESOURCE_SCOPE");
        assertThat(summary.path("scopePolicy").path("listFallbackScope").asText())
                .isEqualTo("PLATFORM_WHEN_PROJECT_FILTER_ABSENT");
        assertThat(summary.path("scopePolicy").path("taskProjectScopeRequired").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("candidateProjectScopeRequired").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("batchCandidateProjectScopeRequired").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("publishProjectScopeRequired").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("asyncTaskProjectScopeRecovered").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("smokeProjectScopeRequired").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("evaluationCorpusProjectIsolated").asBoolean()).isTrue();
        assertThat(summary.path("scopePolicy").path("evaluationCorpusOperationsReady").asBoolean()).isFalse();
        assertThat(summary.path("scopePolicy").path("crossWpScopeDashboardReady").asBoolean()).isFalse();
        assertThat(summary.path("scopePolicy").path("candidateIdentifierListExported").asBoolean()).isFalse();
        assertThat(summary.path("scopePolicy").path("roleRuleDetailExported").asBoolean()).isFalse();
        assertThat(summary.path("scopePolicy").path("serviceTokenValueExported").asBoolean()).isFalse();
        assertThat(summary.path("scopePolicy").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-evaluation-corpus-policy-v1");
        assertThat(summary.path("evaluationCorpusPolicy").path("corpusMode").asText())
                .isEqualTo("GOLDEN_SET_BASELINE");
        assertThat(summary.path("evaluationCorpusPolicy").path("qualityGateMode").asText())
                .isEqualTo("MANUAL_OPT_IN_AI_EVAL");
        assertThat(summary.path("evaluationCorpusPolicy").path("thresholdSource").asText())
                .isEqualTo("DEPLOY_CONFIG");
        assertThat(summary.path("evaluationCorpusPolicy").path("projectScopeRequired").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("goldenSetBaselineRequired").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("qualityEvalScriptReady").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("qualityGateIntegrated").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("readinessDistributionTracked").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("promptVersionTracked").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("evaluationCorpusProjectIsolated").asBoolean()).isTrue();
        assertThat(summary.path("evaluationCorpusPolicy").path("sampleMaintenanceReady").asBoolean()).isFalse();
        assertThat(summary.path("evaluationCorpusPolicy").path("longTermCalibrationReady").asBoolean()).isFalse();
        assertThat(summary.path("evaluationCorpusPolicy").path("operationsConsoleReady").asBoolean()).isFalse();
        assertThat(summary.path("evaluationCorpusPolicy").path("corpusRowExported").asBoolean()).isFalse();
        assertThat(summary.path("evaluationCorpusPolicy").path("candidateBodyExported").asBoolean()).isFalse();
        assertThat(summary.path("evaluationCorpusPolicy").path("reviewCommentExported").asBoolean()).isFalse();
        assertThat(summary.path("evaluationCorpusPolicy").path("promptBodyExported").asBoolean()).isFalse();
        assertThat(summary.path("evaluationCorpusPolicy").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("releaseReadinessPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-release-readiness-policy-v1");
        assertThat(summary.path("releaseReadinessPolicy").path("decisionMode").asText())
                .isEqualTo("ADVISORY_QUALITY_GATE");
        assertThat(summary.path("releaseReadinessPolicy").path("thresholdSource").asText())
                .isEqualTo("DEPLOY_CONFIG");
        assertThat(summary.path("releaseReadinessPolicy").path("qualityThresholdEvaluated").asBoolean()).isTrue();
        assertThat(summary.path("releaseReadinessPolicy").path("advisoryOnly").asBoolean()).isTrue();
        assertThat(summary.path("releaseReadinessPolicy").path("publishBlockingEnabled").asBoolean()).isFalse();
        assertThat(summary.path("releaseReadinessPolicy").path("manualApprovalRequired").asBoolean()).isTrue();
        assertThat(summary.path("releaseReadinessPolicy").path("approvalWorkflowReady").asBoolean()).isFalse();
        assertThat(summary.path("releaseReadinessPolicy").path("autoPublishAllowed").asBoolean()).isFalse();
        assertThat(summary.path("releaseReadinessPolicy").path("confirmedCandidateRequired").asBoolean()).isTrue();
        assertThat(summary.path("releaseReadinessPolicy").path("candidateEvidenceExported").asBoolean()).isFalse();
        assertThat(summary.path("releaseReadinessPolicy").path("approvalNotesExported").asBoolean()).isFalse();
        assertThat(summary.path("releaseReadinessPolicy").path("thresholdRuleDetailExported").asBoolean()).isFalse();
        assertThat(summary.path("releaseReadinessPolicy").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("auditChainPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-audit-chain-policy-v1");
        assertThat(summary.path("auditChainPolicy").path("chainMode").asText())
                .isEqualTo("WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT");
        assertThat(summary.path("auditChainPolicy").path("eventSource").asText())
                .isEqualTo("TASK_REVIEW_PUBLISH_MODEL_REFERENCES");
        assertThat(summary.path("auditChainPolicy").path("wp1AuditEventWritten").asBoolean()).isTrue();
        assertThat(summary.path("auditChainPolicy").path("wp2InvocationReferenceTracked").asBoolean()).isTrue();
        assertThat(summary.path("auditChainPolicy").path("wp3PublishReferenceTracked").asBoolean()).isTrue();
        assertThat(summary.path("auditChainPolicy").path("wp5DomainEventsTracked").asBoolean()).isTrue();
        assertThat(summary.path("auditChainPolicy").path("projectScopeRequired").asBoolean()).isTrue();
        assertThat(summary.path("auditChainPolicy").path("traceSignalTracked").asBoolean()).isTrue();
        assertThat(summary.path("auditChainPolicy").path("crossWpAuditDashboardReady").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("auditOutboxReplayDashboardReady").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("auditEventDetailExported").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("candidateIdentifierListExported").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("platformAuditIdentifierExported").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("traceIdValueExported").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("modelInvocationIdValueExported").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("publishIdentifierValueExported").asBoolean()).isFalse();
        assertThat(summary.path("auditChainPolicy").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("archivePolicy").path("policyVersion").asText())
                .isEqualTo("wp5-archive-policy-v1");
        assertThat(summary.path("archivePolicy").path("retentionDays").asInt()).isEqualTo(180);
        assertThat(summary.path("archivePolicy").path("storagePolicy").asText()).isEqualTo("platformManaged");
        assertThat(summary.path("archivePolicy").path("approvalRequired").asBoolean()).isTrue();
        assertThat(summary.path("archivePolicy").path("archiveApprovalWorkflowReady").asBoolean()).isFalse();
        assertThat(summary.path("archivePolicy").path("externalSharingAllowed").asBoolean()).isFalse();
        assertThat(summary.path("archivePolicy").path("retentionPolicyTracked").asBoolean()).isTrue();
        assertThat(summary.path("archivePolicy").path("archiveStorageReady").asBoolean()).isFalse();
        assertThat(summary.path("archivePolicy").path("archivePathExported").asBoolean()).isFalse();
        assertThat(summary.path("archivePolicy").path("archiveNotesExported").asBoolean()).isFalse();
        assertThat(summary.path("archivePolicy").path("approvalNotesExported").asBoolean()).isFalse();
        assertThat(summary.path("archivePolicy").path("ticketUrlExported").asBoolean()).isFalse();
        assertThat(summary.path("archivePolicy").path("aggregateOnly").asBoolean()).isTrue();
        assertThat(summary.path("linkedAssetsByRequirement").get(0).path("apiCount").asInt()).isEqualTo(2);
        assertThat(summary.path("linkedAssetsByRequirement").get(0).path("apis")).hasSize(1);
        assertThat(summary.path("linkedAssetsByRequirement").get(0).path("pages")).hasSize(1);
        assertThat(summary.path("linkedAssetsByRequirement").get(0).path("flows")).hasSize(1);
        assertThat(summary.path("explicitAssets").path("apiCount").asInt()).isEqualTo(2);
        assertThat(summary.path("explicitAssets").path("apis")).hasSize(2);
        assertThat(summary.path("requirements").get(0).path("descriptionPreview").asText()).endsWith("...");
        assertThat(summary.path("explicitAssets").path("apis").get(0).path("requestSchemaPreview").asText()).endsWith("...");
        assertThat(summary.path("limits").path("rawPromptStored").asBoolean()).isFalse();
    }

    @Test
    void sendsContextPackingPolicyToWp2ModelPayload() throws Exception {
        InMemoryModelAccessRepository repository = new InMemoryModelAccessRepository();
        TestDesignProperties properties = properties(3, 4, 2, 120, 130, 140);
        TestDesignResponseMapper mapper = responseMapper(repository, properties);
        ModelInvocationService invocationService = mock(ModelInvocationService.class);
        ArgumentCaptor<ModelInvocationCommand> commandCaptor = ArgumentCaptor.forClass(ModelInvocationCommand.class);
        when(invocationService.invoke(commandCaptor.capture(), any(ServicePrincipal.class))).thenReturn(new ModelInvocationResult(
                UUID.fromString("00000000-0000-4000-8000-000000000601"),
                UUID.fromString("00000000-0000-4000-8000-000000000701"),
                "local-echo-primary",
                "test-local-model",
                false,
                """
                        {
                          "schemaVersion": "wp5-test",
                          "cases": [
                            {
                              "title": "模型验证上下文裁剪策略需求",
                              "description": "覆盖上下文裁剪策略",
                              "coverageType": "SMOKE",
                              "priority": "HIGH",
                              "preconditions": "需求已确认",
                              "steps": [
                                {"action": "创建生成任务", "expectedResult": "任务可生成"},
                                {"action": "检查上下文策略", "expectedResult": "策略已纳入模型输入"}
                              ],
                              "expectedResult": "策略生效",
                              "requirementRef": "REQ-WP5",
                              "confidence": 0.88
                            }
                          ]
                        }
                        """,
                20,
                10,
                new BigDecimal("0.0003")
        ));
        TestDesignGenerationService service = new TestDesignGenerationService(
                mock(AssetService.class),
                mapper,
                new TestDesignCandidateQualityGate(mapper),
                invocationService,
                new TestDesignModelOutputParser(objectMapper),
                properties,
                objectMapper
        );
        TestDesignTask task = task();

        service.generateCandidates(task, List.of(requirement()), List.of("SMOKE"), Instant.now());

        JsonNode payload = objectMapper.readTree(commandCaptor.getValue().messages().getFirst().content());
        assertThat(payload.path("contextPacking").path("linkedAssetsPerRequirement").asInt()).isEqualTo(3);
        assertThat(payload.path("contextPacking").path("explicitAssetsPerType").asInt()).isEqualTo(4);
        assertThat(payload.path("contextPacking").path("existingCasesPerRequirement").asInt()).isEqualTo(2);
        assertThat(payload.path("contextPacking").path("requirementDescriptionChars").asInt()).isEqualTo(120);
        assertThat(payload.path("contextPacking").path("acceptanceCriteriaChars").asInt()).isEqualTo(130);
        assertThat(payload.path("contextPacking").path("linkedAssetSchemaChars").asInt()).isEqualTo(140);
        assertThat(payload.path("contextPacking").path("rawPromptStored").asBoolean()).isFalse();
        assertThat(payload.path("contextPacking").path("persistedContextSummaryOnly").asBoolean()).isTrue();
        assertThat(payload.path("contextPacking").path("assemblyPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-context-assembly-policy-v2");
        assertThat(payload.path("contextPacking").path("assemblyPolicy").path("assemblyMode").asText())
                .isEqualTo("SNAPSHOT_DIGEST_ONLY");
        assertThat(payload.path("contextPacking").path("assemblyPolicy").path("digestStrategy").asText())
                .isEqualTo("SHA256_CONTEXT_SUMMARY");
        assertThat(payload.path("contextPacking").path("assemblyPolicy").path("inputDigestRequired").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("assemblyPolicy").path("rawContextBodyStored").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("assemblyPolicy").path("modelPayloadStored").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("assemblyPolicy").path("aggregateOnly").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("policyGovernance").path("policySource").asText())
                .isEqualTo("PLATFORM_DEFAULT");
        assertThat(payload.path("contextPacking").path("policyGovernance").path("projectOverrideSupported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("policyGovernance").path("changeApprovalWorkflowReady").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("policyOperations").path("policyVersion").asText())
                .isEqualTo("wp5-context-policy-operations-v2");
        assertThat(payload.path("contextPacking").path("policyOperations").path("policyResolutionOrder").asText())
                .isEqualTo("PLATFORM_DEFAULT_ONLY");
        assertThat(payload.path("contextPacking").path("policyOperations").path("policyFallbackBehavior").asText())
                .isEqualTo("DEPLOY_CONFIG_CHANGE_REQUIRED");
        assertThat(payload.path("contextPacking").path("policyOperations").path("approvalStatus").asText())
                .isEqualTo("WORKFLOW_NOT_READY");
        assertThat(payload.path("contextPacking").path("policyOperations").path("projectOverrideStoreReady").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("policyOperations").path("aggregateOnly").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("scopePolicy").path("policyVersion").asText())
                .isEqualTo("wp5-scope-policy-v1");
        assertThat(payload.path("contextPacking").path("scopePolicy").path("scopeModel").asText())
                .isEqualTo("PROJECT_RESOURCE_SCOPE");
        assertThat(payload.path("contextPacking").path("scopePolicy").path("taskProjectScopeRequired").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("scopePolicy").path("publishProjectScopeRequired").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("scopePolicy").path("candidateIdentifierListExported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("scopePolicy").path("aggregateOnly").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("evaluationCorpusPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-evaluation-corpus-policy-v1");
        assertThat(payload.path("contextPacking").path("evaluationCorpusPolicy").path("corpusMode").asText())
                .isEqualTo("GOLDEN_SET_BASELINE");
        assertThat(payload.path("contextPacking").path("evaluationCorpusPolicy").path("qualityGateMode").asText())
                .isEqualTo("MANUAL_OPT_IN_AI_EVAL");
        assertThat(payload.path("contextPacking").path("evaluationCorpusPolicy").path("qualityEvalScriptReady")
                .asBoolean()).isTrue();
        assertThat(payload.path("contextPacking").path("evaluationCorpusPolicy").path("operationsConsoleReady")
                .asBoolean()).isFalse();
        assertThat(payload.path("contextPacking").path("evaluationCorpusPolicy").path("corpusRowExported")
                .asBoolean()).isFalse();
        assertThat(payload.path("contextPacking").path("evaluationCorpusPolicy").path("aggregateOnly").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("releaseReadinessPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-release-readiness-policy-v1");
        assertThat(payload.path("contextPacking").path("releaseReadinessPolicy").path("decisionMode").asText())
                .isEqualTo("ADVISORY_QUALITY_GATE");
        assertThat(payload.path("contextPacking").path("releaseReadinessPolicy").path("advisoryOnly").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("releaseReadinessPolicy").path("publishBlockingEnabled").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("releaseReadinessPolicy").path("approvalWorkflowReady").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("releaseReadinessPolicy").path("candidateEvidenceExported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("releaseReadinessPolicy").path("aggregateOnly").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("auditChainPolicy").path("policyVersion").asText())
                .isEqualTo("wp5-audit-chain-policy-v1");
        assertThat(payload.path("contextPacking").path("auditChainPolicy").path("chainMode").asText())
                .isEqualTo("WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT");
        assertThat(payload.path("contextPacking").path("auditChainPolicy").path("wp1AuditEventWritten").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("auditChainPolicy").path("crossWpAuditDashboardReady").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("auditChainPolicy").path("auditEventDetailExported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("auditChainPolicy").path("traceIdValueExported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("auditChainPolicy").path("aggregateOnly").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("archivePolicy").path("policyVersion").asText())
                .isEqualTo("wp5-archive-policy-v1");
        assertThat(payload.path("contextPacking").path("archivePolicy").path("retentionDays").asInt()).isEqualTo(180);
        assertThat(payload.path("contextPacking").path("archivePolicy").path("storagePolicy").asText())
                .isEqualTo("platformManaged");
        assertThat(payload.path("contextPacking").path("archivePolicy").path("approvalRequired").asBoolean())
                .isTrue();
        assertThat(payload.path("contextPacking").path("archivePolicy").path("archiveApprovalWorkflowReady").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("archivePolicy").path("archiveStorageReady").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("archivePolicy").path("archivePathExported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("archivePolicy").path("approvalNotesExported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("archivePolicy").path("ticketUrlExported").asBoolean())
                .isFalse();
        assertThat(payload.path("contextPacking").path("archivePolicy").path("aggregateOnly").asBoolean())
                .isTrue();
        assertThat(commandCaptor.getValue().promptKey()).isEqualTo("wp5-test-design-v1");
        assertThat(commandCaptor.getValue().messages()).hasSize(1);
    }

    private TestDesignGenerationService service(TestDesignProperties properties, AssetService assetService) {
        InMemoryModelAccessRepository repository = new InMemoryModelAccessRepository();
        TestDesignResponseMapper mapper = responseMapper(repository, properties);
        return new TestDesignGenerationService(
                assetService,
                mapper,
                new TestDesignCandidateQualityGate(mapper),
                modelInvocationService(repository, new RecordingProviderClient()),
                new TestDesignModelOutputParser(objectMapper),
                properties,
                objectMapper
        );
    }

    private TestDesignResponseMapper responseMapper(
            InMemoryModelAccessRepository repository,
            TestDesignProperties properties
    ) {
        ModelInvocationJobRepository jobRepository = new InMemoryModelInvocationJobRepository();
        return new TestDesignResponseMapper(objectMapper, repository, jobRepository, properties);
    }

    private ModelInvocationService modelInvocationService(
            InMemoryModelAccessRepository repository,
            RecordingProviderClient providerClient
    ) {
        ModelAccessProperties modelAccessProperties = modelAccessProperties();
        ModelInvocationService invocationService = mock(ModelInvocationService.class);
        when(invocationService.invoke(any(ModelInvocationCommand.class), any(ServicePrincipal.class))).thenAnswer(invocation -> {
            ModelInvocationCommand command = invocation.getArgument(0);
            ProviderCallResult result = providerClient.call(null, new ProviderCallRequest("test-local-model", "", command.messages().getFirst().content()));
            return new ModelInvocationResult(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "local-echo-primary",
                    "test-local-model",
                    false,
                    result.content(),
                    result.inputTokens(),
                    result.outputTokens(),
                    BigDecimal.ZERO
            );
        });
        return invocationService;
    }

    private TestDesignProperties properties(
            int linkedAssetsPerRequirement,
            int explicitAssetsPerType,
            int existingCasesPerRequirement,
            int requirementDescriptionChars,
            int acceptanceCriteriaChars,
            int assetSchemaChars
    ) {
        return new TestDesignProperties(
                "test-design-token",
                true,
                "MODEL",
                "wp5-test-design-v1",
                "1.0.0",
                false,
                20,
                3,
                linkedAssetsPerRequirement,
                explicitAssetsPerType,
                existingCasesPerRequirement,
                requirementDescriptionChars,
                acceptanceCriteriaChars,
                assetSchemaChars,
                100,
                true,
                true,
                100,
                600,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                0.86D,
                0.90D,
                180,
                false,
                true
        );
    }

    private ModelAccessProperties modelAccessProperties() {
        return new ModelAccessProperties(
                "test-model-token",
                "test-local-model",
                20_000,
                null,
                null,
                256,
                "UTC",
                10000,
                0,
                1,
                1000,
                1000,
                new BigDecimal("0.8"),
                0,
                1,
                0,
                1,
                0,
                3_600_000,
                null,
                "BLOCK",
                List.of()
        );
    }

    private RequirementResponse requirement() {
        Instant now = Instant.now();
        return new RequirementResponse(
                REQUIREMENT_ID,
                "REQ-WP5",
                "上下文裁剪策略需求",
                "这是一段很长的需求描述，用于验证企业级上下文摘要可以根据运营配置进行裁剪",
                "MANUAL",
                "REQ-001",
                null,
                "这是一段很长的验收标准，用于验证企业级上下文摘要可以根据运营配置进行裁剪",
                "ACTIVE",
                "HIGH",
                "project-wp5",
                "wp5,context",
                1,
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }

    private ApiResponseDTO api(UUID id, String code) {
        Instant now = Instant.now();
        return new ApiResponseDTO(
                id,
                code,
                "上下文 API",
                "用于上下文装配的 API",
                "POST",
                "/api/wp5/" + code.toLowerCase(),
                "OPENAPI",
                code,
                "v1",
                "{\"request\":\"abcdefghijklmnopqrstuvwxyz0123456789\"}",
                "{\"response\":\"abcdefghijklmnopqrstuvwxyz0123456789\"}",
                "project-wp5",
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }

    private PageResponse page(UUID id, String code) {
        Instant now = Instant.now();
        return new PageResponse(
                id,
                code,
                "上下文页面",
                "/wp5/" + code.toLowerCase(),
                "FIGMA",
                code,
                "v1",
                "{\"tree\":\"abcdefghijklmnopqrstuvwxyz0123456789\"}",
                "https://example.test/page.png",
                "project-wp5",
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }

    private BusinessFlowResponse flow(UUID id, String code) {
        Instant now = Instant.now();
        return new BusinessFlowResponse(
                id,
                code,
                "上下文业务流",
                "上下文业务流描述",
                "{\"nodes\":[\"abcdefghijklmnopqrstuvwxyz0123456789\"]}",
                "HIGH",
                "project-wp5",
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }

    private TraceLinkResponse traceLink(UUID apiId, UUID pageId, UUID flowId) {
        return new TraceLinkResponse(UUID.randomUUID(), REQUIREMENT_ID, apiId, pageId, flowId, null, Instant.now());
    }

    private TestDesignTask task() {
        Instant now = Instant.now();
        return new TestDesignTask(
                UUID.fromString("00000000-0000-4000-8000-000000000501"),
                "project-wp5",
                "模型上下文装配任务",
                TestDesignTaskStatus.RUNNING.name(),
                REQUIREMENT_ID.toString(),
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
                "wp5-user",
                null,
                null,
                "input-digest",
                "{\"contextVersion\":\"wp5-context-v1\"}",
                now,
                now
        );
    }

    private static class RecordingProviderClient {

        private String lastMessageText;

        public ProviderCallResult call(Object provider, ProviderCallRequest request) {
            lastMessageText = request.messageText().replaceFirst("^user: ", "");
            return new ProviderCallResult("""
                    {
                      "schemaVersion": "wp5-test",
                      "cases": [
                        {
                          "title": "模型验证上下文裁剪策略需求",
                          "description": "覆盖上下文裁剪策略",
                          "coverageType": "SMOKE",
                          "priority": "HIGH",
                          "preconditions": "需求已确认",
                          "steps": [
                            {"action": "创建生成任务", "expectedResult": "任务可生成"},
                            {"action": "检查上下文策略", "expectedResult": "策略已纳入模型输入"}
                          ],
                          "expectedResult": "策略生效",
                          "requirementRef": "REQ-WP5",
                          "confidence": 0.88
                        }
                      ]
                    }
                    """, 20, 10);
        }

        private String lastMessageText() {
            return lastMessageText;
        }
    }
}
