package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.testdata.application.command.AcquireTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.ReleaseTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.RenewTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestAccountLeasePageRequest;
import com.songhg.veri.agent.testdata.application.query.TestAccountLeaseQuery;
import com.songhg.veri.agent.testdata.application.view.TestAccountLeaseResponse;
import com.songhg.veri.agent.testdata.application.view.TestPooledAccountResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestAccountLease;
import com.songhg.veri.agent.testdata.domain.TestAccountPool;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestAccountLeaseService {

    private static final Pattern HOLDER_REF_PATTERN = Pattern.compile("^[A-Za-z0-9_.:@/-]{1,128}$");
    private static final Pattern REQUEST_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:@/-]{1,128}$");
    private static final Pattern ROLE_TAG_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    private static final Set<String> HOLDER_TYPES = Set.of("MANUAL", "EXECUTION_RUN", "UI_E2E_RUN", "API_AUTOMATION_RUN");
    private static final Set<String> LEASE_STATUSES = Set.of("ACTIVE", "RELEASED", "EXPIRED", "REVOKED");
    private static final Set<String> RELEASE_ACCOUNT_STATUSES = Set.of("AVAILABLE", "LOCKED");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final TestDataRepository repository;
    private final TestDataPlatformContextClient contextClient;
    private final TestDataActorResolver actorResolver;
    private final TestDataProperties properties;
    private final ObjectMapper objectMapper;

    public TestAccountLeaseService(
            TestDataRepository repository,
            TestDataPlatformContextClient contextClient,
            TestDataActorResolver actorResolver,
            TestDataProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Acquires exactly one account by requestKey. The selected account is switched from AVAILABLE to LEASED
     * before the active lease row is inserted, so DB constraints and conditional updates guard concurrent callers.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TestAccountLeaseResponse acquireLease(AcquireTestAccountLeaseCommand command) {
        assertEnabled();
        String projectId = contextClient.projectContext(command.projectId()).resourceId();
        String requestKey = boundedRequestKey(command.requestKey());
        String requestDigest = requestDigest(command, projectId);
        return repository.accountLeaseByProjectAndRequestKey(projectId, requestKey)
                .map(existing -> response(assertSameAcquireRequest(existing, requestDigest)))
                .orElseGet(() -> acquireNewLease(command, projectId, requestKey, requestDigest));
    }

    @Transactional(readOnly = true)
    public PageResponse<TestAccountLeaseResponse> leases(TestAccountLeasePageRequest request) {
        assertEnabled();
        TestAccountLeaseQuery query = normalizeQuery(request.toQuery());
        List<TestAccountLeaseResponse> items = repository.accountLeases(query).stream()
                .map(this::response)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countAccountLeases(query));
    }

    @Transactional(readOnly = true)
    public TestAccountLeaseResponse lease(UUID id) {
        assertEnabled();
        return response(requireLease(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestAccountLeaseResponse renewLease(UUID id, RenewTestAccountLeaseCommand command) {
        assertEnabled();
        TestAccountLease existing = requireLease(id);
        Instant now = Instant.now();
        assertActive(existing, now, "过期或终态租借不可续租");
        TestAccountPool pool = requirePool(existing.poolId());
        int ttl = normalizeTtl(command.ttlSeconds(), pool.defaultTtlSeconds());
        TestAccountLease renewed = new TestAccountLease(
                existing.id(),
                existing.poolId(),
                existing.accountId(),
                existing.projectId(),
                "ACTIVE",
                existing.holderType(),
                existing.holderRef(),
                existing.requestKey(),
                existing.requestDigest(),
                existing.leaseTokenDigest(),
                now.plusSeconds(ttl),
                existing.releasedAt(),
                existing.releaseReason(),
                existing.createdBy(),
                existing.createdAt(),
                now
        );
        if (!repository.renewActiveAccountLease(renewed)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "租借已进入终态，不能续租");
        }
        auditLease(renewed, "test_data.lease.renewed", Map.of("ttlSeconds", ttl, "status", renewed.status()));
        return response(renewed);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestAccountLeaseResponse releaseLease(UUID id, ReleaseTestAccountLeaseCommand command) {
        assertEnabled();
        TestAccountLease existing = requireLease(id);
        if ("RELEASED".equals(existing.status()) || "REVOKED".equals(existing.status())) {
            return response(existing);
        }
        Instant now = Instant.now();
        if ("EXPIRED".equals(existing.status()) || !existing.expiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "过期租借不能释放");
        }
        String nextAccountStatus = normalizeReleaseAccountStatus(command.accountStatus());
        TestAccountLease released = new TestAccountLease(
                existing.id(),
                existing.poolId(),
                existing.accountId(),
                existing.projectId(),
                "RELEASED",
                existing.holderType(),
                existing.holderRef(),
                existing.requestKey(),
                existing.requestDigest(),
                existing.leaseTokenDigest(),
                existing.expiresAt(),
                now,
                boundedNullable(command.releaseReason(), 256),
                existing.createdBy(),
                existing.createdAt(),
                now
        );
        if (!repository.releaseActiveAccountLease(released)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "租借已进入终态，不能释放");
        }
        repository.updateAccountStatus(existing.accountId(), nextAccountStatus, actorResolver.currentActor());
        auditLease(released, "test_data.lease.released", Map.of(
                "accountStatus", nextAccountStatus,
                "status", released.status()
        ));
        return response(released);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public int expireActiveLeases(Instant now, int limit) {
        assertEnabled();
        int count = 0;
        for (TestAccountLease lease : repository.activeExpiredLeases(now, Math.max(1, Math.min(limit, 500)))) {
            TestAccountLease expired = new TestAccountLease(
                    lease.id(),
                    lease.poolId(),
                    lease.accountId(),
                    lease.projectId(),
                    "EXPIRED",
                    lease.holderType(),
                    lease.holderRef(),
                    lease.requestKey(),
                    lease.requestDigest(),
                    lease.leaseTokenDigest(),
                    lease.expiresAt(),
                    null,
                    "lease expired",
                    lease.createdBy(),
                    lease.createdAt(),
                    now
            );
            if (repository.expireActiveAccountLease(expired)) {
                repository.updateAccountStatus(lease.accountId(), "EXPIRED", "wp8-expire-recovery");
                auditLease(expired, "test_data.lease.expired", Map.of("status", expired.status()));
                count++;
            }
        }
        return count;
    }

    public String accountLeaseProjectScopeId(UUID id) {
        return repository.accountLeaseProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号租借不存在"));
    }

    private TestAccountLeaseResponse acquireNewLease(
            AcquireTestAccountLeaseCommand command,
            String projectId,
            String requestKey,
            String requestDigest
    ) {
        TestAccountPool pool = requirePool(command.poolId());
        assertPoolReady(pool, projectId, command.applicationId(), command.environmentId());
        List<String> roleTags = normalizedRoleTags(command.roleTags());
        TestPooledAccount account = repository.firstAvailableAccount(pool.id(), roleTags)
                .orElse(null);
        if (account == null) {
            return replayExistingLeaseOrConflict(projectId, requestKey, requestDigest, "没有可租借账号");
        }
        String actor = actorResolver.currentActor();
        if (!repository.markAccountLeased(account.id(), actor)) {
            return replayExistingLeaseOrConflict(projectId, requestKey, requestDigest, "账号已被其他执行租借");
        }
        Instant now = Instant.now();
        int ttl = normalizeTtl(command.ttlSeconds(), pool.defaultTtlSeconds());
        TestAccountLease lease = new TestAccountLease(
                UUID.randomUUID(),
                pool.id(),
                account.id(),
                projectId,
                "ACTIVE",
                normalizeHolderType(command.holderType()),
                boundedHolderRef(command.holderRef()),
                requestKey,
                requestDigest,
                leaseTokenDigest(projectId, requestKey, account.id(), now),
                now.plusSeconds(ttl),
                null,
                null,
                actor,
                now,
                now
        );
        if (!repository.insertAccountLeaseIfAbsent(lease)) {
            repository.updateAccountStatus(account.id(), "AVAILABLE", actor);
            return response(repository.accountLeaseByProjectAndRequestKey(projectId, requestKey)
                    .map(existing -> assertSameAcquireRequest(existing, requestDigest))
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "账号租借请求已存在或账号已被占用")));
        }
        auditLease(lease, "test_data.lease.acquired", Map.of(
                "accountId", account.id().toString(),
                "ttlSeconds", ttl,
                "roleTags", roleTags
        ));
        return response(lease);
    }

    private TestAccountLeaseResponse replayExistingLeaseOrConflict(
            String projectId,
            String requestKey,
            String requestDigest,
            String message
    ) {
        return response(repository.accountLeaseByProjectAndRequestKey(projectId, requestKey)
                .map(existing -> assertSameAcquireRequest(existing, requestDigest))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, message)));
    }

    private TestAccountLease assertSameAcquireRequest(TestAccountLease existing, String requestDigest) {
        if (StringUtils.hasText(existing.requestDigest()) && existing.requestDigest().equals(requestDigest)) {
            return existing;
        }
        if (!StringUtils.hasText(existing.requestDigest())) {
            return existing;
        }
        throw new BusinessException(ErrorCode.CONFLICT, "租借 requestKey 已被不同请求占用");
    }

    private void assertPoolReady(TestAccountPool pool, String projectId, String applicationId, String environmentId) {
        if (!projectId.equals(pool.projectId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号池不属于当前项目");
        }
        if (!"READY".equals(pool.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "账号池未处于 READY 状态");
        }
        if (StringUtils.hasText(applicationId) && !applicationId.equals(pool.applicationId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借应用与账号池不匹配");
        }
        if (StringUtils.hasText(environmentId) && !environmentId.equals(pool.environmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借环境与账号池不匹配");
        }
    }

    private TestAccountLeaseQuery normalizeQuery(TestAccountLeaseQuery query) {
        String status = query.status() == null ? null : normalizeLeaseStatus(query.status());
        return new TestAccountLeaseQuery(
                query.projectId() == null ? null : contextClient.projectContext(query.projectId()).resourceId(),
                query.poolId(),
                query.accountId(),
                status,
                query.holderRef(),
                query.offset(),
                query.limit()
        );
    }

    private TestAccountLease requireLease(UUID id) {
        return repository.accountLease(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号租借不存在"));
    }

    private TestAccountPool requirePool(UUID id) {
        return repository.accountPool(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号池不存在"));
    }

    private void assertActive(TestAccountLease lease, Instant now, String message) {
        if (!"ACTIVE".equals(lease.status()) || !lease.expiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, message);
        }
    }

    private TestAccountLeaseResponse response(TestAccountLease lease) {
        return new TestAccountLeaseResponse(
                lease.id(),
                lease.poolId(),
                lease.accountId(),
                lease.projectId(),
                lease.status(),
                lease.holderType(),
                lease.holderRef(),
                lease.requestKey(),
                lease.leaseTokenDigest(),
                lease.expiresAt(),
                lease.releasedAt(),
                lease.releaseReason(),
                repository.pooledAccount(lease.accountId()).map(this::accountResponse).orElse(null),
                policy(),
                lease.createdAt(),
                lease.updatedAt()
        );
    }

    private TestPooledAccountResponse accountResponse(TestPooledAccount account) {
        return new TestPooledAccountResponse(
                account.id(),
                account.poolId(),
                account.projectId(),
                account.accountKey(),
                account.displayName(),
                account.status(),
                readStringList(account.roleTagsJson()),
                readMap(account.scopeSummaryJson()),
                account.secretRefDigest(),
                account.lastHealthStatus(),
                account.lastHealthSummary(),
                account.archivedAt(),
                account.createdAt(),
                account.updatedAt()
        );
    }

    private Map<String, Object> policy() {
        return Map.of(
                "secretPlaintextReturned", false,
                "leaseTokenPlaintextReturned", false,
                "activeLeaseUniquePerAccount", true,
                "destructiveCleanupTriggered", false
        );
    }

    private void auditLease(TestAccountLease lease, String action, Map<String, Object> afterJson) {
        contextClient.writeAuditEvent(action, "TEST_ACCOUNT_LEASE", lease.id().toString(), lease.projectId(), "SUCCESS", afterJson);
    }

    private String normalizeHolderType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
        if (!HOLDER_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借 holderType 非法");
        }
        return normalized;
    }

    private String normalizeLeaseStatus(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!LEASE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借状态非法");
        }
        return normalized;
    }

    private String normalizeReleaseAccountStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "AVAILABLE";
        if (!RELEASE_ACCOUNT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "释放后账号状态非法");
        }
        return normalized;
    }

    private List<String> normalizedRoleTags(List<String> roleTags) {
        if (roleTags == null) {
            return List.of();
        }
        List<String> normalized = roleTags.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        normalized.forEach(tag -> {
            if (!ROLE_TAG_PATTERN.matcher(tag).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借角色标签格式非法");
            }
        });
        return normalized;
    }

    private int normalizeTtl(Integer value, int defaultValue) {
        int ttl = value == null ? defaultValue : value;
        if (ttl < 1 || ttl > properties.effectiveMaxLeaseTtlSeconds()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借 TTL 超过允许范围");
        }
        return ttl;
    }

    private String boundedHolderRef(String value) {
        String ref = boundedText(value, 128);
        if (!HOLDER_REF_PATTERN.matcher(ref).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借 holderRef 格式非法");
        }
        return ref;
    }

    private String boundedRequestKey(String value) {
        String key = boundedText(value, 128);
        if (!REQUEST_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租借 requestKey 格式非法");
        }
        return key;
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

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必填文本不能为空");
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

    private String leaseTokenDigest(String projectId, String requestKey, UUID accountId, Instant now) {
        return sha256(projectId + ":" + requestKey + ":" + accountId + ":" + now.toEpochMilli(), "租借 token 摘要算法不可用");
    }

    private String requestDigest(AcquireTestAccountLeaseCommand command, String projectId) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("projectId", projectId);
        normalized.put("applicationId", boundedNullable(command.applicationId(), 64));
        normalized.put("environmentId", boundedNullable(command.environmentId(), 64));
        normalized.put("poolId", command.poolId().toString());
        normalized.put("roleTags", normalizedRoleTags(command.roleTags()));
        normalized.put("holderType", normalizeHolderType(command.holderType()));
        normalized.put("holderRef", boundedHolderRef(command.holderRef()));
        normalized.put("ttlSeconds", command.ttlSeconds());
        try {
            return sha256(objectMapper.writeValueAsString(normalized), "租借请求摘要算法不可用");
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "租借请求摘要无法序列化");
        }
    }

    private String sha256(String source, String errorMessage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, errorMessage);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "账号摘要 JSON 读取失败");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "账号角色标签读取失败");
        }
    }
}
