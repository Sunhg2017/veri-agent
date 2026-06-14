package com.songhg.veri.agent.testdata.infrastructure;

import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolQuery;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.domain.TestAccountPool;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
import com.songhg.veri.agent.testdata.infrastructure.mapper.TestDataMapper;
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
    public Optional<TestPooledAccount> pooledAccount(UUID id) {
        return Optional.ofNullable(mapper.pooledAccount(id));
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
}
