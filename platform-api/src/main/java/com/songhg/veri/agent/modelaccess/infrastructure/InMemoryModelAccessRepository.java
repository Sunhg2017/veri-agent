package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@Profile("local")
public class InMemoryModelAccessRepository implements ModelAccessRepository {

    private final Map<UUID, ModelProviderConfig> providers = new ConcurrentHashMap<>();
    private final Map<UUID, PromptTemplate> prompts = new ConcurrentHashMap<>();
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

    private java.util.stream.Stream<InvocationRecord> filteredInvocations(InvocationQuery query) {
        return invocations.stream()
                .filter(record -> query.projectId() == null || query.projectId().equals(record.projectId()))
                .filter(record -> query.applicationId() == null || query.applicationId().equals(record.applicationId()))
                .filter(record -> query.sensitivityLevel() == null || query.sensitivityLevel().equals(record.sensitivityLevel()))
                .filter(record -> query.status() == null || query.status() == record.status())
                .filter(record -> query.providerId() == null || query.providerId().equals(record.providerId()))
                .filter(record -> query.actorService() == null || query.actorService().equals(record.actorService()))
                .filter(record -> query.startTime() == null || !record.createdAt().isBefore(query.startTime()))
                .filter(record -> query.endTime() == null || record.createdAt().isBefore(query.endTime()));
    }

    private boolean inWindow(InvocationRecord record, Instant startTime, Instant endTime) {
        return (startTime == null || !record.createdAt().isBefore(startTime))
                && (endTime == null || record.createdAt().isBefore(endTime));
    }
}
