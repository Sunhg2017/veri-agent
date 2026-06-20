package com.songhg.veri.agent.modelaccess.api.mapper;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.modelaccess.api.request.CreatePromptRequest;
import com.songhg.veri.agent.modelaccess.api.request.CreateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.request.InvocationPageRequest;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.request.UpdateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.response.CostAlertResponse;
import com.songhg.veri.agent.modelaccess.api.response.CostReportResponse;
import com.songhg.veri.agent.modelaccess.api.response.InvocationSummaryResponse;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.api.response.ModelInvocationJobResponse;
import com.songhg.veri.agent.modelaccess.api.response.ModelQualityEvaluationSummaryResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderResilienceResponse;
import com.songhg.veri.agent.modelaccess.application.view.CostAlertResult;
import com.songhg.veri.agent.modelaccess.application.view.CostReportResult;
import com.songhg.veri.agent.modelaccess.application.command.CreatePromptCommand;
import com.songhg.veri.agent.modelaccess.application.command.CreateProviderCommand;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobResult;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.application.view.ModelQualityEvaluationSummaryResult;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCheckResult;
import com.songhg.veri.agent.modelaccess.application.view.ProviderResilienceResult;
import com.songhg.veri.agent.modelaccess.application.command.UpdateProviderCommand;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Keeps model-access HTTP DTO conversion at the API boundary.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ModelAccessApiMapper {

    ModelInvocationCommand toCommand(InvokeModelRequest request);

    CreateProviderCommand toCommand(CreateProviderRequest request);

    UpdateProviderCommand toCommand(UpdateProviderRequest request);

    CreatePromptCommand toCommand(CreatePromptRequest request);

    InvokeModelResponse toResponse(ModelInvocationResult result);

    ModelInvocationJobResponse toResponse(ModelInvocationJobResult result);

    InvocationSummaryResponse toResponse(InvocationSummaryResult result);

    ModelQualityEvaluationSummaryResponse toResponse(ModelQualityEvaluationSummaryResult result);

    ProviderCheckResponse toResponse(ProviderCheckResult result);

    ProviderResilienceResponse toResponse(ProviderResilienceResult result);

    CostAlertResponse toResponse(CostAlertResult result);

    List<CostAlertResponse> toCostAlertResponses(List<CostAlertResult> results);

    CostReportResponse toResponse(CostReportResult result);

    CostReportResponse.CostReportRow toResponse(CostReportResult.CostReportRowResult result);

    default InvocationQuery toQuery(InvocationPageRequest request) {
        PageQuery pageQuery = request.toPageQuery();
        return new InvocationQuery(
                request.getProjectId(),
                request.getApplicationId(),
                request.getEnvironmentId(),
                request.getSensitivityLevel(),
                request.getStatus(),
                request.getProviderId(),
                request.getActorService(),
                request.getRoleScope(),
                request.getStartTime(),
                request.getEndTime(),
                pageQuery
        );
    }
}
