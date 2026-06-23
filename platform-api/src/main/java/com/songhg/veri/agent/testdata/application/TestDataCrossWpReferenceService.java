package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdata.application.command.AcquireExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.AcquireTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.ReleaseExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.ReleaseTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.TestDataReportEvidenceQuery;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.view.TestAccountLeaseResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataExecutionAccountLeaseResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataReportEvidenceResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataRunnerAccountContractResponse;
import com.songhg.veri.agent.testdata.application.view.TestPooledAccountResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDataCrossWpReferenceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_REPORT_REF_COUNT = 100;

    private final TestAccountLeaseService leaseService;
    private final TestDataRepository repository;
    private final TestDataPlatformContextClient contextClient;
    private final TestDataProperties properties;
    private final TestDataRunnerCredentialResolver runnerCredentialResolver;
    private final ObjectMapper objectMapper;

    @Autowired
    public TestDataCrossWpReferenceService(
            TestAccountLeaseService leaseService,
            TestDataRepository repository,
            TestDataPlatformContextClient contextClient,
            TestDataProperties properties,
            TestDataRunnerCredentialResolver runnerCredentialResolver,
            ObjectMapper objectMapper
    ) {
        this.leaseService = leaseService;
        this.repository = repository;
        this.contextClient = contextClient;
        this.properties = properties;
        this.runnerCredentialResolver = runnerCredentialResolver;
        this.objectMapper = objectMapper;
    }

    public TestDataCrossWpReferenceService(
            TestAccountLeaseService leaseService,
            TestDataRepository repository,
            TestDataPlatformContextClient contextClient,
            TestDataProperties properties,
            ObjectMapper objectMapper
    ) {
        this(leaseService, repository, contextClient, properties, null, objectMapper);
    }

    /**
     * WP9 receives a stable accountLeaseRef and sanitized account metadata, while the underlying lease state
     * machine remains owned by the regular WP8 lease service.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataExecutionAccountLeaseResponse acquireExecutionRunLease(
            AcquireExecutionAccountLeaseCommand command
    ) {
        TestAccountLeaseResponse lease = leaseService.acquireLease(new AcquireTestAccountLeaseCommand(
                command.projectId(),
                command.applicationId(),
                command.environmentId(),
                command.accountPoolRef(),
                command.roleTags(),
                "EXECUTION_RUN",
                command.executionRunRef(),
                command.ttlSeconds(),
                command.requestKey()
        ));
        return executionLeaseResponse(lease);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataExecutionAccountLeaseResponse releaseExecutionRunLease(
            UUID accountLeaseRef,
            ReleaseExecutionAccountLeaseCommand command
    ) {
        TestAccountLeaseResponse existing = leaseService.lease(accountLeaseRef);
        if (!"EXECUTION_RUN".equals(existing.holderType()) || !existing.holderRef().equals(command.executionRunRef())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号租借不属于当前执行运行");
        }
        TestAccountLeaseResponse released = leaseService.releaseLease(
                accountLeaseRef,
                new ReleaseTestAccountLeaseCommand(command.releaseReason(), command.accountStatus())
        );
        return executionLeaseResponse(released);
    }

    /**
     * WP7 runner can resolve account identity and secretRef digest by lease, but never receives password,
     * token, cookie, lease token plaintext or the original secret:// reference from WP8.
     */
    @Transactional(readOnly = true)
    public TestDataRunnerAccountContractResponse runnerAccountContract(UUID accountLeaseRef) {
        TestAccountLeaseResponse lease = leaseService.lease(accountLeaseRef);
        if (!"ACTIVE".equals(lease.status()) || !lease.expiresAt().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有 active 且未过期租借可交给 runner 使用");
        }
        return new TestDataRunnerAccountContractResponse(
                lease.id(),
                lease.status(),
                lease.expiresAt(),
                accountSummary(requiredAccount(lease)),
                runnerCredentialPolicy()
        );
    }

    /**
     * Exposes a runner-only resolution path keyed by lease. The control-plane contract remains digest-only; plaintext
     * is resolved solely for a trusted runner adapter and never returned to ordinary callers.
     */
    @Transactional(readOnly = true)
    public TestDataRunnerCredentialResolver.RunnerCredentialResolution resolveRunnerCredential(
            UUID accountLeaseRef,
            String projectId
    ) {
        if (runnerCredentialResolver == null) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "runner secretRef 未解析: adapter-unavailable");
        }
        return runnerCredentialResolver.resolveForUiE2e(accountLeaseRef, projectId);
    }

    @Transactional(readOnly = true)
    public boolean runnerCredentialInjectionReady() {
        return runnerCredentialResolver != null && runnerCredentialResolver.credentialInjectionReady();
    }

    /**
     * Resolves one bounded WP8 dataset binding for a WP7 step. The returned shape contains only dataset metadata,
     * record keys/digests and the stored masked summary so runtime placeholder injection stays aggregate-only.
     */
    @Transactional(readOnly = true)
    public UiE2eStepDataBindingResolution resolveUiE2eStepDataBinding(
            String projectId,
            Map<String, Object> binding
    ) {
        assertEnabled();
        BindingRequest request = bindingRequest(binding);
        TestDataSet dataSet = resolveBindingDataSet(projectId, request);
        if (!"READY".equals(dataSet.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_SET_NOT_READY");
        }
        List<TestDataRecord> activeRecords = repository.records(dataSet.id()).stream()
                .filter(record -> "ACTIVE".equals(record.status()))
                .toList();
        TestDataRecord record = resolveBindingRecord(activeRecords, request.recordKey());
        return new UiE2eStepDataBindingResolution(
                dataSet.id(),
                dataSet.code(),
                dataSet.status(),
                activeRecords.size(),
                request.bindingAlias(),
                record.recordKey(),
                record.recordDigest(),
                record.externalRefDigest(),
                readMap(record.maskedSummaryJson())
        );
    }

    /**
     * Validates the binding shape during scene writes so operators get fast feedback before bundle generation or
     * runtime execution.
     */
    @Transactional(readOnly = true)
    public UiE2eStepDataBindingValidation validateUiE2eStepDataBinding(
            String projectId,
            Map<String, Object> binding
    ) {
        assertEnabled();
        BindingRequest request = bindingRequest(binding);
        TestDataSet dataSet = resolveBindingDataSet(projectId, request);
        if (!"READY".equals(dataSet.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_SET_NOT_READY");
        }
        List<TestDataRecord> activeRecords = repository.records(dataSet.id()).stream()
                .filter(record -> "ACTIVE".equals(record.status()))
                .toList();
        resolveBindingRecord(activeRecords, request.recordKey());
        return new UiE2eStepDataBindingValidation(
                dataSet.id(),
                dataSet.code(),
                dataSet.status(),
                activeRecords.size(),
                request.bindingAlias()
        );
    }

    /**
     * WP10 report evidence is deliberately aggregate-only. It exposes references, states, counts and digests,
     * but not record bodies, secret references, runner credentials or cleanup result payload values.
     */
    @Transactional(readOnly = true)
    public TestDataReportEvidenceResponse reportEvidence(TestDataReportEvidenceQuery query) {
        assertEnabled();
        String projectId = contextClient.projectContext(query.projectId()).resourceId();
        List<UUID> dataSetRefs = boundedRefs(query.dataSetRefs());
        List<UUID> leaseRefs = boundedRefs(query.accountLeaseRefs());
        List<UUID> taskRefs = boundedRefs(query.cleanupTaskRefs());
        return new TestDataReportEvidenceResponse(
                projectId,
                boundedNullable(query.reportRef(), 128),
                dataSetRefs.stream().map(ref -> dataSetEvidence(ref, projectId)).toList(),
                leaseRefs.stream().map(ref -> leaseEvidence(ref, projectId)).toList(),
                taskRefs.stream().map(ref -> cleanupTaskEvidence(ref, projectId)).toList(),
                reportRedactionPolicy()
        );
    }

    private TestDataExecutionAccountLeaseResponse executionLeaseResponse(TestAccountLeaseResponse lease) {
        return new TestDataExecutionAccountLeaseResponse(
                lease.id(),
                lease.projectId(),
                lease.status(),
                lease.expiresAt(),
                lease.releasedAt(),
                accountSummary(requiredAccount(lease)),
                executionLeasePolicy()
        );
    }

    private TestDataReportEvidenceResponse.DataSetEvidence dataSetEvidence(UUID ref, String projectId) {
        TestDataSet dataSet = repository.dataSet(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试数据集不存在"));
        assertSameProject(dataSet.projectId(), projectId);
        Map<String, Object> schema = readMap(dataSet.schemaJson());
        return new TestDataReportEvidenceResponse.DataSetEvidence(
                dataSet.id(),
                dataSet.applicationId(),
                dataSet.environmentId(),
                dataSet.code(),
                dataSet.status(),
                dataSet.sensitivityLevel(),
                schemaFieldCount(schema),
                repository.countRecords(dataSet.id()),
                digestMap(readMap(dataSet.cleanupPolicyJson())),
                dataSet.sourceRefDigest()
        );
    }

    private TestDataReportEvidenceResponse.AccountLeaseEvidence leaseEvidence(UUID ref, String projectId) {
        TestAccountLeaseResponse lease = leaseService.lease(ref);
        assertSameProject(lease.projectId(), projectId);
        return new TestDataReportEvidenceResponse.AccountLeaseEvidence(
                lease.id(),
                lease.status(),
                lease.holderType(),
                lease.holderRef(),
                lease.expiresAt(),
                lease.releasedAt(),
                lease.account() == null ? null : accountSummary(lease.account())
        );
    }

    private TestDataReportEvidenceResponse.CleanupTaskEvidence cleanupTaskEvidence(UUID ref, String projectId) {
        TestDataTask task = repository.dataTask(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试数据任务不存在"));
        assertSameProject(task.projectId(), projectId);
        Map<String, Object> resultSummary = readMap(task.resultSummaryJson());
        return new TestDataReportEvidenceResponse.CleanupTaskEvidence(
                task.id(),
                task.dataSetId(),
                task.taskType(),
                task.status(),
                digestNullable(task.targetRef()),
                task.attempt(),
                digestMap(resultSummary),
                resultSummary.keySet().stream().sorted().toList(),
                task.errorCode(),
                digestNullable(task.errorSummary()),
                task.traceId(),
                task.startedAt(),
                task.finishedAt()
        );
    }

    private TestDataCrossWpAccountSummary accountSummary(TestPooledAccountResponse account) {
        return new TestDataCrossWpAccountSummary(
                account.id(),
                account.poolId(),
                account.projectId(),
                account.accountKey(),
                account.displayName(),
                account.status(),
                account.roleTags(),
                account.scopeSummary(),
                account.secretRefDigest(),
                account.lastHealthStatus()
        );
    }

    private TestPooledAccountResponse requiredAccount(TestAccountLeaseResponse lease) {
        if (lease.account() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "账号租借缺少账号摘要");
        }
        return lease.account();
    }

    private List<UUID> boundedRefs(List<UUID> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
        refs.stream().filter(ref -> ref != null).forEach(deduplicated::add);
        List<UUID> normalized = deduplicated.stream().toList();
        if (normalized.size() > MAX_REPORT_REF_COUNT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "报告引用数量超过上限");
        }
        return normalized;
    }

    private int schemaFieldCount(Map<String, Object> schema) {
        Object fields = schema.get("fields");
        return fields instanceof List<?> list ? list.size() : 0;
    }

    private Map<String, Object> executionLeasePolicy() {
        return Map.of(
                "wp9StoresAccountLeaseRefOnly", true,
                "secretPlaintextReturned", false,
                "secretRefPlaintextReturned", false,
                "leaseDigestReturned", false,
                "leaseTokenPlaintextReturned", false,
                "crossWpTableAccessAllowed", false
        );
    }

    private Map<String, Object> runnerCredentialPolicy() {
        return Map.of(
                "runnerReceivesPasswordPlaintext", false,
                "runnerReceivesTokenPlaintext", false,
                "runnerReceivesCookiePlaintext", false,
                "secretRefPlaintextReturned", false,
                "secretRefDigestReturned", true,
                "leaseTokenPlaintextReturned", false
        );
    }

    private Map<String, Object> reportRedactionPolicy() {
        return Map.of(
                "secretPlaintextReturned", false,
                "secretRefPlaintextReturned", false,
                "rawRecordPayloadReturned", false,
                "cleanupResultPayloadReturned", false,
                "targetRefReturnedAsDigest", true,
                "errorSummaryReturnedAsDigest", true
        );
    }

    private void assertSameProject(String actualProjectId, String expectedProjectId) {
        if (!expectedProjectId.equals(actualProjectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "跨 WP 引用不属于当前项目");
        }
    }

    private BindingRequest bindingRequest(Map<String, Object> binding) {
        if (binding == null || binding.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_BINDING_INVALID");
        }
        UUID dataSetId = uuidOrNull(readText(binding, "dataSetId", "dataSetRef"));
        String dataSetCode = boundedNullable(readText(binding, "dataSetCode"), 128);
        if (dataSetId == null && !StringUtils.hasText(dataSetCode)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_BINDING_INVALID");
        }
        String bindingAlias = boundedNullable(readText(binding, "bindingAlias", "alias"), 64);
        if (!StringUtils.hasText(bindingAlias)) {
            bindingAlias = "data";
        }
        if (!bindingAlias.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_BINDING_INVALID");
        }
        String recordKey = boundedNullable(readText(binding, "recordKey"), 128);
        return new BindingRequest(dataSetId, dataSetCode, recordKey, bindingAlias);
    }

    private TestDataSet resolveBindingDataSet(String projectId, BindingRequest request) {
        String scopedProjectId = contextClient.projectContext(projectId).resourceId();
        TestDataSet byId = request.dataSetId() == null
                ? null
                : repository.dataSet(request.dataSetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_SET_NOT_FOUND"));
        TestDataSet byCode = StringUtils.hasText(request.dataSetCode())
                ? repository.dataSetByProjectAndCode(scopedProjectId, request.dataSetCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_SET_NOT_FOUND"))
                : null;
        if (byId != null && byCode != null && !byId.id().equals(byCode.id())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_BINDING_INVALID");
        }
        TestDataSet dataSet = byId != null ? byId : byCode;
        if (dataSet == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_SET_NOT_FOUND");
        }
        assertSameProject(dataSet.projectId(), scopedProjectId);
        return dataSet;
    }

    private TestDataRecord resolveBindingRecord(List<TestDataRecord> activeRecords, String recordKey) {
        if (StringUtils.hasText(recordKey)) {
            return activeRecords.stream()
                    .filter(record -> recordKey.equals(record.recordKey()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_RECORD_NOT_FOUND"));
        }
        if (activeRecords.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_RECORD_NOT_FOUND");
        }
        if (activeRecords.size() > 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_RECORD_AMBIGUOUS");
        }
        return activeRecords.get(0);
    }

    private String readText(Map<String, Object> value, String... keys) {
        if (value == null || value.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object raw = value.get(key);
            if (raw != null && StringUtils.hasText(raw.toString())) {
                return raw.toString().trim();
            }
        }
        return null;
    }

    private UUID uuidOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_TEST_DATA_BINDING_INVALID");
        }
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文本长度超过上限");
        }
        return trimmed;
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP8 测试数据控制面已关闭");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "跨 WP 摘要 JSON 读取失败");
        }
    }

    private String digestMap(Map<String, Object> value) {
        try {
            return sha256(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "跨 WP 摘要 JSON 无法序列化");
        }
    }

    private String digestNullable(String value) {
        return StringUtils.hasText(value) ? sha256(value.trim()) : null;
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "跨 WP 摘要算法不可用");
        }
    }

    private record BindingRequest(
            UUID dataSetId,
            String dataSetCode,
            String recordKey,
            String bindingAlias
    ) {
    }

    public record UiE2eStepDataBindingValidation(
            UUID dataSetId,
            String dataSetCode,
            String dataSetStatus,
            long activeRecordCount,
            String bindingAlias
    ) {
    }

    public record UiE2eStepDataBindingResolution(
            UUID dataSetId,
            String dataSetCode,
            String dataSetStatus,
            long recordCount,
            String bindingAlias,
            String recordKey,
            String recordDigest,
            String externalRefDigest,
            Map<String, Object> maskedSummary
    ) {
    }
}
