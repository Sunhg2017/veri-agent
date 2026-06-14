package com.songhg.veri.agent.testdata.infrastructure;

import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
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

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private static String recordKey(UUID dataSetId, String recordKey) {
        return dataSetId + ":" + recordKey;
    }
}
