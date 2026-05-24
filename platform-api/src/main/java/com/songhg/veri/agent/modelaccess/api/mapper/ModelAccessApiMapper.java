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
import com.songhg.veri.agent.modelaccess.api.response.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderResilienceResponse;
import com.songhg.veri.agent.modelaccess.application.CostAlertResult;
import com.songhg.veri.agent.modelaccess.application.CostReportResult;
import com.songhg.veri.agent.modelaccess.application.CreatePromptCommand;
import com.songhg.veri.agent.modelaccess.application.CreateProviderCommand;
import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationJobResult;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.application.ProviderCheckResult;
import com.songhg.veri.agent.modelaccess.application.ProviderResilienceResult;
import com.songhg.veri.agent.modelaccess.application.UpdateProviderCommand;
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
                request.getSensitivityLevel(),
                request.getStatus(),
                request.getProviderId(),
                request.getActorService(),
                request.getStartTime(),
                request.getEndTime(),
                pageQuery
        );
    }
}
