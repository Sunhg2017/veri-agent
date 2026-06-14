package com.songhg.veri.agent.testdata.application.port;

import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolQuery;
import com.songhg.veri.agent.testdata.domain.TestAccountPool;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
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

    Optional<TestPooledAccount> pooledAccount(UUID id);

    Optional<TestPooledAccount> pooledAccountByPoolAndKey(UUID poolId, String accountKey);

    List<TestPooledAccount> pooledAccounts(UUID poolId);

    long countPooledAccounts(UUID poolId, String status);

    Optional<String> pooledAccountProjectScopeId(UUID id);
}
