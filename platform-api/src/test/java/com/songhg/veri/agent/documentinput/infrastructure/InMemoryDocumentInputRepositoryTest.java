package com.songhg.veri.agent.documentinput.infrastructure;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.documentinput.application.DocumentImportQuery;
import com.songhg.veri.agent.documentinput.application.DocumentSourceQuery;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentImportStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDocumentInputRepositoryTest {

    private final InMemoryDocumentInputRepository repository = new InMemoryDocumentInputRepository();

    @Test
    void storesSourcesAndFindsByCodeCaseInsensitively() {
        UUID id = UUID.randomUUID();
        repository.saveSource(new DocumentSourceConfig(
                id,
                "Custom-Reqs",
                "Custom Reqs",
                DocumentSourceType.CUSTOM_API,
                DocumentSourceStatus.ENABLED,
                "https://example.test",
                "project-wp4",
                repository.defaultFieldMapping().id(),
                null,
                Instant.now(),
                Instant.now()
        ));

        assertThat(repository.sourceByCode("custom-reqs")).isPresent();
        assertThat(repository.countSources(new DocumentSourceQuery(
                DocumentSourceType.CUSTOM_API,
                DocumentSourceStatus.ENABLED,
                PageQuery.of(0, 20)
        ))).isEqualTo(1);
    }

    @Test
    void storesImportRecordsWithPagedFiltering() {
        UUID importId = UUID.randomUUID();
        repository.saveImport(new DocumentImportRecord(
                importId,
                "project-wp4",
                null,
                null,
                DocumentSourceType.TEXT,
                "REQ-1",
                null,
                "Import",
                DocumentImportStatus.SUCCEEDED,
                1,
                1,
                "[]",
                null,
                "digest",
                Instant.now(),
                Instant.now()
        ));

        DocumentImportQuery query = new DocumentImportQuery(
                "project-wp4",
                null,
                DocumentSourceType.TEXT,
                DocumentImportStatus.SUCCEEDED,
                PageQuery.of(0, 10)
        );
        assertThat(repository.countImports(query)).isEqualTo(1);
        assertThat(repository.imports(query)).extracting(DocumentImportRecord::id).containsExactly(importId);
    }
}
