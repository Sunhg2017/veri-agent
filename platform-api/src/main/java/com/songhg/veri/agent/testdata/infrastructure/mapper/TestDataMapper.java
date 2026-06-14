package com.songhg.veri.agent.testdata.infrastructure.mapper;

import com.songhg.veri.agent.testdata.application.query.TestAccountPoolQuery;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.domain.TestAccountPool;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
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

    TestPooledAccount pooledAccount(@Param("id") UUID id);

    TestPooledAccount pooledAccountByPoolAndKey(@Param("poolId") UUID poolId, @Param("accountKey") String accountKey);

    List<TestPooledAccount> pooledAccounts(@Param("poolId") UUID poolId);

    long countPooledAccounts(@Param("poolId") UUID poolId, @Param("status") String status);

    String pooledAccountProjectScopeId(@Param("id") UUID id);
}
