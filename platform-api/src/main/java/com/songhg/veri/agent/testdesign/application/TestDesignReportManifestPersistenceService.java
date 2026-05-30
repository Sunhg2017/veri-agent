package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TestDesignReportManifestPersistenceService {

    private final TestDesignRepository repository;

    public TestDesignReportManifestPersistenceService(TestDesignRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists only the aggregate manifest facts needed for archive reconciliation.
     *
     * <p>The digest is calculated from the exact CSV returned after the final safety scan, but the raw report content,
     * row summaries, candidate identifiers, trace identifiers and audit identifiers are deliberately not stored.
     */
    public TestDesignReportManifest save(
            TestDesignTask task,
            TestDesignTaskReportManifestRows.ManifestSnapshot snapshot,
            String reportCsv,
            Instant generatedAt
    ) {
        return repository.saveReportManifest(new TestDesignReportManifest(
                UUID.randomUUID(),
                task.id(),
                task.projectId(),
                snapshot.schemaVersion(),
                snapshot.fieldSetVersion(),
                snapshot.manifestMode(),
                snapshot.rowCountBeforeManifest(),
                snapshot.reportRowCount(),
                snapshot.aggregateOnly(),
                snapshot.detailRowsExported(),
                snapshot.manifestStatus(),
                sha256(reportCsv),
                generatedAt,
                Instant.now()
        ));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
