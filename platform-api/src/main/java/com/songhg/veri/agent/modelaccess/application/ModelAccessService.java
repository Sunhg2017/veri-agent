package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.command.CreatePromptCommand;
import com.songhg.veri.agent.modelaccess.application.command.CreateProviderCommand;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.command.UpdateProviderCommand;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.port.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.port.PlatformContextClient;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.CostAlertResult;
import com.songhg.veri.agent.modelaccess.application.view.CostReportResult;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCheckResult;
import com.songhg.veri.agent.modelaccess.application.view.ProviderResilienceResult;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;















@Service
public class ModelAccessService {

    private final ModelAccessRepository repository;
    private final ModelAccessProperties properties;
    private final ModelProviderManagementService providerManagementService;
    private final PromptTemplateManagementService promptTemplateManagementService;
    private final ModelInvocationService invocationService;
    private final ModelCostAnalysisService costAnalysisService;

    public ModelAccessService(
            ModelAccessRepository repository,
            List<ModelProviderClient> providerClients,
            PlatformContextClient platformContextClient,
            SensitiveContentGuard contentGuard,
            PromptRenderer promptRenderer,
            ModelAccessProperties properties,
            ModelAccessMetrics metrics,
            ProviderResilienceManager providerResilienceManager
    ) {
        this(
                repository,
                properties,
                new ModelInvocationService(
                        repository,
                        providerClients,
                        platformContextClient,
                        contentGuard,
                        promptRenderer,
                        properties,
                        metrics,
                        providerResilienceManager
                ),
                new ModelCostAnalysisService(repository, properties),
                new ModelProviderManagementService(
                        repository,
                        providerClients,
                        contentGuard,
                        properties,
                        metrics,
                        providerResilienceManager
                ),
                new PromptTemplateManagementService(repository)
        );
    }

    @Autowired
    public ModelAccessService(
            ModelAccessRepository repository,
            ModelAccessProperties properties,
            ModelInvocationService invocationService,
            ModelCostAnalysisService costAnalysisService,
            ModelProviderManagementService providerManagementService,
            PromptTemplateManagementService promptTemplateManagementService
    ) {
        this.repository = repository;
        this.properties = properties;
        this.providerManagementService = providerManagementService;
        this.promptTemplateManagementService = promptTemplateManagementService;
        this.invocationService = invocationService;
        this.costAnalysisService = costAnalysisService;
    }

    public List<ModelProviderConfig> providers() {
        return providerManagementService.providers();
    }

    public ModelProviderConfig createProvider(CreateProviderCommand request) {
        return providerManagementService.createProvider(request);
    }

    public ModelProviderConfig updateProvider(UUID id, UpdateProviderCommand request) {
        return providerManagementService.updateProvider(id, request);
    }

    public ModelProviderConfig setProviderStatus(UUID id, ProviderStatus status) {
        return providerManagementService.setProviderStatus(id, status);
    }

    public ProviderCheckResult checkProvider(UUID id) {
        return providerManagementService.checkProvider(id);
    }

    public ProviderResilienceResult providerResilience(UUID id) {
        return providerManagementService.providerResilience(id);
    }

    public ProviderResilienceResult resetProviderCircuit(UUID id) {
        return providerManagementService.resetProviderCircuit(id);
    }

    public List<PromptTemplate> prompts(String promptKey) {
        return promptTemplateManagementService.prompts(promptKey);
    }

    public PromptTemplate createPrompt(CreatePromptCommand request) {
        return promptTemplateManagementService.createPrompt(request);
    }

    public PromptTemplate activatePrompt(UUID id) {
        return promptTemplateManagementService.activatePrompt(id);
    }

    public PromptTemplate approvePrompt(UUID id, String approvedBy, String reviewNote) {
        return promptTemplateManagementService.approvePrompt(id, approvedBy, reviewNote);
    }

    public PromptTemplate approvePrompt(UUID id, String reviewNote) {
        return promptTemplateManagementService.approvePrompt(id, reviewNote);
    }

    public PromptTemplate rejectPrompt(UUID id, String approvedBy, String reviewNote) {
        return promptTemplateManagementService.rejectPrompt(id, approvedBy, reviewNote);
    }

    public PromptTemplate rejectPrompt(UUID id, String reviewNote) {
        return promptTemplateManagementService.rejectPrompt(id, reviewNote);
    }

    public ModelInvocationResult invoke(ModelInvocationCommand request, ServicePrincipal principal) {
        return invocationService.invoke(request, principal);
    }

    public List<InvocationRecord> invocations() {
        return repository.invocations(new InvocationQuery(null, null, null, null, null, null, null, null, PageQuery.of(0, 200)));
    }

    public PageResponse<InvocationRecord> invocations(InvocationQuery query) {
        InvocationQuery normalized = normalizeQuery(query);
        List<InvocationRecord> items = repository.invocations(normalized);
        long total = repository.countInvocations(normalized);
        return PageResponse.of(items, normalized.index(), normalized.size(), total);
    }

    public InvocationSummaryResult invocationSummary(InvocationQuery query) {
        return repository.invocationSummary(normalizeQuery(query));
    }

