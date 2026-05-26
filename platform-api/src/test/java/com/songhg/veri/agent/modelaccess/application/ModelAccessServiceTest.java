package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.port.PlatformContextClient;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.CostAlertResult;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;







class ModelAccessServiceTest {

    @Test
    void costAlertsUseRepositoryDistinctLookupsWithoutLoadingInvocationRows() {
        ModelAccessRepository repository = mock(ModelAccessRepository.class);
        when(repository.distinctProjectIds(any(), any())).thenReturn(List.of("project-a"));
        when(repository.distinctActorServices(any(), any())).thenReturn(List.of("wp4-document-input"));
        when(repository.invocationSummary(any())).thenReturn(new InvocationSummaryResult(
                1,
                1,
                0,
                0,
                10,
                5,
                new BigDecimal("9.00")
        ));
        ModelAccessService service = service(repository);

        List<CostAlertResult> alerts = service.costAlerts(null, null);

        assertThat(alerts)
                .extracting(CostAlertResult::scope)
                .containsExactly("PROJECT", "CALLER_SERVICE");
        verify(repository).distinctProjectIds(any(), any());
        verify(repository).distinctActorServices(any(), any());
        verify(repository, never()).invocations(any());
    }

    @Test
    void writesInvocationCsvInPagedChunksAndEscapesValues() throws Exception {
        ModelAccessRepository repository = mock(ModelAccessRepository.class);
        when(repository.invocations(argThat(query -> query != null && query.index() == 0)))
                .thenReturn(invocationRecords(100));
        when(repository.invocations(argThat(query -> query != null && query.index() == 1)))
                .thenReturn(List.of(invocationRecord(101)));
        ModelAccessService service = service(repository);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeInvocationsCsv(new InvocationQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PageQuery.of(0, 100)
        ), output);

        String csv = output.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("invocationId,createdAt,projectId");
        assertThat(csv).contains("\"project,with-comma\"");
        assertThat(csv).contains("\"request \"\"quoted\"\"\nline\"");
        verify(repository).invocations(argThat(query -> query != null && query.index() == 0 && query.size() == 100));
        verify(repository).invocations(argThat(query -> query != null && query.index() == 1 && query.size() == 100));
    }

    private ModelAccessService service(ModelAccessRepository repository) {
        return new ModelAccessService(
                repository,
                List.of(),
                mock(PlatformContextClient.class),
                mock(SensitiveContentGuard.class),
                mock(PromptRenderer.class),
                properties(),
                mock(ModelAccessMetrics.class),
                mock(ProviderResilienceManager.class)
        );
    }

    private ModelAccessProperties properties() {
        return new ModelAccessProperties(
                "test-model-token",
                "test-local-model",
                4000,
                null,
                new BigDecimal("10.00"),
                256,
                "UTC",
                10000,
                1,
                1,
                1000,
                1000,
                new BigDecimal("0.8"),
                0,
                1,
                0,
                1,
                0,
                3_600_000,
                new BigDecimal("10.00"),
                "BLOCK",
                List.of()
        );
    }

    private List<InvocationRecord> invocationRecords(int count) {
        return IntStream.range(0, count)
                .mapToObj(this::invocationRecord)
                .toList();
    }

    private InvocationRecord invocationRecord(int index) {
        String projectId = index == 0 ? "project,with-comma" : "project-" + index;
        String requestPreview = index == 0 ? "request \"quoted\"\nline" : "request-" + index;
        return new InvocationRecord(
                new UUID(0, index + 1L),
                projectId,
                "app-" + index,
                "env",
                "INTERNAL",
                "prompt-key",
                1,
                new UUID(1, index + 1L),
                "local-echo-primary",
                "test-local-model",
                "default",
                "default",
                "CHAT",
                InvocationStatus.SUCCEEDED,
                false,
                "digest-" + index,
                requestPreview,
                "response-" + index,
                10,
                5,
                new BigDecimal("0.01"),
                null,
                null,
                20,
                "wp4-document-input",
                null,
                Instant.parse("2026-05-23T00:00:00Z").plusSeconds(index)
        );
    }
}
