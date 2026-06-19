package com.songhg.veri.agent.testdata.infrastructure.mapper;

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
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TestDataMapper {

    void insertDataSet(TestDataSet dataSet);

    void updateDataSet(TestDataSet dataSet);

    void archiveDataSet(TestDataSet dataSet);

    TestDataSet dataSet(@Param("id") UUID id);

    TestDataSet dataSetByProjectAndCode(@Param("projectId") String projectId, @Param("code") String code);

    List<TestDataSet> dataSets(@Param("query") TestDataSetQuery query);

    long countDataSets(@Param("query") TestDataSetQuery query);

    String dataSetProjectScopeId(@Param("id") UUID id);

    void upsertRecords(@Param("records") List<TestDataRecord> records);

    List<TestDataRecord> records(@Param("dataSetId") UUID dataSetId);

    long countRecords(@Param("dataSetId") UUID dataSetId);

    void insertAccountPool(TestAccountPool pool);

    void updateAccountPool(TestAccountPool pool);

    void archiveAccountPool(TestAccountPool pool);

    TestAccountPool accountPool(@Param("id") UUID id);

    TestAccountPool accountPoolByProjectAndCode(@Param("projectId") String projectId, @Param("code") String code);

    List<TestAccountPool> accountPools(@Param("query") TestAccountPoolQuery query);

    long countAccountPools(@Param("query") TestAccountPoolQuery query);

    String accountPoolProjectScopeId(@Param("id") UUID id);

    void insertPooledAccount(TestPooledAccount account);

    void updatePooledAccount(TestPooledAccount account);

    int updatePooledAccountSecretRefCipher(
            @Param("accountId") UUID accountId,
            @Param("secretRefCipher") String secretRefCipher,
            @Param("updatedBy") String updatedBy
    );

    TestPooledAccount pooledAccount(@Param("id") UUID id);

    String pooledAccountSecretRefCipher(@Param("id") UUID id);

    TestPooledAccount pooledAccountByPoolAndKey(@Param("poolId") UUID poolId, @Param("accountKey") String accountKey);

    List<TestPooledAccount> pooledAccounts(@Param("poolId") UUID poolId);

    long countPooledAccounts(@Param("poolId") UUID poolId, @Param("status") String status);

    String pooledAccountProjectScopeId(@Param("id") UUID id);

    List<TestPooledAccount> pooledAccountsForHealthCheck(@Param("limit") int limit);

    TestPooledAccount firstAvailableAccount(@Param("poolId") UUID poolId, @Param("roleTags") List<String> roleTags);

    int markAccountLeased(@Param("accountId") UUID accountId, @Param("updatedBy") String updatedBy);

    int updateAccountStatus(
            @Param("accountId") UUID accountId,
            @Param("status") String status,
            @Param("updatedBy") String updatedBy
    );

    int insertAccountLeaseIfAbsent(TestAccountLease lease);

    void updateAccountLease(TestAccountLease lease);

    int renewActiveAccountLease(TestAccountLease lease);

    int releaseActiveAccountLease(TestAccountLease lease);

    int expireActiveAccountLease(TestAccountLease lease);

    TestAccountLease accountLease(@Param("id") UUID id);

    TestAccountLease accountLeaseByProjectAndRequestKey(
            @Param("projectId") String projectId,
            @Param("requestKey") String requestKey
    );

    List<TestAccountLease> accountLeases(@Param("query") TestAccountLeaseQuery query);

    long countAccountLeases(@Param("query") TestAccountLeaseQuery query);

    String accountLeaseProjectScopeId(@Param("id") UUID id);

    TestAccountLease activeLeaseByAccount(@Param("accountId") UUID accountId);

    List<TestAccountLease> activeExpiredLeases(@Param("now") Instant now, @Param("limit") int limit);

    int insertDataTaskIfAbsent(TestDataTask task);

    int lockDataTaskRequestKey(@Param("lockKey") String lockKey);

    List<TestDataTask> pendingDataTasks(@Param("limit") int limit);

    int claimPendingDataTask(TestDataTask task);

    int updateDataTaskIfRequestKeyAvailable(TestDataTask task);

    int retryDataTaskIfCurrentAttempt(@Param("task") TestDataTask task, @Param("expectedAttempt") int expectedAttempt);

    TestDataTask dataTask(@Param("id") UUID id);

    TestDataTask dataTaskByProjectAndRequestKey(@Param("projectId") String projectId, @Param("requestKey") String requestKey);

    List<TestDataTask> dataTasks(@Param("query") TestDataTaskQuery query);

    long countDataTasks(@Param("query") TestDataTaskQuery query);

    String dataTaskProjectScopeId(@Param("id") UUID id);
}
