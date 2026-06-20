package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalUiE2eArtifactStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsArtifactWithinControlledRoot() throws Exception {
        LocalUiE2eArtifactStorage storage = new LocalUiE2eArtifactStorage(properties(tempDir.resolve("artifacts"), 1024));
        UUID runId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        Path source = Files.writeString(tempDir.resolve("runner.log"), "wp7 artifact body", StandardCharsets.UTF_8);

        var stored = storage.store(runId, artifactId, "LOG", source);

        assertThat(stored.storageRef()).isEqualTo("artifact://ui-e2e/" + runId + "/log-" + artifactId + ".log");
        assertThat(stored.fileName()).isEqualTo("log-" + artifactId + ".log");
        assertThat(stored.contentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(stored.sizeBytes()).isEqualTo(Files.size(source));
        assertThat(storage.isDownloadReady(stored.storageRef())).isTrue();

        var content = storage.read(stored.storageRef());
        assertThat(content.fileName()).isEqualTo(stored.fileName());
        assertThat(content.contentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(new String(content.content(), StandardCharsets.UTF_8)).isEqualTo("wp7 artifact body");
    }

    @Test
    void rejectsStorageRefsThatEscapeControlledRoot() {
        LocalUiE2eArtifactStorage storage = new LocalUiE2eArtifactStorage(properties(tempDir.resolve("artifacts"), 1024));

        assertThat(storage.isDownloadReady("artifact://ui-e2e/../../outside.log")).isFalse();
        assertThatThrownBy(() -> storage.read("artifact://ui-e2e/../../outside.log"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void rejectsArtifactsThatExceedConfiguredSizeLimit() throws Exception {
        LocalUiE2eArtifactStorage storage = new LocalUiE2eArtifactStorage(properties(tempDir.resolve("artifacts"), 8));
        Path source = Files.writeString(tempDir.resolve("trace.zip"), "0123456789", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.store(UUID.randomUUID(), UUID.randomUUID(), "TRACE", source))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("size limit");
    }

    private UiE2eProperties properties(Path artifactRoot, long maxArtifactSizeBytes) {
        return new UiE2eProperties(
                true,
                true,
                "playwright-subprocess",
                300,
                1800,
                1,
                maxArtifactSizeBytes,
                20,
                2,
                List.of("https://portal.example.test"),
                true,
                false,
                false,
                true,
                false,
                true,
                "node",
                "../portal-web/node_modules",
                artifactRoot.toString()
        );
    }
}
