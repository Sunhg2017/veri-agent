package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelAccessPolicyOverride;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptApprovalStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@Profile("local")
@Primary
public class InMemoryModelAccessRepository implements ModelAccessRepository {

    private final Map<UUID, ModelProviderConfig> providers = new ConcurrentHashMap<>();
    private final Map<UUID, PromptTemplate> prompts = new ConcurrentHashMap<>();
    private final Map<String, ModelAccessPolicyOverride> policies = new ConcurrentHashMap<>();
    private final List<InvocationRecord> invocations = new ArrayList<>();

    public InMemoryModelAccessRepository() {
        Instant now = Instant.now();
        saveProvider(new ModelProviderConfig(
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "local-echo-primary",
                ProviderType.LOCAL_ECHO,
                "default",
                "CHAT,TEXT,JSON,REQUIREMENT_PARSE",
                null,
                "local://echo",
                ProviderStatus.ENABLED,
                10,
                3000,
                new BigDecimal("0.0001"),
                new BigDecimal("0.0002"),
                now,
                now
        ));
        savePrompt(new PromptTemplate(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                "test-case-design",
                "测试用例设计助手",
                1,
                "你是企业级测试设计助手，请基于以下上下文输出结构化建议：{{context}}",
                PromptStatus.ACTIVE,
                "WP2 默认 Prompt",
                false,
                PromptApprovalStatus.NOT_REQUIRED,
                null,
                null,
                null,
                now,
                now
        ));
        savePrompt(new PromptTemplate(
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                "wp4-document-requirement-parse",
                "WP4 文档需求解析助手",
                1,
                """
                        {{schemaMarker}}
                        你是企业级需求解析助手。请只返回 JSON，不要返回 Markdown。
                        JSON schema: {"requirements":[{"title":"需求标题","description":"需求说明","priority":"CRITICAL|HIGH|MEDIUM|LOW","acceptanceCriteria":"验收标准","tags":["标签"],"confidence":0.0}]}
                        从用户提供的文本、Markdown 或 JSON 中抽取可人工确认的需求候选项；无法判断时返回空 requirements。
                        """,
                PromptStatus.ACTIVE,
                "WP4 AI 文档解析 MVP Prompt",
                false,
                PromptApprovalStatus.NOT_REQUIRED,
                null,
                null,
                null,
                now,
                now
        ));
        savePrompt(new PromptTemplate(
                UUID.fromString("00000000-0000-0000-0000-000000000303"),
                "wp5-test-design-v1",
                "WP5 测试设计生成助手",
                1,
                """
                        {{schemaMarker}}
                        你是企业级测试设计助手。请只返回 JSON，不要返回 Markdown。
                        JSON schema: {"schemaVersion":"wp5-model-v1","cases":[{"title":"用例标题","description":"用例说明","coverageType":"SMOKE|FUNCTIONAL|EXCEPTION|BOUNDARY|PERMISSION|REGRESSION","priority":"CRITICAL|HIGH|MEDIUM|LOW","preconditions":"前置条件","steps":[{"action":"操作","expectedResult":"预期"}],"expectedResult":"整体预期","requirementRef":"需求 ID","apiRefs":[],"pageRefs":[],"flowRefs":[],"tags":["标签"],"rationale":"生成依据","riskNotes":"风险提示","confidence":0.0}]}
                        根据 WP5 上下文为每个需求和覆盖类型生成可人工评审的候选用例。
                        """,
                PromptStatus.ACTIVE,
                "WP5 AI 用例生成 MVP Prompt",
                false,
                PromptApprovalStatus.NOT_REQUIRED,
                null,
                null,
                null,
                now,
                now
        ));
        savePrompt(new PromptTemplate(
                UUID.fromString("00000000-0000-0000-0000-000000000304"),
                "wp6-api-automation-v1",
                "WP6 接口自动化用例生成助手",
                1,
                """
                        {{schemaMarker}}
                        你是企业级接口自动化用例生成助手。请只返回 JSON，不要返回 Markdown。
                        JSON schema: {"schemaVersion":"wp6-api-automation-v1","cases":[{"assetApiId":"uuid","title":"用例标题","method":"GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS","path":"/openapi/path","coverageType":"SMOKE|FUNCTIONAL|EXCEPTION","expectedStatus":200,"assertions":["STATUS_CODE"],"requestTemplate":{"aggregateOnly":true,"bodyTemplateStored":false,"secretValuesStored":false},"rationale":"生成依据"}]}
                        只能基于用户提供的 endpoint 聚合摘要生成用例；不得输出请求正文、响应正文、secret、token、cookie 或 Authorization 示例值。
                        """,
                PromptStatus.ACTIVE,
                "WP6 API automation model prompt",
                false,
                PromptApprovalStatus.NOT_REQUIRED,
                null,
                null,
                null,
                now,
                now
        ));
    }

