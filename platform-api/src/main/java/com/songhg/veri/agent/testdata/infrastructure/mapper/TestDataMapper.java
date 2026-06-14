package com.songhg.veri.agent.testdata.infrastructure.mapper;

import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
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
}
