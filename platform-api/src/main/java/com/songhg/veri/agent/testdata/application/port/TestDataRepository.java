package com.songhg.veri.agent.testdata.application.port;

import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
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
}
