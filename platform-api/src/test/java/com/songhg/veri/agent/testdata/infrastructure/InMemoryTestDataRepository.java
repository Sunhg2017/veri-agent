package com.songhg.veri.agent.testdata.infrastructure;

import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolQuery;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.domain.TestAccountPool;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
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

    private final ConcurrentHashMap<UUID, TestDataSet> dataSets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TestDataRecord> records = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestAccountPool> accountPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestPooledAccount> pooledAccounts = new ConcurrentHashMap<>();

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

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private static String recordKey(UUID dataSetId, String recordKey) {
        return dataSetId + ":" + recordKey;
    }
}
