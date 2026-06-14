package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.command.ImportTestDataRecordsCommand;
import com.songhg.veri.agent.testdata.application.query.TestDataSetPageRequest;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDataSetServiceTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);

    @Test
    void rejectsBusinessApisWhenControlPlaneDisabled() {
        TestDataSetService service = service(false, 10, 512);

        assertThatThrownBy(() -> service.dataSets(new TestDataSetPageRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void repeatedUpsertDoesNotConsumeRecordLimit() {
        TestDataSetService service = service(true, 1, 512);
        var dataSet = service.createDataSet(new CreateTestDataSetCommand(
                "project-alpha",
                null,
                null,
                "dataset-alpha",
                "Dataset alpha",
                "READY",
                Map.of("fields", List.of(Map.of("name", "recordId", "type", "STRING"))),
                "INTERNAL",
                Map.of("mode", "MANUAL_CONFIRM"),
                "MANUAL",
                null
        ));

        service.importRecords(dataSet.id(), importCommand("record:001", DIGEST_A));
        var overwritten = service.importRecords(dataSet.id(), importCommand("record:001", DIGEST_B));

        assertThat(overwritten.importedCount()).isEqualTo(1);
        assertThat(overwritten.records()).singleElement()
                .extracting(record -> record.recordDigest())
                .isEqualTo(DIGEST_B);

        assertThatThrownBy(() -> service.importRecords(dataSet.id(), importCommand("record:002", DIGEST_C)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private TestDataSetService service(boolean enabled, int maxRecords, int summaryMaxBytes) {
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.now()
        ));
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-tester");
        return new TestDataSetService(
                new InMemoryTestDataRepository(),
                contextClient,
                actorResolver,
                new TestDataProperties(enabled, maxRecords, summaryMaxBytes, 60, 120, false, true),
                new ObjectMapper()
        );
    }

    private ImportTestDataRecordsCommand importCommand(String recordKey, String digest) {
        return new ImportTestDataRecordsCommand(List.of(new ImportTestDataRecordsCommand.RecordItem(
                recordKey,
                digest,
                Map.of("recordId", recordKey),
                null,
                List.of("sanitized")
        )));
    }
}
