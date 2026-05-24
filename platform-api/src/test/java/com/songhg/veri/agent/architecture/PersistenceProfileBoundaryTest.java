package com.songhg.veri.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PersistenceProfileBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/songhg/veri/agent");
    private static final Pattern NOT_DB_PROFILE = Pattern.compile("@Profile\\(\\s*\"!db\"\\s*\\)");
    private static final Pattern LOCAL_PROFILE = Pattern.compile("@Profile\\(\\s*\"local\"\\s*\\)");
    private static final Pattern DB_PROFILE = Pattern.compile("@Profile\\(\\s*\"db\"\\s*\\)");
    private static final Pattern SPRING_STEREOTYPE = Pattern.compile("@(?:Component|Repository|Service)\\b");

    @Test
    void mainRuntimeDoesNotContainInMemoryImplementations() throws Exception {
        List<Path> runtimeInMemoryFiles = inMemorySourceFiles();

        assertThat(runtimeInMemoryFiles)
                .as("runtime code must not carry InMemory fixture stores; place test doubles under src/test")
                .isEmpty();
    }

    @Test
    void inMemoryImplementationsDoNotUseNegatedDbProfile() throws Exception {
        List<Path> violatedFiles = inMemorySourceFiles()
                .stream()
                .filter(path -> NOT_DB_PROFILE.matcher(read(path)).find())
                .toList();

        assertThat(violatedFiles)
                .as("InMemory persistence implementations must opt into local explicitly instead of matching every non-db profile")
                .isEmpty();
    }

    @Test
    void springManagedInMemoryImplementationsAreLocalOnlyUnlessAllowlisted() throws Exception {
        List<Path> violatedFiles = inMemorySourceFiles()
                .stream()
                .filter(path -> SPRING_STEREOTYPE.matcher(read(path)).find())
                .filter(path -> !LOCAL_PROFILE.matcher(read(path)).find())
                .toList();

        assertThat(violatedFiles)
                .as("Spring-managed InMemory implementations must stay local-only to avoid semantic drift from database stores")
                .isEmpty();
    }

    @Test
    void springManagedJdbcImplementationsAreDbOnly() throws Exception {
        List<Path> violatedFiles = jdbcSourceFiles()
                .stream()
                .filter(path -> SPRING_STEREOTYPE.matcher(read(path)).find())
                .filter(path -> !DB_PROFILE.matcher(read(path)).find())
                .toList();

        assertThat(violatedFiles)
                .as("Spring-managed JDBC implementations must be bound to the db profile")
                .isEmpty();
    }

    @Test
    void mainRuntimeDoesNotUseDatabaseVendorInPersistenceImplementationNames() throws Exception {
        assertThat(sourceFilesNamed("Postgres"))
                .as("persistence implementation names should describe the adapter style, not the current database vendor")
                .isEmpty();
    }

    private List<Path> inMemorySourceFiles() throws Exception {
        return sourceFilesNamed("InMemory");
    }

    private List<Path> jdbcSourceFiles() throws Exception {
        return sourceFilesNamed("Jdbc");
    }

    private List<Path> sourceFilesNamed(String prefix) throws Exception {
        try (var files = Files.walk(SOURCE_ROOT)) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }
}
