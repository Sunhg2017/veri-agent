package com.songhg.veri.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PermissionCodeUsageTest {

    private static final Pattern PERMISSION_LITERAL = Pattern.compile(
            "\"(?:role|audit|context|department|user|project|application|environment|config|secret|asset"
                    + "|modelAccess|requirementInput|testDesign|apiAutomation|execution|testData):[A-Za-z0-9_]+\""
    );

    @Test
    void permissionCodesAreDeclaredOnlyInCommonAuthorizationLayer() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/songhg/veri/agent");
        List<Path> violatedFiles;
        try (var files = Files.walk(sourceRoot)) {
            violatedFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("authorization/application/PermissionCodes.java")))
                    .filter(this::containsPermissionLiteral)
                    .toList();
        }

        assertThat(violatedFiles)
                .as("permission code literals must be centralized in PermissionCodes")
                .isEmpty();
    }

    private boolean containsPermissionLiteral(Path path) {
        try {
            // Keep controller and infrastructure permission checks from growing new magic strings.
            return PERMISSION_LITERAL.matcher(Files.readString(path)).find();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }
}
