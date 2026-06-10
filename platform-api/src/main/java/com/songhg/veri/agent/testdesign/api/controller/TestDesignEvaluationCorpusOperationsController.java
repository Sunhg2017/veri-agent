package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignEvaluationCorpusOperationsService;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignEvaluationSampleFromCandidateCommand;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignCalibrationRunCommand;
import com.songhg.veri.agent.testdesign.application.command.SaveTestDesignEvaluationSampleCommand;
import com.songhg.veri.agent.testdesign.application.command.TransitionTestDesignEvaluationSampleCommand;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCalibrationRunPageRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationSamplePageRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCalibrationRunResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCalibrationRunsResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationSampleResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationSampleSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 real evaluation sample maintenance and long-term prompt calibration operations APIs.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design/quality")
public class TestDesignEvaluationCorpusOperationsController {

    private final TestDesignEvaluationCorpusOperationsService service;

    public TestDesignEvaluationCorpusOperationsController(TestDesignEvaluationCorpusOperationsService service) {
        this.service = service;
    }

    @GetMapping("/evaluation-samples")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.EVALUATION_SAMPLE_LIST)
    public PageResponse<TestDesignEvaluationSampleResponse> samples(
            @Valid @ModelAttribute TestDesignEvaluationSamplePageRequest request
    ) {
        return service.samples(request);
    }

    @GetMapping("/evaluation-samples/summary")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.EVALUATION_SAMPLE_LIST)
    public TestDesignEvaluationSampleSummaryResponse sampleSummary(
            @Valid @ModelAttribute TestDesignEvaluationSamplePageRequest request
    ) {
        return service.sampleSummary(request.getProjectId(), request.getPromptKey());
    }

    @PostMapping("/evaluation-samples")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.EVALUATION_SAMPLE_REQUEST)
    public TestDesignEvaluationSampleResponse createSample(
            @Valid @RequestBody SaveTestDesignEvaluationSampleCommand command
    ) {
        return service.createSample(command);
    }

    @PutMapping("/evaluation-samples/{id}")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.EVALUATION_SAMPLE)
    public TestDesignEvaluationSampleResponse updateSample(
            @PathVariable UUID id,
            @Valid @RequestBody SaveTestDesignEvaluationSampleCommand command
    ) {
        return service.updateSample(id, command);
    }

    @PatchMapping("/evaluation-samples/{id}/status")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.EVALUATION_SAMPLE)
    public TestDesignEvaluationSampleResponse transitionSample(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionTestDesignEvaluationSampleCommand command
    ) {
        return service.transitionSample(id, command);
    }

    @PostMapping("/evaluation-samples/from-candidate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.EVALUATION_SAMPLE_FROM_CANDIDATE)
    public TestDesignEvaluationSampleResponse createSampleFromCandidate(
            @Valid @RequestBody CreateTestDesignEvaluationSampleFromCandidateCommand command
    ) {
        return service.createSampleFromCandidate(command);
    }

    @GetMapping("/calibration-runs")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.CALIBRATION_RUN_LIST)
    public TestDesignCalibrationRunsResponse calibrationRuns(
            @Valid @ModelAttribute TestDesignCalibrationRunPageRequest request
    ) {
        return service.calibrationRuns(request);
    }

    @PostMapping("/calibration-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_POLICY_MANAGE,
            scope = TestDesignPermissionScopes.CALIBRATION_RUN_REQUEST)
    public TestDesignCalibrationRunResponse runCalibration(
            @Valid @RequestBody RequestTestDesignCalibrationRunCommand command
    ) {
        return service.runCalibration(command);
    }
}
