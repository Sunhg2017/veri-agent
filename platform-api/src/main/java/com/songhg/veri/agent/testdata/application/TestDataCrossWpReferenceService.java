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
    private final ObjectMapper objectMapper;

    public TestDataCrossWpReferenceService(
            TestAccountLeaseService leaseService,
            TestDataRepository repository,
            TestDataPlatformContextClient contextClient,
            TestDataProperties properties,
            ObjectMapper objectMapper
    ) {
        this.leaseService = leaseService;
        this.repository = repository;
        this.contextClient = contextClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
}