    @Override
    public List<ModelProviderConfig> providers() {
        return providers.values()
                .stream()
                .sorted(Comparator.comparingInt(ModelProviderConfig::priority))
                .toList();
    }

    @Override
    public Optional<ModelProviderConfig> provider(UUID id) {
        return Optional.ofNullable(providers.get(id));
    }

    @Override
    public ModelProviderConfig saveProvider(ModelProviderConfig provider) {
        providers.put(provider.id(), provider);
        return provider;
    }

    @Override
    public List<PromptTemplate> prompts(String promptKey) {
        return prompts.values()
                .stream()
                .filter(prompt -> promptKey == null || prompt.promptKey().equals(promptKey))
                .sorted(Comparator.comparing(PromptTemplate::promptKey).thenComparing(PromptTemplate::version).reversed())
                .toList();
    }

    @Override
    public Optional<PromptTemplate> prompt(UUID id) {
        return Optional.ofNullable(prompts.get(id));
    }

    @Override
    public Optional<PromptTemplate> activePrompt(String promptKey) {
        return prompts.values()
                .stream()
                .filter(prompt -> prompt.promptKey().equals(promptKey))
                .filter(prompt -> prompt.status() == PromptStatus.ACTIVE)
                .max(Comparator.comparingInt(PromptTemplate::version));
    }

    @Override
    public PromptTemplate savePrompt(PromptTemplate prompt) {
        prompts.put(prompt.id(), prompt);
        return prompt;
    }

    @Override
    public void deactivateActivePrompts(String promptKey) {
        Instant now = Instant.now();
        prompts.values()
                .stream()
                .filter(prompt -> prompt.promptKey().equals(promptKey))
                .filter(prompt -> prompt.status() == PromptStatus.ACTIVE)
                .forEach(prompt -> prompts.put(prompt.id(), new PromptTemplate(
                        prompt.id(),
                        prompt.promptKey(),
                        prompt.name(),
                        prompt.version(),
                        prompt.content(),
                        PromptStatus.ARCHIVED,
                        prompt.changeNote(),
                        prompt.highRisk(),
                        prompt.approvalStatus(),
                        prompt.approvedBy(),
                        prompt.approvedAt(),
                        prompt.approvalNote(),
                        prompt.createdAt(),
                        now
                )));
    }

    @Override
    public synchronized InvocationRecord saveInvocation(InvocationRecord record) {
        invocations.add(0, record);
        return record;
    }

    @Override
    public synchronized Optional<InvocationRecord> invocation(UUID id) {
        return invocations.stream()
                .filter(record -> record.id().equals(id))
                .findFirst();
    }

