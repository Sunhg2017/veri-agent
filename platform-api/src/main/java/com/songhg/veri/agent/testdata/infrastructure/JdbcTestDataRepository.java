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
import com.songhg.veri.agent.testdata.infrastructure.mapper.TestDataMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcTestDataRepository implements TestDataRepository {

    private final TestDataMapper mapper;

    public JdbcTestDataRepository(TestDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertDataSet(TestDataSet dataSet) {
        mapper.insertDataSet(dataSet);
    }

    @Override
    public void updateDataSet(TestDataSet dataSet) {
        mapper.updateDataSet(dataSet);
    }

    @Override
    public void archiveDataSet(TestDataSet dataSet) {
        mapper.archiveDataSet(dataSet);
    }

    @Override
    public Optional<TestDataSet> dataSet(UUID id) {
        return Optional.ofNullable(mapper.dataSet(id));
    }

    @Override
    public Optional<TestDataSet> dataSetByProjectAndCode(String projectId, String code) {
        return Optional.ofNullable(mapper.dataSetByProjectAndCode(projectId, code));
    }

    @Override
    public List<TestDataSet> dataSets(TestDataSetQuery query) {
        return mapper.dataSets(query);
    }

    @Override
    public long countDataSets(TestDataSetQuery query) {
        return mapper.countDataSets(query);
    }

    @Override
    public Optional<String> dataSetProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.dataSetProjectScopeId(id));
    }

    @Override
    public void upsertRecords(List<TestDataRecord> records) {
        if (records != null && !records.isEmpty()) {
            mapper.upsertRecords(records);
        }
    }

    @Override
    public List<TestDataRecord> records(UUID dataSetId) {
        return mapper.records(dataSetId);
    }

    @Override
    public long countRecords(UUID dataSetId) {
        return mapper.countRecords(dataSetId);
    }

    @Override
    public void insertAccountPool(TestAccountPool pool) {
        mapper.insertAccountPool(pool);
    }

    @Override
    public void updateAccountPool(TestAccountPool pool) {
        mapper.updateAccountPool(pool);
    }

    @Override
    public void archiveAccountPool(TestAccountPool pool) {
        mapper.archiveAccountPool(pool);
    }

    @Override
    public Optional<TestAccountPool> accountPool(UUID id) {
        return Optional.ofNullable(mapper.accountPool(id));
    }

    @Override
    public Optional<TestAccountPool> accountPoolByProjectAndCode(String projectId, String code) {
        return Optional.ofNullable(mapper.accountPoolByProjectAndCode(projectId, code));
    }

    @Override
    public List<TestAccountPool> accountPools(TestAccountPoolQuery query) {
        return mapper.accountPools(query);
    }

    @Override
    public long countAccountPools(TestAccountPoolQuery query) {
        return mapper.countAccountPools(query);
    }

    @Override
    public Optional<String> accountPoolProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.accountPoolProjectScopeId(id));
    }

    @Override
    public void insertPooledAccount(TestPooledAccount account) {
        mapper.insertPooledAccount(account);
    }

    @Override
    public void updatePooledAccount(TestPooledAccount account) {
        mapper.updatePooledAccount(account);
    }

    @Override
    public void updatePooledAccountSecretRefCipher(UUID accountId, String secretRefCipher, String updatedBy) {
        mapper.updatePooledAccountSecretRefCipher(accountId, secretRefCipher, updatedBy);
    }

    @Override
    public Optional<TestPooledAccount> pooledAccount(UUID id) {
        return Optional.ofNullable(mapper.pooledAccount(id));
    }

    @Override
    public Optional<String> pooledAccountSecretRefCipher(UUID accountId) {
        return Optional.ofNullable(mapper.pooledAccountSecretRefCipher(accountId));
    }

    @Override
    public Optional<TestPooledAccount> pooledAccountByPoolAndKey(UUID poolId, String accountKey) {
        return Optional.ofNullable(mapper.pooledAccountByPoolAndKey(poolId, accountKey));
    }

    @Override
    public List<TestPooledAccount> pooledAccounts(UUID poolId) {
        return mapper.pooledAccounts(poolId);
    }

    @Override
    public long countPooledAccounts(UUID poolId, String status) {
        return mapper.countPooledAccounts(poolId, status);
    }

    @Override
    public Optional<String> pooledAccountProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.pooledAccountProjectScopeId(id));
    }

    @Override
    public List<TestPooledAccount> pooledAccountsForHealthCheck(int limit) {
        return mapper.pooledAccountsForHealthCheck(limit);
    }

    @Override
    public Optional<TestPooledAccount> firstAvailableAccount(UUID poolId, List<String> roleTags) {
        return Optional.ofNullable(mapper.firstAvailableAccount(poolId, roleTags));
    }

    @Override
    public boolean markAccountLeased(UUID accountId, String updatedBy) {
        return mapper.markAccountLeased(accountId, updatedBy) == 1;
    }

    @Override
    public boolean updateAccountStatus(UUID accountId, String status, String updatedBy) {
        return mapper.updateAccountStatus(accountId, status, updatedBy) == 1;
    }

    @Override
    public boolean insertAccountLeaseIfAbsent(TestAccountLease lease) {
        return mapper.insertAccountLeaseIfAbsent(lease) == 1;
    }

    @Override
    public void updateAccountLease(TestAccountLease lease) {
        mapper.updateAccountLease(lease);
    }

    @Override
    public boolean renewActiveAccountLease(TestAccountLease lease) {
        return mapper.renewActiveAccountLease(lease) == 1;
    }

    @Override
    public boolean releaseActiveAccountLease(TestAccountLease lease) {
        return mapper.releaseActiveAccountLease(lease) == 1;
    }

    @Override
    public boolean expireActiveAccountLease(TestAccountLease lease) {
        return mapper.expireActiveAccountLease(lease) == 1;
    }

    @Override
    public Optional<TestAccountLease> accountLease(UUID id) {
        return Optional.ofNullable(mapper.accountLease(id));
    }

    @Override
    public Optional<TestAccountLease> accountLeaseByProjectAndRequestKey(String projectId, String requestKey) {
        return Optional.ofNullable(mapper.accountLeaseByProjectAndRequestKey(projectId, requestKey));
    }

    @Override
    public List<TestAccountLease> accountLeases(TestAccountLeaseQuery query) {
        return mapper.accountLeases(query);
    }

    @Override
    public long countAccountLeases(TestAccountLeaseQuery query) {
        return mapper.countAccountLeases(query);
    }

    @Override
    public Optional<String> accountLeaseProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.accountLeaseProjectScopeId(id));
    }

    @Override
    public Optional<TestAccountLease> activeLeaseByAccount(UUID accountId) {
        return Optional.ofNullable(mapper.activeLeaseByAccount(accountId));
    }

    @Override
    public List<TestAccountLease> activeExpiredLeases(Instant now, int limit) {
        return mapper.activeExpiredLeases(now, limit);
    }

    @Override
    public boolean insertDataTaskIfAbsent(TestDataTask task) {
        return mapper.insertDataTaskIfAbsent(task) == 1;
    }

    @Override
    public void lockDataTaskRequestKey(String projectId, String requestKey) {
        mapper.lockDataTaskRequestKey("wp8:test-data-task:" + projectId + ":" + requestKey);
    }

    @Override
    public List<TestDataTask> pendingDataTasks(int limit) {
        return mapper.pendingDataTasks(limit);
    }

    @Override
    public boolean claimPendingDataTask(TestDataTask task) {
        return mapper.claimPendingDataTask(task) == 1;
    }

    @Override
    public boolean updateDataTaskIfRequestKeyAvailable(TestDataTask task) {
        return mapper.updateDataTaskIfRequestKeyAvailable(task) == 1;
    }

    @Override
    public boolean retryDataTaskIfCurrentAttempt(TestDataTask task, int expectedAttempt) {
        return mapper.retryDataTaskIfCurrentAttempt(task, expectedAttempt) == 1;
    }

    @Override
    public Optional<TestDataTask> dataTask(UUID id) {
        return Optional.ofNullable(mapper.dataTask(id));
    }

    @Override
    public Optional<TestDataTask> dataTaskByProjectAndRequestKey(String projectId, String requestKey) {
        return Optional.ofNullable(mapper.dataTaskByProjectAndRequestKey(projectId, requestKey));
    }

    @Override
    public List<TestDataTask> dataTasks(TestDataTaskQuery query) {
        return mapper.dataTasks(query);
    }

    @Override
    public long countDataTasks(TestDataTaskQuery query) {
        return mapper.countDataTasks(query);
    }

    @Override
    public Optional<String> dataTaskProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.dataTaskProjectScopeId(id));
    }
}
