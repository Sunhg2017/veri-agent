package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.AssetListRequest;
import com.songhg.veri.agent.asset.api.request.CreateRequirementRequest;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetContextAuditContractTest {

    private final InMemoryAssetRepository repository = new InMemoryAssetRepository();
    private final TestPlatformContextClient contextClient = new TestPlatformContextClient();
    private final AssetService service = new AssetService(repository, contextClient);

    @Test
    void rejectsDisabledProjectReadsAndWritesWithoutCreatingAssets() {
        contextClient.projectStatus.put("project-disabled", "DISABLED");

        assertThatThrownBy(() -> service.listRequirements(listRequest("project-disabled")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("项目状态不允许写入资产");
                });

        assertThatThrownBy(() -> service.createRequirement(requirementRequest("停用项目需求", "project-disabled")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("项目状态不允许写入资产");
                });

        assertThat(service.listRequirements(listRequest("project-active")).total()).isZero();
    }

    @Test
    void rejectsUnauthorizedProjectReadsAndWritesWithoutCreatingAssets() {
        contextClient.forbiddenProjects.add("project-forbidden");

        assertThatThrownBy(() -> service.listRequirements(listRequest("project-forbidden")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("project access denied");

        assertThatThrownBy(() -> service.createRequirement(requirementRequest("未授权项目需求", "project-forbidden")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("project access denied");

        assertThat(service.listRequirements(listRequest("project-active")).total()).isZero();
    }

    @Test
    void doesNotCreateRequirementWhenAuditWriteFails() {
        contextClient.failAuditWrites = true;

        assertThatThrownBy(() -> service.createRequirement(requirementRequest("审计失败需求", "project-active")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit backend unavailable");

        assertThat(service.listRequirements(listRequest("project-active")).total()).isZero();
    }

    private AssetListRequest listRequest(String projectId) {
        AssetListRequest request = new AssetListRequest();
        request.setProjectId(projectId);
        return request;
    }

    private CreateRequirementRequest requirementRequest(String title, String projectId) {
        return new CreateRequirementRequest(
                title,
                "contract test",
                null,
                "HIGH",
                projectId,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static class TestPlatformContextClient implements PlatformContextClient {

        private final Map<String, String> projectStatus = new HashMap<>();
        private final Set<String> forbiddenProjects = new HashSet<>();
        private boolean failAuditWrites;

        @Override
        public ProjectContext getProjectContext(String projectId) {
            if (forbiddenProjects.contains(projectId)) {
                throw new AccessDeniedException("project access denied: " + projectId);
            }
            return new ProjectContext(
                    projectId,
                    projectStatus.getOrDefault(projectId, "ACTIVE"),
                    "INTERNAL",
                    false
            );
        }

        @Override
        public void writeAuditEvent(String action, String resourceType, String resourceId, String scopeId, String result) {
            if (failAuditWrites) {
                throw new IllegalStateException("audit backend unavailable");
            }
        }
    }
}