    public List<CostAlertResult> costAlerts(String projectId, String actorService) {
        return costAnalysisService.costAlerts(projectId, actorService);
    }

    public CostReportResult costReport(LocalDate startDate, LocalDate endDate, String projectId) {
        return costAnalysisService.costReport(startDate, endDate, projectId);
    }

    public void writeInvocationsCsv(InvocationQuery query, OutputStream outputStream) throws IOException {
        InvocationQuery normalized = normalizeQuery(query);
        int exportRows = properties.maxExportRows() <= 0 ? 10000 : Math.min(50000, properties.maxExportRows());
        int chunkSize = 100;
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.append("invocationId,createdAt,projectId,applicationId,environmentId,sensitivityLevel,status,")
                .append("providerId,providerName,modelName,routingRuleName,routingGroup,modelCapability,promptKey,promptVersion,fallbackUsed,")
                .append("promptDigest,inputTokens,outputTokens,totalCost,latencyMs,actorService,")
                .append("delegatedUserId,errorCode,errorMessage,requestPreview,responsePreview\n");
        int written = 0;
        int pageIndex = 0;
        while (written < exportRows) {
            InvocationQuery exportQuery = new InvocationQuery(
                    normalized.projectId(),
                    normalized.applicationId(),
                    normalized.sensitivityLevel(),
                    normalized.status(),
                    normalized.providerId(),
                    normalized.actorService(),
                    normalized.startTime(),
                    normalized.endTime(),
                    PageQuery.of(pageIndex, chunkSize)
            );
            List<InvocationRecord> records = repository.invocations(exportQuery);
            if (records.isEmpty()) {
                break;
            }
            for (InvocationRecord record : records) {
                if (written >= exportRows) {
                    break;
                }
                appendCsvRecord(writer, record);
                written++;
            }
            writer.flush();
            if (records.size() < chunkSize) {
                break;
            }
            pageIndex++;
        }
        writer.flush();
    }

    public int enabledProviderCount() {
        return providerManagementService.enabledProviderCount();
    }

    public int activePromptCount() {
        return promptTemplateManagementService.activePromptCount();
    }

    public boolean providerRateLimitEnabled() {
        return providerManagementService.providerRateLimitEnabled();
    }

    public int providerRateLimitMaxRequests() {
        return providerManagementService.providerRateLimitMaxRequests();
    }

    public long providerRateLimitWindowSeconds() {
        return providerManagementService.providerRateLimitWindowSeconds();
    }

    public boolean providerConcurrencyLimitEnabled() {
        return providerManagementService.providerConcurrencyLimitEnabled();
    }

    public int providerMaxConcurrentRequests() {
        return providerManagementService.providerMaxConcurrentRequests();
    }

    public int openCircuitProviderCount() {
        return providerManagementService.openCircuitProviderCount();
    }

    private String sensitivityLevel(String sensitivityLevel) {
        if (!StringUtils.hasText(sensitivityLevel)) {
            return "INTERNAL";
        }
        String normalized = sensitivityLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED" -> normalized;
            case "STRICT" -> "RESTRICTED";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "sensitivityLevel 仅支持 PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED");
        };
    }

    private InvocationQuery normalizeQuery(InvocationQuery query) {
        PageQuery pageQuery = PageQuery.of(query.index(), query.size());
        return new InvocationQuery(
                trimToNull(query.projectId()),
                trimToNull(query.applicationId()),
                normalizeQuerySensitivityLevel(query.sensitivityLevel()),
                query.status(),
                query.providerId(),
                trimToNull(query.actorService()),
                query.startTime(),
                query.endTime(),
                pageQuery
        );
    }

    private String normalizeQuerySensitivityLevel(String sensitivityLevel) {
        if (!StringUtils.hasText(sensitivityLevel)) {
            return null;
        }
        return sensitivityLevel(sensitivityLevel);
    }

    private void appendCsvRecord(BufferedWriter writer, InvocationRecord record) throws IOException {
        appendCsvRow(
                writer,
                record.id(),
                record.createdAt(),
                record.projectId(),
                record.applicationId(),
                record.environmentId(),
                record.sensitivityLevel(),
                record.status(),
                record.providerId(),
                record.providerName(),
                record.modelName(),
                record.routingRuleName(),
                record.routingGroup(),
                record.modelCapability(),
                record.promptKey(),
                record.promptVersion(),
                record.fallbackUsed(),
                record.promptDigest(),
                record.inputTokens(),
                record.outputTokens(),
                record.totalCost(),
                record.latencyMs(),
                record.actorService(),
                record.delegatedUserId(),
                record.errorCode(),
                record.errorMessage(),
                record.requestPreview(),
                record.responsePreview()
        );
    }

    private void appendCsvRow(BufferedWriter writer, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.append(',');
            }
            appendCsvValue(writer, values[i]);
        }
        writer.append('\n');
    }

    private void appendCsvValue(BufferedWriter writer, Object value) throws IOException {
        String text = value == null ? "" : String.valueOf(value);
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (quote) {
            writer.append('"').append(text.replace("\"", "\"\"")).append('"');
        } else {
            writer.append(text);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

}
