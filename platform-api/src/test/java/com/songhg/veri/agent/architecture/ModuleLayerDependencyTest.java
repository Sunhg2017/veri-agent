package com.songhg.veri.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModuleLayerDependencyTest {

    private static final int MAX_CONTROLLER_LINES = 200;

    private static final Pattern FORBIDDEN_API_DTO_IMPORT = Pattern.compile(
            "^import com\\.songhg\\.veri\\.agent\\.[^.]+\\.api\\.(request|response)\\.",
            Pattern.MULTILINE
    );

    private static final Pattern APPLICATION_CONTRACT_FILE = Pattern.compile(
            ".*(Request|Response|Result|DTO|Payload|Query|Repository|Command|Policy|Context|Event)\\.java"
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

    @Test
    void managementControllersDoNotDependOnAggregateFacade() throws Exception {
        Path controllerRoot = Path.of("src/main/java/com/songhg/veri/agent/management/api/controller");
        List<Path> violatedFiles;
        try (var files = Files.walk(controllerRoot)) {
            violatedFiles = files
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(this::usesManagementConsoleFacade)
                    .toList();
        }

        assertThat(violatedFiles)
                .as("management controllers must inject capability-specific operation ports")
                .isEmpty();
    }

    @Test
    void managementControllersDoNotPerformManualAuthorization() throws Exception {
        Path controllerRoot = Path.of("src/main/java/com/songhg/veri/agent/management/api/controller");
        List<Path> violatedFiles;
        try (var files = Files.walk(controllerRoot)) {
            violatedFiles = files
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(this::usesAuthorizationServiceDirectly)
                    .toList();
        }

        assertThat(violatedFiles)
                .as("management controllers must use common annotations and delegate payload-sensitive checks")
                .isEmpty();
    }

    @Test
    void nonAuthControllersDoNotReachIntoAuthorizationInternals() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/songhg/veri/agent");
        List<Path> violatedFiles;
        try (var files = Files.walk(sourceRoot)) {
            violatedFiles = files
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> !path.toString().contains("/auth/api/controller/"))
                    .filter(this::usesAuthorizationInternalsDirectly)
                    .toList();
        }

        assertThat(violatedFiles)
                .as("resource controllers must delegate principal resolution and access-denied details")
                .isEmpty();
    }

    @Test
    void managementApplicationContractsStayInExplicitSubpackages() throws Exception {
        Path applicationRoot = Path.of("src/main/java/com/songhg/veri/agent/management/application");
        List<Path> directJavaFiles;
        try (var files = Files.list(applicationRoot)) {
            directJavaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        assertThat(directJavaFiles)
                .as("management application contracts must be grouped under command/query/view/port")
                .isEmpty();
    }

    @Test
    void controllersStayFocusedOnOneResourceArea() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/songhg/veri/agent");
        List<Path> oversizedControllers;
        try (var files = Files.walk(sourceRoot)) {
            oversizedControllers = files
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(this::exceedsControllerLineLimit)
                    .toList();
        }

        assertThat(oversizedControllers)
                .as("controllers above " + MAX_CONTROLLER_LINES + " lines must be split by resource/use case")
                .isEmpty();
    }

    @Test
    void majorApplicationContractsStayOutOfRootPackage() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/java/com/songhg/veri/agent/asset/application"),
                Path.of("src/main/java/com/songhg/veri/agent/auth/application"),
                Path.of("src/main/java/com/songhg/veri/agent/documentinput/application"),
                Path.of("src/main/java/com/songhg/veri/agent/integration/application"),
                Path.of("src/main/java/com/songhg/veri/agent/modelaccess/application")
        );
        List<Path> misplacedContracts = new java.util.ArrayList<>();
        for (Path root : roots) {
            try (var files = Files.list(root)) {
                misplacedContracts.addAll(files
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(this::looksLikeApplicationContract)
                        .toList());
            }
        }

        assertThat(misplacedContracts)
                .as("application request/query/view/port contracts must live under explicit subpackages")
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

    private boolean usesManagementConsoleFacade(Path path) {
        try {
            // The old aggregate facade made management controllers grow in every direction at once.
            return Files.readString(path).contains("ManagementConsoleService");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }

    private boolean usesAuthorizationServiceDirectly(Path path) {
        try {
            return Files.readString(path).contains("AuthorizationService");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }

    private boolean usesAuthorizationInternalsDirectly(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("AuthorizationService") || content.contains("PlatformAccessDeniedException");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }

    private boolean exceedsControllerLineLimit(Path path) {
        try {
            return Files.readAllLines(path).size() > MAX_CONTROLLER_LINES;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }

    private boolean looksLikeApplicationContract(Path path) {
        return APPLICATION_CONTRACT_FILE.matcher(path.getFileName().toString()).matches();
    }
}
