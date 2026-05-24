package com.songhg.veri.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModuleLayerDependencyTest {

    private static final Pattern FORBIDDEN_API_DTO_IMPORT = Pattern.compile(
            "^import com\\.songhg\\.veri\\.agent\\.[^.]+\\.api\\.(request|response)\\.",
            Pattern.MULTILINE
    );

    @Test
    void applicationAndInfrastructureDoNotDependOnApiDtos() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/songhg/veri/agent");
        List<Path> violatedFiles;
        try (var files = Files.walk(sourceRoot)) {
            violatedFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::applicationOrInfrastructure)
                    .filter(this::importsApiDto)
                    .toList();
        }

        assertThat(violatedFiles)
                .as("application/infrastructure must use application contracts instead of HTTP DTO packages")
                .isEmpty();
    }

    private boolean applicationOrInfrastructure(Path path) {
        String normalized = path.toString();
        return normalized.contains("/application/") || normalized.contains("/infrastructure/");
    }

    private boolean importsApiDto(Path path) {
        try {
            // Guard the architectural boundary that R2-Q2 called out: HTTP DTOs stay outside core layers.
            return FORBIDDEN_API_DTO_IMPORT.matcher(Files.readString(path)).find();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }
}
