package com.songhg.veri.agent.testdata.infrastructure;

import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
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
}
