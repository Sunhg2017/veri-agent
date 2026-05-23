package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.response.ApiResponseDTO;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.api.response.PageResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class AssetResponseMapper {

    private AssetResponseMapper() {
    }

    static RequirementResponse toRequirementResponse(AssetRequirement r) {
        return new RequirementResponse(
                r.id(), r.code(), r.title(), r.description(), r.source(), r.sourceRef(), r.sourceUrl(),
                r.acceptanceCriteria(),
                r.status(), r.priority(),
                r.projectId(), r.tags(), r.version(),
                lifecycleStatus(r.lifecycleStatus(), r.deletedAt()), r.archivedAt(), r.deletedAt(),
                r.createdAt(), r.updatedAt()
        );
    }

    static ApiResponseDTO toApiResponse(AssetApi a) {
        return new ApiResponseDTO(
                a.id(), a.code(), a.summary(), a.description(), a.httpMethod(), a.path(), a.source(), a.sourceRef(),
                a.version(),
                a.requestSchema(), a.responseSchema(), a.projectId(),
                a.status(),
                lifecycleStatus(a.lifecycleStatus(), a.deletedAt()), a.archivedAt(), a.deletedAt(),
                a.createdAt(), a.updatedAt()
        );
    }

    static PageResponse toPageResponse(AssetPage p) {
        return new PageResponse(
                p.id(), p.code(), p.name(), p.urlPattern(), p.source(), p.sourceRef(), p.sourceVersion(),
                p.componentTree(), p.screenshotUrl(),
                p.projectId(), p.status(),
                lifecycleStatus(p.lifecycleStatus(), p.deletedAt()), p.archivedAt(), p.deletedAt(),
                p.createdAt(), p.updatedAt()
        );
    }

    static BusinessFlowResponse toBusinessFlowResponse(AssetBusinessFlow f) {
        return new BusinessFlowResponse(
                f.id(), f.code(), f.name(), f.description(), f.flowJson(), f.priority(),
                f.projectId(), f.status(),
                lifecycleStatus(f.lifecycleStatus(), f.deletedAt()), f.archivedAt(), f.deletedAt(),
                f.createdAt(), f.updatedAt()
        );
    }

    static TestCaseResponse toTestCaseResponse(TestCaseRecord tc, List<TestCaseStep> steps) {
        List<TestCaseStepResponse> stepResponses = steps == null ? Collections.emptyList()
                : steps.stream()
                        .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                        .map(AssetResponseMapper::toTestCaseStepResponse)
                        .toList();
        return new TestCaseResponse(
                tc.id(), tc.code(), tc.title(), tc.description(), tc.requirementId(), tc.apiId(),
                tc.source(), tc.sourceRef(), tc.projectId(),
                tc.status(), tc.priority(), tc.tags(), stepResponses,
                tc.version(),
                lifecycleStatus(tc.lifecycleStatus(), tc.deletedAt()), tc.archivedAt(), tc.deletedAt(),
                tc.createdAt(), tc.updatedAt()
        );
    }

    private static TestCaseStepResponse toTestCaseStepResponse(TestCaseStep s) {
        return new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult());
    }

    private static String lifecycleStatus(String lifecycleStatus, Instant deletedAt) {
        return AssetLifecycleStatus.normalize(lifecycleStatus, deletedAt);
    }
}
