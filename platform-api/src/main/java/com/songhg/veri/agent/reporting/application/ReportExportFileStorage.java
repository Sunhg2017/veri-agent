package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.common.storage.OpaqueFileStorage;
import com.songhg.veri.agent.reporting.domain.ReportExportManifest;
import java.io.IOException;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Encapsulates controlled file storage for WP10 export bodies.
 *
 * <p>The manifest remains the source of record while the sanitized export bytes are stored behind an opaque reference so
 * operators can download a real file without exposing file-system layout to the API contract.</p>
 */
public class ReportExportFileStorage {

    private final OpaqueFileStorage storage;

    public ReportExportFileStorage(OpaqueFileStorage storage) {
        this.storage = storage;
    }

    public StoredExport store(ReportExportManifest manifest, byte[] content) throws IOException {
        OpaqueFileStorage.StoredFile stored = storage.storeBytes(
                partition(manifest),
                fileName(manifest),
                contentType(manifest.exportType()),
                content == null ? new byte[0] : content
        );
        return new StoredExport(
                stored.storageRef(),
                stored.fileName(),
                stored.contentType(),
                stored.sizeBytes()
        );
    }

    public DownloadableExport read(ReportExportManifest manifest) throws IOException {
        OpaqueFileStorage.StoredFileContent content = storage.read(storageRef(manifest));
        return new DownloadableExport(
                content.storageRef(),
                content.fileName(),
                contentType(manifest.exportType()),
                content.content()
        );
    }

    public boolean isDownloadReady(ReportExportManifest manifest) {
        return storage.isDownloadReady(storageRef(manifest));
    }

    public String storageRef(ReportExportManifest manifest) {
        return storage.storageRef(partition(manifest), fileName(manifest));
    }

    private String partition(ReportExportManifest manifest) {
        return manifest.reportId().toString();
    }

    private String fileName(ReportExportManifest manifest) {
        String suffix = switch (normalizedType(manifest.exportType())) {
            case "MARKDOWN" -> ".md";
            case "PDF" -> ".pdf";
            case "WORD" -> ".docx";
            default -> ".json";
        };
        return "export-" + manifest.id() + suffix;
    }

    private String contentType(String exportType) {
        return switch (normalizedType(exportType)) {
            case "MARKDOWN" -> "text/markdown;charset=UTF-8";
            case "PDF" -> "application/pdf";
            case "WORD" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/json;charset=UTF-8";
        };
    }

    private String normalizedType(String exportType) {
        if (!StringUtils.hasText(exportType)) {
            return "JSON";
        }
        return exportType.trim().toUpperCase(Locale.ROOT);
    }

    public record StoredExport(
            String storageRef,
            String fileName,
            String contentType,
            long sizeBytes
    ) {
    }

    public record DownloadableExport(
            String storageRef,
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}