    @Override
    public synchronized List<InvocationRecord> invocations(InvocationQuery query) {
        return filteredInvocations(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public synchronized long countInvocations(InvocationQuery query) {
        return filteredInvocations(query).count();
    }

    @Override
    public synchronized List<String> distinctProjectIds(Instant startTime, Instant endTime) {
        return invocations.stream()
                .filter(record -> inWindow(record, startTime, endTime))
                .map(InvocationRecord::projectId)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public synchronized List<String> distinctActorServices(Instant startTime, Instant endTime) {
        return invocations.stream()
                .filter(record -> inWindow(record, startTime, endTime))
                .map(InvocationRecord::actorService)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public synchronized InvocationSummaryResult invocationSummary(InvocationQuery query) {
        List<InvocationRecord> records = filteredInvocations(query).toList();
        long succeeded = records.stream().filter(record -> record.status() == InvocationStatus.SUCCEEDED).count();
        long failed = records.stream().filter(record -> record.status() == InvocationStatus.FAILED).count();
        long blocked = records.stream().filter(record -> record.status() == InvocationStatus.BLOCKED).count();
        long inputTokens = records.stream().mapToLong(InvocationRecord::inputTokens).sum();
        long outputTokens = records.stream().mapToLong(InvocationRecord::outputTokens).sum();
        BigDecimal totalCost = records.stream()
                .map(InvocationRecord::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvocationSummaryResult(records.size(), succeeded, failed, blocked, inputTokens, outputTokens, totalCost);
    }

    @Override
    public List<ModelAccessPolicyOverride> modelAccessPolicies(String scopeType, String scopeKey) {
        return policies.values()
                .stream()
                .filter(policy -> scopeType == null || scopeType.equals(policy.scopeType()))
                .filter(policy -> scopeKey == null || scopeKey.equals(policy.scopeKey()))
                .sorted(Comparator
                        .comparingInt(this::scopePriority)
                        .thenComparing(ModelAccessPolicyOverride::scopeKey))
                .toList();
    }

    @Override
    public Optional<ModelAccessPolicyOverride> modelAccessPolicy(String scopeType, String scopeKey) {
        return Optional.ofNullable(policies.get(policyKey(scopeType, scopeKey)));
    }

    @Override
    public ModelAccessPolicyOverride saveModelAccessPolicy(ModelAccessPolicyOverride policy) {
        policies.put(policyKey(policy.scopeType(), policy.scopeKey()), policy);
        return policy;
    }

    private java.util.stream.Stream<InvocationRecord> filteredInvocations(InvocationQuery query) {
        return invocations.stream()
                .filter(record -> query.projectId() == null || query.projectId().equals(record.projectId()))
                .filter(record -> query.applicationId() == null || query.applicationId().equals(record.applicationId()))
                .filter(record -> query.environmentId() == null || query.environmentId().equals(record.environmentId()))
                .filter(record -> query.sensitivityLevel() == null || query.sensitivityLevel().equals(record.sensitivityLevel()))
                .filter(record -> query.status() == null || query.status() == record.status())
                .filter(record -> query.providerId() == null || query.providerId().equals(record.providerId()))
                .filter(record -> query.actorService() == null || query.actorService().equals(record.actorService()))
                .filter(record -> query.roleScope() == null || query.roleScope().equals(record.roleScope()))
                .filter(record -> query.startTime() == null || !record.createdAt().isBefore(query.startTime()))
                .filter(record -> query.endTime() == null || record.createdAt().isBefore(query.endTime()));
    }

    private String policyKey(String scopeType, String scopeKey) {
        return scopeType + ":" + scopeKey;
    }

    private int scopePriority(ModelAccessPolicyOverride policy) {
        return switch (policy.scopeType()) {
            case "PLATFORM" -> 0;
            case "ROLE" -> 1;
            case "PROJECT" -> 2;
            case "ENVIRONMENT" -> 3;
            default -> 9;
        };
    }

    private boolean inWindow(InvocationRecord record, Instant startTime, Instant endTime) {
        return (startTime == null || !record.createdAt().isBefore(startTime))
                && (endTime == null || record.createdAt().isBefore(endTime));
    }
}
