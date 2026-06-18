package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.command.GenerateTestDataRecordsCommand;
import com.songhg.veri.agent.testdata.application.command.ImportTestDataRecordsCommand;
import com.songhg.veri.agent.testdata.application.command.UpdateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestDataSetPageRequest;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.application.view.TestDataRecordGenerationResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataRecordImportResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataRecordResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataSetDetailResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataSetExportResponse;
import com.songhg.veri.agent.testdata.application.view.TestDataSetSummaryResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDataSetService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Pattern RECORD_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,128}$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final List<String> STATUS_VALUES = List.of("DRAFT", "READY", "DISABLED", "ARCHIVED");
    private static final Set<String> STATUSES = Set.copyOf(STATUS_VALUES);
    private static final Set<String> WRITABLE_STATUSES = Set.of("DRAFT", "READY", "DISABLED");
    private static final Set<String> SENSITIVITY_LEVELS = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");
    private static final Set<String> SOURCE_TYPES = Set.of("MANUAL", "GENERATED", "EXTERNAL_REF");
    private static final Set<String> FIELD_TYPES = Set.of("STRING", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "OBJECT", "ARRAY");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final TestDataRepository repository;
    private final TestDataPlatformContextClient contextClient;
    private final TestDataActorResolver actorResolver;
    private final TestDataProperties properties;
    private final ObjectMapper objectMapper;

    public TestDataSetService(
            TestDataRepository repository,
            TestDataPlatformContextClient contextClient,
            TestDataActorResolver actorResolver,
            TestDataProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataSetDetailResponse createDataSet(CreateTestDataSetCommand command) {
        assertEnabled();
        PlatformContext context = contextClient.projectContext(command.projectId());
        String projectId = context.resourceId();
        String code = boundedCode(command.code());
        repository.dataSetByProjectAndCode(projectId, code).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CONFLICT, "测试数据集 code 已存在");
        });
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        TestDataSet dataSet = new TestDataSet(
                UUID.randomUUID(),
                projectId,
                boundedNullable(command.applicationId(), 64),
                boundedNullable(command.environmentId(), 64),
                code,
                boundedText(command.name(), 128),
                normalizeWritableStatus(command.status(), "DRAFT"),
                json(validatedSchema(command.schema())),
                normalizeSensitivity(command.sensitivityLevel()),
                json(safeObject(command.cleanupPolicy())),
                normalizeSourceType(command.sourceType()),
                digestOrNull(command.sourceRefDigest(), "sourceRefDigest"),
                actor,
                actor,
                null,
                now,
                now
        );
        try {
            repository.insertDataSet(dataSet);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "测试数据集 code 已存在");
        }
        auditDataSet(dataSet, "test_data.data_set.created", Map.of(
                "status", dataSet.status(),
                "recordCount", 0,
                "sourceRefDigestPresent", dataSet.sourceRefDigest() != null
        ));
        return detail(dataSet);
    }

    @Transactional(readOnly = true)
    public PageResponse<TestDataSetSummaryResponse> dataSets(TestDataSetPageRequest request) {
        assertEnabled();
        TestDataSetQuery query = normalizeQuery(request.toQuery());
        List<TestDataSetSummaryResponse> items = repository.dataSets(query).stream()
                .map(this::summary)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countDataSets(query));
    }

    @Transactional(readOnly = true)
    public TestDataSetDetailResponse dataSet(UUID id) {
        assertEnabled();
        return detail(requireDataSet(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataSetDetailResponse updateDataSet(UUID id, UpdateTestDataSetCommand command) {
        assertEnabled();
        TestDataSet existing = requireDataSet(id);
        assertMutable(existing);
        Instant now = Instant.now();
        TestDataSet updated = new TestDataSet(
                existing.id(),
                existing.projectId(),
                command.applicationId() == null ? existing.applicationId() : boundedNullable(command.applicationId(), 64),
                command.environmentId() == null ? existing.environmentId() : boundedNullable(command.environmentId(), 64),
                existing.code(),
                StringUtils.hasText(command.name()) ? boundedText(command.name(), 128) : existing.name(),
                command.status() == null ? existing.status() : normalizeWritableStatus(command.status(), existing.status()),
                command.schema() == null ? existing.schemaJson() : json(validatedSchema(command.schema())),
                command.sensitivityLevel() == null
                        ? existing.sensitivityLevel()
                        : normalizeSensitivity(command.sensitivityLevel()),
                command.cleanupPolicy() == null ? existing.cleanupPolicyJson() : json(safeObject(command.cleanupPolicy())),
                command.sourceType() == null ? existing.sourceType() : normalizeSourceType(command.sourceType()),
                command.sourceRefDigest() == null
                        ? existing.sourceRefDigest()
                        : digestOrNull(command.sourceRefDigest(), "sourceRefDigest"),
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.archivedAt(),
                existing.createdAt(),
                now
        );
        repository.updateDataSet(updated);
        auditDataSet(updated, "test_data.data_set.updated", Map.of(
                "status", updated.status(),
                "recordCount", repository.countRecords(updated.id())
        ));
        return detail(updated);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataSetDetailResponse archiveDataSet(UUID id) {
        assertEnabled();
        TestDataSet existing = requireDataSet(id);
        if ("ARCHIVED".equals(existing.status())) {
            return detail(existing);
        }
        Instant now = Instant.now();
        TestDataSet archived = new TestDataSet(
                existing.id(),
                existing.projectId(),
                existing.applicationId(),
                existing.environmentId(),
                existing.code(),
                existing.name(),
                "ARCHIVED",
                existing.schemaJson(),
                existing.sensitivityLevel(),
                existing.cleanupPolicyJson(),
                existing.sourceType(),
                existing.sourceRefDigest(),
                existing.createdBy(),
                actorResolver.currentActor(),
                now,
                existing.createdAt(),
                now
        );
        repository.archiveDataSet(archived);
        auditDataSet(archived, "test_data.data_set.archived", Map.of(
                "status", archived.status(),
                "recordCount", repository.countRecords(archived.id())
        ));
        return detail(archived);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataRecordImportResponse importRecords(UUID dataSetId, ImportTestDataRecordsCommand command) {
        assertEnabled();
        TestDataSet dataSet = requireDataSet(dataSetId);
        if (!"DRAFT".equals(dataSet.status()) && !"READY".equals(dataSet.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前数据集状态不可导入记录摘要");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        Map<String, TestDataRecord> recordByKey = new LinkedHashMap<>();
        for (ImportTestDataRecordsCommand.RecordItem item : command.records()) {
            TestDataRecord record = record(dataSet, item, actor, now);
            recordByKey.put(record.recordKey(), record);
        }
        List<TestDataRecord> records = List.copyOf(recordByKey.values());
        List<TestDataRecord> existingRecords = repository.records(dataSetId);
        Set<String> existingKeys = new HashSet<>();
        existingRecords.forEach(record -> existingKeys.add(record.recordKey()));
        long newRecordCount = records.stream()
                .filter(record -> !existingKeys.contains(record.recordKey()))
                .count();
        if (existingRecords.size() + newRecordCount > properties.effectiveRecordMaxCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据集记录数量超过上限");
        }
        repository.upsertRecords(records);
        auditDataSet(dataSet, "test_data.record.imported", Map.of(
                "importedCount", records.size(),
                "recordCount", repository.countRecords(dataSetId)
        ));
        return new TestDataRecordImportResponse(
                dataSetId,
                records.size(),
                records.stream().map(this::recordResponse).toList(),
                dataPolicy()
        );
    }

    /**
     * Generates bounded synthetic record summaries from the dataset schema.
     * The generated payload stays on the control plane: it only persists masked summaries, digests and tags.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataRecordGenerationResponse generateRecords(UUID dataSetId, GenerateTestDataRecordsCommand command) {
        assertEnabled();
        TestDataSet dataSet = requireDataSet(dataSetId);
        assertGeneratable(dataSet);
        if (!"DRAFT".equals(dataSet.status()) && !"READY".equals(dataSet.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前数据集状态不可生成记录摘要");
        }
        int count = command.count();
        List<TestDataRecord> existingRecords = repository.records(dataSetId);
        if (existingRecords.size() + count > properties.effectiveRecordMaxCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据集记录数量超过上限");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        Map<String, Object> schema = readMap(dataSet.schemaJson());
        List<Map<String, Object>> schemaFields = schemaFields(schema);
        String prefix = StringUtils.hasText(command.recordKeyPrefix())
                ? boundedText(command.recordKeyPrefix(), 96)
                : defaultGeneratedRecordKeyPrefix(dataSet);
        Set<String> existingKeys = new HashSet<>();
        existingRecords.forEach(record -> existingKeys.add(record.recordKey()));
        List<String> tags = generatedTags(command.tags());
        List<TestDataRecord> generatedRecords = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String recordKey = nextGeneratedRecordKey(prefix, existingKeys, i);
            Map<String, Object> maskedSummary = generatedSummary(dataSet, schemaFields, recordKey, i);
            assertSummarySize(maskedSummary);
            TestDataRecord record = new TestDataRecord(
                    UUID.randomUUID(),
                    dataSet.id(),
                    dataSet.projectId(),
                    recordKey,
                    "ACTIVE",
                    sha256(recordKey + "|" + json(maskedSummary) + "|" + dataSet.id()),
                    json(maskedSummary),
                    null,
                    json(tags),
                    actor,
                    actor,
                    now,
                    now
            );
            generatedRecords.add(record);
            existingKeys.add(recordKey);
        }
        repository.upsertRecords(generatedRecords);
        auditDataSet(dataSet, "test_data.record.generated", Map.of(
                "generatedCount", generatedRecords.size(),
                "recordCount", repository.countRecords(dataSetId),
                "sourceType", dataSet.sourceType()
        ));
        return new TestDataRecordGenerationResponse(
                dataSetId,
                generatedRecords.size(),
                generatedRecords.stream().map(this::recordResponse).toList(),
                dataPolicy()
        );
    }

    /**
     * Builds the WP8 data-set export as an audit-safe metadata snapshot.
     * The export intentionally carries record digests, tags and masked-summary keys only; masked values and raw payloads stay out.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TestDataSetExportResponse exportDataSet(UUID id) {
        assertEnabled();
        if (!properties.exportEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP8 脱敏导出已关闭");
        }
        TestDataSet dataSet = requireDataSet(id);
        List<TestDataRecord> records = repository.records(id);
        Map<String, Object> schema = readMap(dataSet.schemaJson());
        TestDataSetExportResponse response = new TestDataSetExportResponse(
                "wp8-data-set-export-v1",
                Instant.now(),
                exportDataSetSnapshot(dataSet),
                records.size(),
                schemaFieldCount(schema),
                sensitiveFieldCount(schema),
                records.stream().map(this::exportRecordSnapshot).toList(),
                exportRedactionPolicy()
        );
        auditDataSet(dataSet, "test_data.exported", Map.of(
                "schemaVersion", response.schemaVersion(),
                "recordCount", response.recordCount(),
                "schemaFieldCount", response.schemaFieldCount(),
                "sensitiveFieldCount", response.sensitiveFieldCount(),
                "maskedSummaryValuesExported", false
        ));
        return response;
    }

    public String dataSetProjectScopeId(UUID id) {
        return repository.dataSetProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试数据集不存在"));
    }

    private TestDataSet requireDataSet(UUID id) {
        return repository.dataSet(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试数据集不存在"));
    }

    private void assertMutable(TestDataSet dataSet) {
        if (!WRITABLE_STATUSES.contains(dataSet.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前数据集状态不可修改");
        }
    }

    private TestDataRecord record(
            TestDataSet dataSet,
            ImportTestDataRecordsCommand.RecordItem item,
            String actor,
            Instant now
    ) {
        String key = boundedRecordKey(item.recordKey());
        Map<String, Object> maskedSummary = safeObject(item.maskedSummary());
        assertSummarySize(maskedSummary);
        return new TestDataRecord(
                UUID.randomUUID(),
                dataSet.id(),
                dataSet.projectId(),
                key,
                "ACTIVE",
                digest(item.recordDigest(), "recordDigest"),
                json(maskedSummary),
                digestOrNull(item.externalRefDigest(), "externalRefDigest"),
                json(item.tags() == null ? List.of() : item.tags().stream()
                        .filter(StringUtils::hasText)
                        .map(value -> boundedText(value, 64))
                        .distinct()
                        .toList()),
                actor,
                actor,
                now,
                now
        );
    }

    private void assertGeneratable(TestDataSet dataSet) {
        if (!"GENERATED".equals(dataSet.sourceType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 GENERATED 数据集支持自动生成记录摘要");
        }
    }

    private String defaultGeneratedRecordKeyPrefix(TestDataSet dataSet) {
        return dataSet.code() + ":gen";
    }

    private String nextGeneratedRecordKey(String prefix, Set<String> existingKeys, int index) {
        String candidate = prefix + "-" + String.format(Locale.ROOT, "%03d", index);
        int retry = index;
        while (existingKeys.contains(candidate)) {
            retry++;
            candidate = prefix + "-" + String.format(Locale.ROOT, "%03d", retry);
        }
        return boundedRecordKey(candidate);
    }

    private List<String> generatedTags(List<String> tags) {
        Set<String> normalized = new java.util.LinkedHashSet<>();
        normalized.add("generated");
        normalized.add("synthetic");
        if (tags != null) {
            tags.stream()
                    .filter(StringUtils::hasText)
                    .map(value -> boundedText(value, 64))
                    .forEach(normalized::add);
        }
        return List.copyOf(normalized);
    }

    private Map<String, Object> generatedSummary(
            TestDataSet dataSet,
            List<Map<String, Object>> schemaFields,
            String recordKey,
            int index
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recordKey", recordKey);
        summary.put("recordIndex", index);
        summary.put("dataSetCode", dataSet.code());
        summary.put("sourceType", dataSet.sourceType());
        if (schemaFields.isEmpty()) {
            summary.put("syntheticValue", dataSet.code() + "-sample-" + index);
            return summary;
        }
        for (Map<String, Object> field : schemaFields) {
            Object name = field.get("name");
            Object type = field.get("type");
            if (name instanceof String fieldName && type instanceof String fieldType) {
                summary.put(fieldName, generatedValue(fieldName, fieldType, field, index));
            }
        }
        return summary;
    }

    private Object generatedValue(String fieldName, String fieldType, Map<String, Object> field, int index) {
        boolean sensitive = Boolean.TRUE.equals(field.get("sensitive"));
        String base = fieldName + (sensitive ? "-masked-" : "-sample-") + index;
        return switch (fieldType) {
            case "NUMBER" -> index;
            case "BOOLEAN" -> index % 2 == 0;
            case "DATE" -> String.format(Locale.ROOT, "2026-06-%02d", (index - 1) % 28 + 1);
            case "DATETIME" -> Instant.parse("2026-06-15T00:00:00Z").plusSeconds(index * 60L).toString();
            case "OBJECT" -> Map.of(
                    "generated", true,
                    "field", fieldName,
                    "index", index
            );
            case "ARRAY" -> List.of(base);
            default -> base;
        };
    }

    private List<Map<String, Object>> schemaFields(Map<String, Object> schema) {
        Object fields = schema.get("fields");
        if (!(fields instanceof List<?> fieldList)) {
            return List.of();
        }
        List<Map<String, Object>> normalizedFields = new ArrayList<>();
        for (Object field : fieldList) {
            if (field instanceof Map<?, ?> fieldMap) {
                Map<String, Object> normalizedField = new LinkedHashMap<>();
                fieldMap.forEach((key, value) -> {
                    if (key instanceof String stringKey) {
                        normalizedField.put(stringKey, value);
                    }
                });
                normalizedFields.add(normalizedField);
            }
        }
        return normalizedFields;
    }

    /**
     * WP8 schema is intentionally a bounded metadata object, not a raw data payload.
     * It may describe fields, sensitivity and required flags, but every field name and type is normalized here.
     */
    private Map<String, Object> validatedSchema(Map<String, Object> schema) {
        Map<String, Object> safeSchema = new LinkedHashMap<>(safeObject(schema));
        Object rawFields = safeSchema.get("fields");
        if (rawFields == null) {
            return safeSchema;
        }
        if (!(rawFields instanceof List<?> fields)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "schema.fields 必须为数组");
        }
        if (fields.size() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "schema.fields 数量超过上限");
        }
        List<Map<String, Object>> normalizedFields = new ArrayList<>();
        for (Object field : fields) {
            if (!(field instanceof Map<?, ?> fieldMap)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "schema.fields 项必须为对象");
            }
            Object name = fieldMap.get("name");
            Object type = fieldMap.get("type");
            if (!(name instanceof String fieldName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "schema 字段名非法");
            }
            String normalizedName = fieldName.trim();
            if (!normalizedName.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "schema 字段名非法");
            }
            if (!(type instanceof String fieldType)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "schema 字段类型非法");
            }
            String normalizedType = fieldType.trim().toUpperCase(Locale.ROOT);
            if (!FIELD_TYPES.contains(normalizedType)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "schema 字段类型非法");
            }
            Map<String, Object> normalizedField = new LinkedHashMap<>();
            fieldMap.forEach((key, value) -> {
                if (key instanceof String stringKey) {
                    normalizedField.put(stringKey, value);
                }
            });
            normalizedField.put("name", normalizedName);
            normalizedField.put("type", normalizedType);
            normalizedFields.add(normalizedField);
        }
        safeSchema.put("fields", normalizedFields);
        return safeSchema;
    }

    private TestDataSetQuery normalizeQuery(TestDataSetQuery query) {
        String status = query.status() == null ? null : normalizeStatus(query.status(), null);
        return new TestDataSetQuery(
                query.projectId() == null ? null : contextClient.projectContext(query.projectId()).resourceId(),
                query.applicationId(),
                query.environmentId(),
                status,
                query.keyword(),
                query.offset(),
                query.limit()
        );
    }

    private TestDataSetDetailResponse detail(TestDataSet dataSet) {
        return new TestDataSetDetailResponse(
                dataSet.id(),
                dataSet.projectId(),
                dataSet.applicationId(),
                dataSet.environmentId(),
                dataSet.code(),
                dataSet.name(),
                dataSet.status(),
                readMap(dataSet.schemaJson()),
                dataSet.sensitivityLevel(),
                readMap(dataSet.cleanupPolicyJson()),
                dataSet.sourceType(),
                dataSet.sourceRefDigest(),
                repository.records(dataSet.id()).stream().map(this::recordResponse).toList(),
                dataPolicy(),
                dataSet.archivedAt(),
                dataSet.createdAt(),
                dataSet.updatedAt()
        );
    }

    private TestDataSetSummaryResponse summary(TestDataSet dataSet) {
        return new TestDataSetSummaryResponse(
                dataSet.id(),
                dataSet.projectId(),
                dataSet.applicationId(),
                dataSet.environmentId(),
                dataSet.code(),
                dataSet.name(),
                dataSet.status(),
                dataSet.sensitivityLevel(),
                dataSet.sourceType(),
                dataSet.sourceRefDigest(),
                repository.countRecords(dataSet.id()),
                readMap(dataSet.cleanupPolicyJson()),
                dataSet.archivedAt(),
                dataSet.createdAt(),
                dataSet.updatedAt()
        );
    }

    private TestDataRecordResponse recordResponse(TestDataRecord record) {
        return new TestDataRecordResponse(
                record.id(),
                record.dataSetId(),
                record.projectId(),
                record.recordKey(),
                record.status(),
                record.recordDigest(),
                readMap(record.maskedSummaryJson()),
                record.externalRefDigest(),
                readStringList(record.tagsJson()),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private TestDataSetExportResponse.DataSetSnapshot exportDataSetSnapshot(TestDataSet dataSet) {
        return new TestDataSetExportResponse.DataSetSnapshot(
                dataSet.id(),
                dataSet.projectId(),
                dataSet.applicationId(),
                dataSet.environmentId(),
                dataSet.code(),
                dataSet.name(),
                dataSet.status(),
                dataSet.sensitivityLevel(),
                dataSet.sourceType(),
                dataSet.sourceRefDigest(),
                dataSet.archivedAt(),
                dataSet.createdAt(),
                dataSet.updatedAt()
        );
    }

    private TestDataSetExportResponse.RecordSnapshot exportRecordSnapshot(TestDataRecord record) {
        return new TestDataSetExportResponse.RecordSnapshot(
                record.recordKey(),
                record.recordDigest(),
                record.externalRefDigest(),
                readStringList(record.tagsJson()),
                new ArrayList<>(readMap(record.maskedSummaryJson()).keySet()),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private int schemaFieldCount(Map<String, Object> schema) {
        Object fields = schema.get("fields");
        return fields instanceof List<?> fieldList ? fieldList.size() : 0;
    }

    private int sensitiveFieldCount(Map<String, Object> schema) {
        Object fields = schema.get("fields");
        if (!(fields instanceof List<?> fieldList)) {
            return 0;
        }
        int count = 0;
        for (Object field : fieldList) {
            if (field instanceof Map<?, ?> fieldMap && Boolean.TRUE.equals(fieldMap.get("sensitive"))) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Object> exportRedactionPolicy() {
        return Map.of(
                "rawRecordPayloadExported", false,
                "maskedSummaryValuesExported", false,
                "secretRefPlaintextExported", false,
                "authorizationHeaderExported", false,
                "recordDigestExported", true,
                "tagValuesExported", true
        );
    }

    private Map<String, Object> dataPolicy() {
        return Map.of(
                "rawRecordPayloadStored", false,
                "secretPlaintextStored", false,
                "recordSummaryMaxBytes", properties.effectiveRecordSummaryMaxBytes(),
                "recordMaxCount", properties.effectiveRecordMaxCount(),
                "allowedStatuses", STATUS_VALUES
        );
    }

    private void auditDataSet(TestDataSet dataSet, String action, Map<String, Object> afterJson) {
        contextClient.writeAuditEvent(action, "TEST_DATA_SET", dataSet.id().toString(), dataSet.projectId(), "SUCCESS", afterJson);
    }

    private String normalizeStatus(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue == null ? null : defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据集状态非法");
        }
        return normalized;
    }

    private String normalizeWritableStatus(String value, String defaultValue) {
        String normalized = normalizeStatus(value, defaultValue);
        if (!WRITABLE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "归档状态必须通过 archive 接口进入");
        }
        return normalized;
    }

    private String normalizeSensitivity(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "INTERNAL";
        if (!SENSITIVITY_LEVELS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "敏感级别非法");
        }
        return normalized;
    }

    private String normalizeSourceType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MANUAL";
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据来源类型非法");
        }
        return normalized;
    }

    private String boundedCode(String value) {
        String code = boundedText(value, 128);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据集 code 格式非法");
        }
        return code;
    }

    private String boundedRecordKey(String value) {
        String key = boundedText(value, 128);
        if (!RECORD_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "记录 key 格式非法");
        }
        return key;
    }

    private String digest(String value, String field) {
        if (!StringUtils.hasText(value) || !SHA256_PATTERN.matcher(value.trim()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " 必须为 64 位小写 SHA-256");
        }
        return value.trim();
    }

    private String digestOrNull(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return digest(value, field);
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必填文本不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文本长度超过上限");
        }
        return trimmed;
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文本长度超过上限");
        }
        return trimmed;
    }

    private Map<String, Object> safeObject(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP8 测试数据控制面已关闭");
        }
    }

    private void assertSummarySize(Map<String, Object> maskedSummary) {
        int byteLength = json(maskedSummary).getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > properties.effectiveRecordSummaryMaxBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "记录脱敏摘要超过大小上限");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 无法序列化");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "测试数据 JSON 读取失败");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "测试数据 tags 读取失败");
        }
    }
}
