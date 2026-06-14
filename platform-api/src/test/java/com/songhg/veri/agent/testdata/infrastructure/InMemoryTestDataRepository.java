package com.songhg.veri.agent.testdata.infrastructure;

import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestAccountLeaseQuery;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolQuery;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.application.query.TestDataTaskQuery;
import com.songhg.veri.agent.testdata.domain.TestAccountLease;
import com.songhg.veri.agent.testdata.domain.TestAccountPool;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Repository
public class InMemoryTestDataRepository implements TestDataRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<UUID, TestDataSet> dataSets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TestDataRecord> records = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestAccountPool> accountPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestPooledAccount> pooledAccounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestAccountLease> accountLeases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDataTask> dataTasks = new ConcurrentHashMap<>();

    @Override
    public void insertDataSet(TestDataSet dataSet) {
        if (dataSetByProjectAndCode(dataSet.projectId(), dataSet.code()).isPresent()) {
            throw new DuplicateKeyException("Duplicate test data set code");
        }
        dataSets.put(dataSet.id(), dataSet);
    }

    @Override
    public void updateDataSet(TestDataSet dataSet) {
        dataSets.computeIfPresent(dataSet.id(), (ignored, current) -> "ARCHIVED".equals(current.status())
                ? current
                : dataSet);
    }

    @Override
    public void archiveDataSet(TestDataSet dataSet) {
        dataSets.computeIfPresent(dataSet.id(), (ignored, current) -> "ARCHIVED".equals(current.status())
                ? current
                : dataSet);
    }

    @Override
    public Optional<TestDataSet> dataSet(UUID id) {
        return Optional.ofNullable(dataSets.get(id));
    }

    @Override
    public Optional<TestDataSet> dataSetByProjectAndCode(String projectId, String code) {
        return dataSets.values().stream()
                .filter(dataSet -> projectId.equals(dataSet.projectId()))
                .filter(dataSet -> code.equals(dataSet.code()))
                .findFirst();
    }

    @Override
    public List<TestDataSet> dataSets(TestDataSetQuery query) {
        return filteredDataSets(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countDataSets(TestDataSetQuery query) {
        return filteredDataSets(query).count();
    }

    @Override
    public Optional<String> dataSetProjectScopeId(UUID id) {
        return dataSet(id).map(TestDataSet::projectId);
    }

    @Override
    public void upsertRecords(List<TestDataRecord> newRecords) {
        for (TestDataRecord record : newRecords) {
            String key = recordKey(record.dataSetId(), record.recordKey());
            records.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return record;
                }
                return new TestDataRecord(
                        existing.id(),
                        record.dataSetId(),
                        record.projectId(),
                        record.recordKey(),
                        record.status(),
                        record.recordDigest(),
                        record.maskedSummaryJson(),
                        record.externalRefDigest(),
                        record.tagsJson(),
                        existing.createdBy(),
                        record.updatedBy(),
                        existing.createdAt(),
                        record.updatedAt()
                );
            });
        }
    }

    @Override
    public List<TestDataRecord> records(UUID dataSetId) {
        return records.values().stream()
                .filter(record -> dataSetId.equals(record.dataSetId()))
                .sorted(Comparator.comparing(TestDataRecord::recordKey))
                .toList();
    }

    @Override
    public long countRecords(UUID dataSetId) {
        return records.values().stream()
                .filter(record -> dataSetId.equals(record.dataSetId()))
                .count();
    }

    @Override
    public void insertAccountPool(TestAccountPool pool) {
        if (accountPoolByProjectAndCode(pool.projectId(), pool.code()).isPresent()) {
            throw new DuplicateKeyException("Duplicate test account pool code");
        }
        accountPools.put(pool.id(), pool);
    }

    @Override
    public void updateAccountPool(TestAccountPool pool) {
        accountPools.computeIfPresent(pool.id(), (ignored, current) -> "ARCHIVED".equals(current.status())
                ? current
                : pool);
    }

    @Override
    public void archiveAccountPool(TestAccountPool pool) {
        accountPools.computeIfPresent(pool.id(), (ignored, current) -> "ARCHIVED".equals(current.status())
                ? current
                : pool);
    }

    @Override
    public Optional<TestAccountPool> accountPool(UUID id) {
        return Optional.ofNullable(accountPools.get(id));
    }

    @Override
    public Optional<TestAccountPool> accountPoolByProjectAndCode(String projectId, String code) {
        return accountPools.values().stream()
                .filter(pool -> projectId.equals(pool.projectId()))
                .filter(pool -> code.equals(pool.code()))
                .findFirst();
    }

    @Override
    public List<TestAccountPool> accountPools(TestAccountPoolQuery query) {
        return filteredAccountPools(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countAccountPools(TestAccountPoolQuery query) {
        return filteredAccountPools(query).count();
    }

    @Override
    public Optional<String> accountPoolProjectScopeId(UUID id) {
        return accountPool(id).map(TestAccountPool::projectId);
    }

    @Override
    public void insertPooledAccount(TestPooledAccount account) {
        if (pooledAccountByPoolAndKey(account.poolId(), account.accountKey()).isPresent()) {
            throw new DuplicateKeyException("Duplicate test pooled account key");
        }
        pooledAccounts.put(account.id(), account);
    }

    @Override
    public void updatePooledAccount(TestPooledAccount account) {
        pooledAccounts.put(account.id(), account);
    }

    @Override
    public Optional<TestPooledAccount> pooledAccount(UUID id) {
        return Optional.ofNullable(pooledAccounts.get(id));
    }

    @Override
    public Optional<TestPooledAccount> pooledAccountByPoolAndKey(UUID poolId, String accountKey) {
        return pooledAccounts.values().stream()
                .filter(account -> poolId.equals(account.poolId()))
                .filter(account -> accountKey.equals(account.accountKey()))
                .findFirst();
    }

    @Override
    public List<TestPooledAccount> pooledAccounts(UUID poolId) {
        return pooledAccounts.values().stream()
                .filter(account -> poolId.equals(account.poolId()))
                .sorted(Comparator.comparing(TestPooledAccount::accountKey))
                .toList();
    }

    @Override
    public long countPooledAccounts(UUID poolId, String status) {
        Stream<TestPooledAccount> stream = pooledAccounts.values().stream()
                .filter(account -> poolId.equals(account.poolId()));
        if (StringUtils.hasText(status)) {
            stream = stream.filter(account -> status.equals(account.status()));
        }
        return stream.count();
    }

    @Override
    public Optional<String> pooledAccountProjectScopeId(UUID id) {
        return pooledAccount(id).map(TestPooledAccount::projectId);
    }

    @Override
    public Optional<TestPooledAccount> firstAvailableAccount(UUID poolId, List<String> roleTags) {
        return pooledAccounts.values().stream()
                .filter(account -> poolId.equals(account.poolId()))
                .filter(account -> "AVAILABLE".equals(account.status()))
                .filter(account -> hasAllRoleTags(account, roleTags))
                .sorted(Comparator.comparing(TestPooledAccount::updatedAt).thenComparing(TestPooledAccount::accountKey))
                .findFirst();
    }

    @Override
    public boolean markAccountLeased(UUID accountId, String updatedBy) {
        boolean[] updated = {false};
        return pooledAccounts.computeIfPresent(accountId, (ignored, current) -> {
            if (!"AVAILABLE".equals(current.status())) {
                return current;
            }
            updated[0] = true;
            Instant now = Instant.now();
            return new TestPooledAccount(
                    current.id(),
                    current.poolId(),
                    current.projectId(),
                    current.accountKey(),
                    current.displayName(),
                    "LEASED",
                    current.roleTagsJson(),
                    current.scopeSummaryJson(),
                    current.secretRefDigest(),
                    current.lastHealthStatus(),
                    current.lastHealthSummary(),
                    current.createdBy(),
                    updatedBy,
                    current.archivedAt(),
                    current.createdAt(),
                    now
            );
        }) != null && updated[0];
    }

    @Override
    public boolean updateAccountStatus(UUID accountId, String status, String updatedBy) {
        return pooledAccounts.computeIfPresent(accountId, (ignored, current) -> {
            Instant now = Instant.now();
            return new TestPooledAccount(
                    current.id(),
                    current.poolId(),
                    current.projectId(),
                    current.accountKey(),
                    current.displayName(),
                    status,
                    current.roleTagsJson(),
                    current.scopeSummaryJson(),
                    current.secretRefDigest(),
                    current.lastHealthStatus(),
                    current.lastHealthSummary(),
                    current.createdBy(),
                    updatedBy,
                    "ARCHIVED".equals(status) ? now : current.archivedAt(),
                    current.createdAt(),
                    now
            );
        }) != null;
    }

    @Override
    public boolean insertAccountLeaseIfAbsent(TestAccountLease lease) {
        if (accountLeaseByProjectAndRequestKey(lease.projectId(), lease.requestKey()).isPresent()) {
            return false;
        }
        boolean activeExists = accountLeases.values().stream()
                .anyMatch(existing -> lease.accountId().equals(existing.accountId()) && "ACTIVE".equals(existing.status()));
        if (activeExists && "ACTIVE".equals(lease.status())) {
            return false;
        }
        accountLeases.put(lease.id(), lease);
        return true;
    }

    @Override
    public void updateAccountLease(TestAccountLease lease) {
        accountLeases.put(lease.id(), lease);
    }

    @Override
    public boolean renewActiveAccountLease(TestAccountLease lease) {
        TestAccountLease existing = accountLeases.get(lease.id());
        if (existing == null || !"ACTIVE".equals(existing.status())) {
            return false;
        }
        accountLeases.put(lease.id(), lease);
        return true;
    }

    @Override
    public boolean releaseActiveAccountLease(TestAccountLease lease) {
        TestAccountLease existing = accountLeases.get(lease.id());
        if (existing == null || !"ACTIVE".equals(existing.status())) {
            return false;
        }
        accountLeases.put(lease.id(), lease);
        return true;
    }

    @Override
    public boolean expireActiveAccountLease(TestAccountLease lease) {
        TestAccountLease existing = accountLeases.get(lease.id());
        if (existing == null || !"ACTIVE".equals(existing.status())) {
            return false;
        }
        accountLeases.put(lease.id(), lease);
        return true;
    }

    @Override
    public Optional<TestAccountLease> accountLease(UUID id) {
        return Optional.ofNullable(accountLeases.get(id));
    }

    @Override
    public Optional<TestAccountLease> accountLeaseByProjectAndRequestKey(String projectId, String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            return Optional.empty();
        }
        return accountLeases.values().stream()
                .filter(lease -> projectId.equals(lease.projectId()))
                .filter(lease -> requestKey.equals(lease.requestKey()))
                .findFirst();
    }

    @Override
    public List<TestAccountLease> accountLeases(TestAccountLeaseQuery query) {
        return filteredAccountLeases(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countAccountLeases(TestAccountLeaseQuery query) {
        return filteredAccountLeases(query).count();
    }

    @Override
    public Optional<String> accountLeaseProjectScopeId(UUID id) {
        return accountLease(id).map(TestAccountLease::projectId);
    }

    @Override
    public List<TestAccountLease> activeExpiredLeases(Instant now, int limit) {
        return accountLeases.values().stream()
                .filter(lease -> "ACTIVE".equals(lease.status()))
                .filter(lease -> !lease.expiresAt().isAfter(now))
                .sorted(Comparator.comparing(TestAccountLease::expiresAt))
                .limit(limit)
                .toList();
    }

    @Override
    public boolean insertDataTaskIfAbsent(TestDataTask task) {
        if (dataTaskByProjectAndRequestKey(task.projectId(), task.requestKey()).isPresent()) {
            return false;
        }
        dataTasks.put(task.id(), task);
        return true;
    }

    @Override
    public boolean updateDataTaskIfRequestKeyAvailable(TestDataTask task) {
        if (dataTaskByProjectAndRequestKey(task.projectId(), task.requestKey())
                .filter(existing -> !existing.id().equals(task.id()))
                .isPresent()) {
            return false;
        }
        dataTasks.put(task.id(), task);
        return true;
    }

    @Override
    public boolean retryDataTaskIfCurrentAttempt(TestDataTask task, int expectedAttempt) {
        TestDataTask existing = dataTasks.get(task.id());
        if (existing == null
                || (!"FAILED".equals(existing.status()) && !"CANCELED".equals(existing.status()))
                || existing.attempt() != expectedAttempt) {
            return false;
        }
        return updateDataTaskIfRequestKeyAvailable(task);
    }

    @Override
    public Optional<TestDataTask> dataTask(UUID id) {
        return Optional.ofNullable(dataTasks.get(id));
    }

    @Override
    public Optional<TestDataTask> dataTaskByProjectAndRequestKey(String projectId, String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            return Optional.empty();
        }
        return dataTasks.values().stream()
                .filter(task -> projectId.equals(task.projectId()))
                .filter(task -> requestKey.equals(task.requestKey()))
                .findFirst();
    }

    @Override
    public List<TestDataTask> dataTasks(TestDataTaskQuery query) {
        return filteredDataTasks(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countDataTasks(TestDataTaskQuery query) {
        return filteredDataTasks(query).count();
    }

    @Override
    public Optional<String> dataTaskProjectScopeId(UUID id) {
        return dataTask(id).map(TestDataTask::projectId);
    }

    private Stream<TestDataSet> filteredDataSets(TestDataSetQuery query) {
        Stream<TestDataSet> stream = dataSets.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(dataSet -> query.projectId().equals(dataSet.projectId()));
        }
        if (StringUtils.hasText(query.applicationId())) {
            stream = stream.filter(dataSet -> query.applicationId().equals(dataSet.applicationId()));
        }
        if (StringUtils.hasText(query.environmentId())) {
            stream = stream.filter(dataSet -> query.environmentId().equals(dataSet.environmentId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(dataSet -> query.status().equals(dataSet.status()));
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().toLowerCase();
            stream = stream.filter(dataSet -> contains(dataSet.code(), keyword) || contains(dataSet.name(), keyword));
        }
        return stream.sorted(Comparator.comparing(TestDataSet::updatedAt).reversed()
                .thenComparing(TestDataSet::code));
    }

    private Stream<TestAccountPool> filteredAccountPools(TestAccountPoolQuery query) {
        Stream<TestAccountPool> stream = accountPools.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(pool -> query.projectId().equals(pool.projectId()));
        }
        if (StringUtils.hasText(query.applicationId())) {
            stream = stream.filter(pool -> query.applicationId().equals(pool.applicationId()));
        }
        if (StringUtils.hasText(query.environmentId())) {
            stream = stream.filter(pool -> query.environmentId().equals(pool.environmentId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(pool -> query.status().equals(pool.status()));
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().toLowerCase();
            stream = stream.filter(pool -> contains(pool.code(), keyword) || contains(pool.name(), keyword));
        }
        return stream.sorted(Comparator.comparing(TestAccountPool::updatedAt).reversed()
                .thenComparing(TestAccountPool::code));
    }

    private Stream<TestAccountLease> filteredAccountLeases(TestAccountLeaseQuery query) {
        Stream<TestAccountLease> stream = accountLeases.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(lease -> query.projectId().equals(lease.projectId()));
        }
        if (query.poolId() != null) {
            stream = stream.filter(lease -> query.poolId().equals(lease.poolId()));
        }
        if (query.accountId() != null) {
            stream = stream.filter(lease -> query.accountId().equals(lease.accountId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(lease -> query.status().equals(lease.status()));
        }
        if (StringUtils.hasText(query.holderRef())) {
            stream = stream.filter(lease -> query.holderRef().equals(lease.holderRef()));
        }
        return stream.sorted(Comparator.comparing(TestAccountLease::createdAt).reversed()
                .thenComparing(TestAccountLease::id));
    }

    private Stream<TestDataTask> filteredDataTasks(TestDataTaskQuery query) {
        Stream<TestDataTask> stream = dataTasks.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(task -> query.projectId().equals(task.projectId()));
        }
        if (query.dataSetId() != null) {
            stream = stream.filter(task -> query.dataSetId().equals(task.dataSetId()));
        }
        if (StringUtils.hasText(query.taskType())) {
            stream = stream.filter(task -> query.taskType().equals(task.taskType()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(task -> query.status().equals(task.status()));
        }
        return stream.sorted(Comparator.comparing(TestDataTask::createdAt).reversed()
                .thenComparing(TestDataTask::id));
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private static String recordKey(UUID dataSetId, String recordKey) {
        return dataSetId + ":" + recordKey;
    }

    private static boolean hasAllRoleTags(TestPooledAccount account, List<String> requiredRoleTags) {
        if (requiredRoleTags == null || requiredRoleTags.isEmpty()) {
            return true;
        }
        try {
            List<String> actualTags = OBJECT_MAPPER.readValue(account.roleTagsJson(), STRING_LIST_TYPE);
            return actualTags.containsAll(requiredRoleTags);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
