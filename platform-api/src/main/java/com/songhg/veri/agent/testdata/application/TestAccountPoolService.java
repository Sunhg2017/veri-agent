package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpdateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpdateTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolPageRequest;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolQuery;
import com.songhg.veri.agent.testdata.application.view.TestAccountPoolDetailResponse;
import com.songhg.veri.agent.testdata.application.view.TestAccountPoolSummaryResponse;
import com.songhg.veri.agent.testdata.application.view.TestPooledAccountResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestAccountPoolService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Pattern ACCOUNT_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.@:-]{1,128}$");
    private static final Pattern ROLE_TAG_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    private static final Pattern SECRET_REF_PATTERN = Pattern.compile("^secret://[A-Za-z0-9][A-Za-z0-9._~:/@+_-]{0,238}$");
    private static final List<String> POOL_STATUS_VALUES = List.of("DRAFT", "READY", "DISABLED", "ARCHIVED");
    private static final Set<String> POOL_STATUSES = Set.copyOf(POOL_STATUS_VALUES);
    private static final Set<String> POOL_WRITABLE_STATUSES = Set.of("DRAFT", "READY", "DISABLED");
    private static final List<String> ACCOUNT_STATUS_VALUES = List.of(
            "AVAILABLE",
            "LEASED",
            "LOCKED",
            "EXPIRED",
            "DISABLED",
            "ARCHIVED"
    );
    private static final Set<String> ACCOUNT_STATUSES = Set.copyOf(ACCOUNT_STATUS_VALUES);
    private static final Set<String> ACCOUNT_MANAGED_STATUSES = Set.of(
            "AVAILABLE",
            "LOCKED",
            "DISABLED",
            "ARCHIVED"
    );
    private static final Set<String> HEALTH_STATUSES = Set.of("UNKNOWN", "HEALTHY", "UNHEALTHY", "LOCKED");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final TestDataRepository repository;
    private final TestDataPlatformContextClient contextClient;
    private final TestDataActorResolver actorResolver;
    private final TestDataProperties properties;
    private final ObjectMapper objectMapper;

    public TestAccountPoolService(
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

    @Transactional(noRollbackFor = BusinessException.class)
    public TestAccountPoolDetailResponse createAccountPool(CreateTestAccountPoolCommand command) {
        assertEnabled();
        PlatformContext context = contextClient.projectContext(command.projectId());
        String projectId = context.resourceId();
        String code = boundedCode(command.code());
        repository.accountPoolByProjectAndCode(projectId, code).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CONFLICT, "账号池 code 已存在");
        });
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        TestAccountPool pool = new TestAccountPool(
                UUID.randomUUID(),
                projectId,
                boundedNullable(command.applicationId(), 64),
                boundedNullable(command.environmentId(), 64),
                code,
                boundedText(command.name(), 128),
                normalizePoolWritableStatus(command.status(), "DRAFT"),
                json(safeObject(command.leasePolicy())),
                normalizeTtl(command.defaultTtlSeconds(), properties.effectiveDefaultLeaseTtlSeconds()),
                actor,
                actor,
                null,
                now,
                now
        );
        try {
            repository.insertAccountPool(pool);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号池 code 已存在");
        }
        auditPool(pool, "test_data.account_pool.created", Map.of(
                "status", pool.status(),
                "accountCount", 0,
                "defaultTtlSeconds", pool.defaultTtlSeconds()
        ));
        return detail(pool);
    }

    @Transactional(readOnly = true)
    public PageResponse<TestAccountPoolSummaryResponse> accountPools(TestAccountPoolPageRequest request) {
        assertEnabled();
        TestAccountPoolQuery query = normalizeQuery(request.toQuery());
        List<TestAccountPoolSummaryResponse> items = repository.accountPools(query).stream()
                .map(this::summary)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countAccountPools(query));
    }

    @Transactional(readOnly = true)
    public TestAccountPoolDetailResponse accountPool(UUID id) {
        assertEnabled();
        return detail(requirePool(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestAccountPoolDetailResponse updateAccountPool(UUID id, UpdateTestAccountPoolCommand command) {
        assertEnabled();
        TestAccountPool existing = requirePool(id);
        assertMutablePool(existing);
        Instant now = Instant.now();
        TestAccountPool updated = new TestAccountPool(
                existing.id(),
                existing.projectId(),
                command.applicationId() == null ? existing.applicationId() : boundedNullable(command.applicationId(), 64),
                command.environmentId() == null ? existing.environmentId() : boundedNullable(command.environmentId(), 64),
                existing.code(),
                StringUtils.hasText(command.name()) ? boundedText(command.name(), 128) : existing.name(),
                command.status() == null ? existing.status() : normalizePoolWritableStatus(command.status(), existing.status()),
                command.leasePolicy() == null ? existing.leasePolicyJson() : json(safeObject(command.leasePolicy())),
                command.defaultTtlSeconds() == null
                        ? existing.defaultTtlSeconds()
                        : normalizeTtl(command.defaultTtlSeconds(), existing.defaultTtlSeconds()),
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.archivedAt(),
                existing.createdAt(),
                now
        );
        repository.updateAccountPool(updated);
        auditPool(updated, "test_data.account_pool.updated", poolAuditPayload(updated));
        return detail(updated);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestAccountPoolDetailResponse disableAccountPool(UUID id) {
        assertEnabled();
        TestAccountPool existing = requirePool(id);
        if ("DISABLED".equals(existing.status())) {
            return detail(existing);
        }
        assertMutablePool(existing);
        TestAccountPool disabled = new TestAccountPool(
                existing.id(),
                existing.projectId(),
                existing.applicationId(),
                existing.environmentId(),
                existing.code(),
                existing.name(),
                "DISABLED",
                existing.leasePolicyJson(),
                existing.defaultTtlSeconds(),
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.archivedAt(),
                existing.createdAt(),
                Instant.now()
        );
        repository.updateAccountPool(disabled);
        auditPool(disabled, "test_data.account_pool.updated", poolAuditPayload(disabled));
        return detail(disabled);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestAccountPoolDetailResponse archiveAccountPool(UUID id) {
        assertEnabled();
        TestAccountPool existing = requirePool(id);
        if ("ARCHIVED".equals(existing.status())) {
            return detail(existing);
        }
        Instant now = Instant.now();
        TestAccountPool archived = new TestAccountPool(
                existing.id(),
                existing.projectId(),
                existing.applicationId(),
                existing.environmentId(),
                existing.code(),
                existing.name(),
                "ARCHIVED",
                existing.leasePolicyJson(),
                existing.defaultTtlSeconds(),
                existing.createdBy(),
                actorResolver.currentActor(),
                now,
                existing.createdAt(),
                now
        );
        repository.archiveAccountPool(archived);
        auditPool(archived, "test_data.account_pool.archived", poolAuditPayload(archived));
        return detail(archived);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestPooledAccountResponse addAccount(UUID poolId, UpsertTestPooledAccountCommand command) {
        assertEnabled();
        TestAccountPool pool = requirePool(poolId);
        if ("DISABLED".equals(pool.status()) || "ARCHIVED".equals(pool.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前账号池状态不可新增账号");
        }
        String accountKey = boundedAccountKey(command.accountKey());
        repository.pooledAccountByPoolAndKey(poolId, accountKey).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CONFLICT, "账号 key 已存在");
        });
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        TestPooledAccount account = new TestPooledAccount(
                UUID.randomUUID(),
                pool.id(),
                pool.projectId(),
                accountKey,
                boundedNullable(command.displayName(), 128),
                normalizeManagedAccountStatus(command.status(), "AVAILABLE"),
                json(normalizedRoleTags(command.roleTags())),
                json(safeObject(command.scopeSummary())),
                digestSecretRef(command.secretRef()),
                normalizeHealthStatus(command.lastHealthStatus()),
                boundedNullable(command.lastHealthSummary(), 512),
                actor,
                actor,
                null,
                now,
                now
        );
        try {
            repository.insertPooledAccount(account);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号 key 已存在");
        }
        auditAccount(account, "created");
        return accountResponse(account);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestPooledAccountResponse updateAccount(UUID id, UpdateTestPooledAccountCommand command) {
        assertEnabled();
        TestPooledAccount existing = requireAccount(id);
        if ("ARCHIVED".equals(existing.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已归档账号不可修改");
        }
        TestAccountPool pool = requirePool(existing.poolId());
        if ("ARCHIVED".equals(pool.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已归档账号池不可维护账号");
        }
        String nextStatus = command.status() == null
                ? existing.status()
                : normalizeManagedAccountStatus(command.status(), existing.status());
        Instant now = Instant.now();
        TestPooledAccount updated = new TestPooledAccount(
                existing.id(),
                existing.poolId(),
                existing.projectId(),
                existing.accountKey(),
                command.displayName() == null ? existing.displayName() : boundedNullable(command.displayName(), 128),
                nextStatus,
                command.roleTags() == null ? existing.roleTagsJson() : json(normalizedRoleTags(command.roleTags())),
                command.scopeSummary() == null ? existing.scopeSummaryJson() : json(safeObject(command.scopeSummary())),
                command.secretRef() == null ? existing.secretRefDigest() : digestSecretRef(command.secretRef()),
                command.lastHealthStatus() == null
                        ? existing.lastHealthStatus()
                        : normalizeHealthStatus(command.lastHealthStatus()),
                command.lastHealthSummary() == null
                        ? existing.lastHealthSummary()
                        : boundedNullable(command.lastHealthSummary(), 512),
                existing.createdBy(),
                actorResolver.currentActor(),
                "ARCHIVED".equals(nextStatus) ? now : existing.archivedAt(),
                existing.createdAt(),
                now
        );
        repository.updatePooledAccount(updated);
        auditAccount(updated, "updated");
        return accountResponse(updated);
    }

    public String accountPoolProjectScopeId(UUID id) {
        return repository.accountPoolProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号池不存在"));
    }

    public String pooledAccountProjectScopeId(UUID id) {
        return repository.pooledAccountProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));
    }

    private TestAccountPool requirePool(UUID id) {
        return repository.accountPool(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号池不存在"));
    }

    private TestPooledAccount requireAccount(UUID id) {
        return repository.pooledAccount(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));
    }

    private void assertMutablePool(TestAccountPool pool) {
        if ("ARCHIVED".equals(pool.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已归档账号池不可修改");
        }
    }

    private TestAccountPoolQuery normalizeQuery(TestAccountPoolQuery query) {
        String status = query.status() == null ? null : normalizePoolStatus(query.status(), null);
        return new TestAccountPoolQuery(
                query.projectId() == null ? null : contextClient.projectContext(query.projectId()).resourceId(),
                query.applicationId(),
                query.environmentId(),
                status,
                query.keyword(),
                query.offset(),
                query.limit()
        );
    }

    private TestAccountPoolDetailResponse detail(TestAccountPool pool) {
        return new TestAccountPoolDetailResponse(
                pool.id(),
                pool.projectId(),
                pool.applicationId(),
                pool.environmentId(),
                pool.code(),
                pool.name(),
                pool.status(),
                readMap(pool.leasePolicyJson()),
                pool.defaultTtlSeconds(),
                repository.pooledAccounts(pool.id()).stream().map(this::accountResponse).toList(),
                policy(),
                pool.archivedAt(),
                pool.createdAt(),
                pool.updatedAt()
        );
    }

    private TestAccountPoolSummaryResponse summary(TestAccountPool pool) {
        return new TestAccountPoolSummaryResponse(
                pool.id(),
                pool.projectId(),
                pool.applicationId(),
                pool.environmentId(),
                pool.code(),
                pool.name(),
                pool.status(),
                readMap(pool.leasePolicyJson()),
                pool.defaultTtlSeconds(),
                repository.countPooledAccounts(pool.id(), null),
                repository.countPooledAccounts(pool.id(), "AVAILABLE"),
                repository.countPooledAccounts(pool.id(), "LOCKED"),
                repository.countPooledAccounts(pool.id(), "DISABLED"),
                pool.archivedAt(),
                pool.createdAt(),
                pool.updatedAt()
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
                "secretPlaintextStored", false,
                "secretRefPlaintextReturned", false,
                "secretRefDigestAlgorithm", "SHA-256",
                "leaseApiReady", false,
                "allowedPoolStatuses", POOL_STATUS_VALUES,
                "managedAccountStatuses", ACCOUNT_MANAGED_STATUSES
        );
    }

    private Map<String, Object> poolAuditPayload(TestAccountPool pool) {
        return Map.of(
                "status", pool.status(),
                "accountCount", repository.countPooledAccounts(pool.id(), null),
                "defaultTtlSeconds", pool.defaultTtlSeconds()
        );
    }

    private void auditPool(TestAccountPool pool, String action, Map<String, Object> afterJson) {
        contextClient.writeAuditEvent(
                action,
                "TEST_ACCOUNT_POOL",
                pool.id().toString(),
                pool.projectId(),
                "SUCCESS",
                afterJson
        );
    }

    private void auditAccount(TestPooledAccount account, String operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", operation);
        payload.put("poolId", account.poolId().toString());
        payload.put("status", account.status());
        payload.put("roleTags", readStringList(account.roleTagsJson()));
        payload.put("secretRefDigest", account.secretRefDigest());
        contextClient.writeAuditEvent(
                "test_data.account.updated",
                "TEST_POOLED_ACCOUNT",
                account.id().toString(),
                account.projectId(),
                "SUCCESS",
                payload
        );
    }

    private String normalizePoolStatus(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue == null ? null : defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!POOL_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号池状态非法");
        }
        return normalized;
    }

    private String normalizePoolWritableStatus(String value, String defaultValue) {
        String normalized = normalizePoolStatus(value, defaultValue);
        if (!POOL_WRITABLE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "归档状态必须通过 archive 接口进入");
        }
        return normalized;
    }

    private String normalizeManagedAccountStatus(String value, String defaultValue) {
        String normalized = normalizeAccountStatus(value, defaultValue);
        if (!ACCOUNT_MANAGED_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "账号租借状态由 M4 租借流程维护");
        }
        return normalized;
    }

    private String normalizeAccountStatus(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue == null ? null : defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ACCOUNT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号状态非法");
        }
        return normalized;
    }

    private String normalizeHealthStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!HEALTH_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号健康状态非法");
        }
        return normalized;
    }

    private int normalizeTtl(Integer value, int defaultValue) {
        int ttl = value == null ? defaultValue : value;
        if (ttl < 1 || ttl > properties.effectiveMaxLeaseTtlSeconds()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号池默认 TTL 超过允许范围");
        }
        return ttl;
    }

    private String boundedCode(String value) {
        String code = boundedText(value, 128);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号池 code 格式非法");
        }
        return code;
    }

    private String boundedAccountKey(String value) {
        String key = boundedText(value, 128);
        if (!ACCOUNT_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号 key 格式非法");
        }
        return key;
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
        if (normalized.size() > 32) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号角色标签数量超过上限");
        }
        normalized.forEach(tag -> {
            if (!ROLE_TAG_PATTERN.matcher(tag).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号角色标签格式非法");
            }
        });
        return normalized;
    }

    /**
     * WP8 M3 accepts secretRef only as a write-time pointer. The service persists a SHA-256 digest
     * for audit and rotation comparison, and deliberately drops the original reference before repository writes.
     */
    private String digestSecretRef(String secretRef) {
        String normalized = boundedText(secretRef, 256);
        if (!SECRET_REF_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "secretRef 必须使用 secret:// 引用");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "secretRef 摘要算法不可用");
        }
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

    private Map<String, Object> safeObject(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP8 测试数据控制面已关闭");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 无法序列化");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "账号池 JSON 读取失败");
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
