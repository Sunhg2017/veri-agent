package com.songhg.veri.agent.testdata.application.port;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestDataRepository {

    void insertDataSet(TestDataSet dataSet);

    void updateDataSet(TestDataSet dataSet);

    void archiveDataSet(TestDataSet dataSet);

    Optional<TestDataSet> dataSet(UUID id);

    Optional<TestDataSet> dataSetByProjectAndCode(String projectId, String code);

    List<TestDataSet> dataSets(TestDataSetQuery query);

    long countDataSets(TestDataSetQuery query);

    Optional<String> dataSetProjectScopeId(UUID id);

    void upsertRecords(List<TestDataRecord> records);

    List<TestDataRecord> records(UUID dataSetId);

    long countRecords(UUID dataSetId);

    void insertAccountPool(TestAccountPool pool);

    void updateAccountPool(TestAccountPool pool);

    void archiveAccountPool(TestAccountPool pool);

    Optional<TestAccountPool> accountPool(UUID id);

    Optional<TestAccountPool> accountPoolByProjectAndCode(String projectId, String code);

    List<TestAccountPool> accountPools(TestAccountPoolQuery query);

    long countAccountPools(TestAccountPoolQuery query);

    Optional<String> accountPoolProjectScopeId(UUID id);

    void insertPooledAccount(TestPooledAccount account);

    void updatePooledAccount(TestPooledAccount account);

    void updatePooledAccountSecretRefCipher(UUID accountId, String secretRefCipher, String updatedBy);

    Optional<TestPooledAccount> pooledAccount(UUID id);

    Optional<String> pooledAccountSecretRefCipher(UUID accountId);

    Optional<TestPooledAccount> pooledAccountByPoolAndKey(UUID poolId, String accountKey);

    List<TestPooledAccount> pooledAccounts(UUID poolId);

    long countPooledAccounts(UUID poolId, String status);

    Optional<String> pooledAccountProjectScopeId(UUID id);

    List<TestPooledAccount> pooledAccountsForHealthCheck(int limit);

    Optional<TestPooledAccount> firstAvailableAccount(UUID poolId, List<String> roleTags);

    boolean markAccountLeased(UUID accountId, String updatedBy);

    boolean updateAccountStatus(UUID accountId, String status, String updatedBy);

    boolean insertAccountLeaseIfAbsent(TestAccountLease lease);

    void updateAccountLease(TestAccountLease lease);

    boolean renewActiveAccountLease(TestAccountLease lease);

    boolean releaseActiveAccountLease(TestAccountLease lease);

    boolean expireActiveAccountLease(TestAccountLease lease);

    Optional<TestAccountLease> accountLease(UUID id);

    Optional<TestAccountLease> accountLeaseByProjectAndRequestKey(String projectId, String requestKey);

    List<TestAccountLease> accountLeases(TestAccountLeaseQuery query);

    long countAccountLeases(TestAccountLeaseQuery query);

    Optional<String> accountLeaseProjectScopeId(UUID id);

    Optional<TestAccountLease> activeLeaseByAccount(UUID accountId);

    List<TestAccountLease> activeExpiredLeases(Instant now, int limit);

    boolean insertDataTaskIfAbsent(TestDataTask task);

    /**
     * Serializes project-scoped task request keys when the storage profile supports transaction locks.
     */
    default void lockDataTaskRequestKey(String projectId, String requestKey) {
    }

    List<TestDataTask> pendingDataTasks(int limit);

    boolean claimPendingDataTask(TestDataTask task);

    boolean updateDataTaskIfRequestKeyAvailable(TestDataTask task);

    boolean retryDataTaskIfCurrentAttempt(TestDataTask task, int expectedAttempt);

    Optional<TestDataTask> dataTask(UUID id);

    Optional<TestDataTask> dataTaskByProjectAndRequestKey(String projectId, String requestKey);

    List<TestDataTask> dataTasks(TestDataTaskQuery query);

    long countDataTasks(TestDataTaskQuery query);

    Optional<String> dataTaskProjectScopeId(UUID id);
}
