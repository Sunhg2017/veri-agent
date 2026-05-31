package com.songhg.veri.agent.testdesign.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.testdesign.application.TestDesignQualityService;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationCorpusSummaryRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignPromptTrendRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationCorpusSummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPromptTrendResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualitySummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP5 任务级质量运营摘要接口。
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/test-design")
public class TestDesignTaskQualityController {

    private final TestDesignQualityService service;

    public TestDesignTaskQualityController(TestDesignQualityService service) {
        this.service = service;
    }

    /**
     * 查询任务全量候选质量摘要，只返回聚合指标和分布。
     */
    @GetMapping("/tasks/{id}/quality/summary")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.TASK)
    public TestDesignQualitySummaryResponse qualitySummary(@PathVariable UUID id) {
        return service.qualitySummary(id);
    }

    /**
     * 查询最近任务按 Prompt 版本聚合的质量趋势，不返回候选正文或评审评论。
     */
    @GetMapping("/quality/prompt-trend")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ, scope = TestDesignPermissionScopes.PROMPT_TREND)
    public TestDesignPromptTrendResponse promptTrend(@Valid @ModelAttribute TestDesignPromptTrendRequest request) {
        return service.promptTrend(request);
    }

    /**
     * 查询评测语料运营摘要，只返回策略边界、版本准出分布和人工反馈聚合信号。
     */
    @GetMapping("/quality/evaluation-corpus-summary")
    @RequirePermission(value = PermissionCodes.TEST_DESIGN_READ,
            scope = TestDesignPermissionScopes.EVALUATION_CORPUS_SUMMARY)
    public TestDesignEvaluationCorpusSummaryResponse evaluationCorpusSummary(
            @Valid @ModelAttribute TestDesignEvaluationCorpusSummaryRequest request
    ) {
        return service.evaluationCorpusSummary(request);
    }
}
