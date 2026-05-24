package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.api.request.CreatePromptRequest;
import com.songhg.veri.agent.modelaccess.domain.PromptApprovalStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateManagementServiceTest {

    private InMemoryModelAccessRepository repository;
    private PromptTemplateManagementService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryModelAccessRepository();
        service = new PromptTemplateManagementService(repository);
    }

    @Test
    void createsActivatedLowRiskPromptAndArchivesPreviousActiveVersion() {
        PromptTemplate first = service.createPrompt(promptRequest(
                " release-advice ",
                " Release Advice ",
                "content v1",
                " v1 ",
                false,
                true
        ));

        PromptTemplate second = service.createPrompt(promptRequest(
                "release-advice",
                "Release Advice",
                "content v2",
                "v2",
                false,
                true
        ));

        PromptTemplate firstAfterSecondActivation = repository.prompt(first.id()).orElseThrow();
        assertThat(first.promptKey()).isEqualTo("release-advice");
        assertThat(first.name()).isEqualTo("Release Advice");
        assertThat(first.changeNote()).isEqualTo("v1");
        assertThat(first.status()).isEqualTo(PromptStatus.ACTIVE);
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.status()).isEqualTo(PromptStatus.ACTIVE);
        assertThat(second.approvalStatus()).isEqualTo(PromptApprovalStatus.NOT_REQUIRED);
        assertThat(firstAfterSecondActivation.status()).isEqualTo(PromptStatus.ARCHIVED);
        assertThat(repository.activePrompt("release-advice")).hasValueSatisfying(active ->
                assertThat(active.id()).isEqualTo(second.id()));
    }

    @Test
    void requiresApprovalBeforeActivatingHighRiskPrompt() {
        PromptTemplate created = service.createPrompt(promptRequest(
                "risk-prompt",
                "高风险 Prompt",
                "请生成生产变更建议",
                "new risk prompt",
                true,
                true
        ));

        assertThat(created.status()).isEqualTo(PromptStatus.DRAFT);
        assertThat(created.highRisk()).isTrue();
        assertThat(created.approvalStatus()).isEqualTo(PromptApprovalStatus.PENDING);
        assertThatThrownBy(() -> service.activatePrompt(created.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("高风险 Prompt 需审批通过后才能激活");
                });

        PromptTemplate approved = service.approvePrompt(created.id(), " reviewer ", " approved ");
        PromptTemplate activated = service.activatePrompt(created.id());

        assertThat(approved.status()).isEqualTo(PromptStatus.DRAFT);
        assertThat(approved.approvalStatus()).isEqualTo(PromptApprovalStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo("reviewer");
        assertThat(approved.approvalNote()).isEqualTo("approved");
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(activated.status()).isEqualTo(PromptStatus.ACTIVE);
        assertThat(activated.approvalStatus()).isEqualTo(PromptApprovalStatus.APPROVED);
    }

    @Test
    void rejectedHighRiskPromptCanBeReviewedAgainBeforeActivation() {
        PromptTemplate created = service.createPrompt(promptRequest(
                "risk-retry-prompt",
                "高风险重审 Prompt",
                "请生成生产数据修复建议",
                null,
                true,
                true
        ));

        PromptTemplate rejected = service.rejectPrompt(created.id(), " ", " 需要补充安全边界 ");
        assertThatThrownBy(() -> service.activatePrompt(created.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("高风险 Prompt 需审批通过后才能激活");
                });
        PromptTemplate approvedAgain = service.approvePrompt(created.id(), "security-reviewer", "补充后通过");

        assertThat(rejected.approvalStatus()).isEqualTo(PromptApprovalStatus.REJECTED);
        assertThat(rejected.approvedBy()).isEqualTo("system");
        assertThat(rejected.approvalNote()).isEqualTo("需要补充安全边界");
        assertThat(approvedAgain.approvalStatus()).isEqualTo(PromptApprovalStatus.APPROVED);
        assertThat(approvedAgain.approvedBy()).isEqualTo("security-reviewer");
        assertThat(approvedAgain.approvalNote()).isEqualTo("补充后通过");
    }

    @Test
    void rejectsLowRiskPromptReviewAndActivePromptReview() {
        PromptTemplate lowRisk = service.createPrompt(promptRequest(
                "low-risk-prompt",
                "低风险 Prompt",
                "请生成测试建议",
                null,
                false,
                false
        ));
        PromptTemplate highRisk = service.createPrompt(promptRequest(
                "active-risk-prompt",
                "已激活高风险 Prompt",
                "请生成灰度发布建议",
                null,
                true,
                false
        ));
        service.approvePrompt(highRisk.id(), "reviewer", "ok");
        service.activatePrompt(highRisk.id());

        assertThatThrownBy(() -> service.approvePrompt(lowRisk.id(), "reviewer", "no need"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("低风险 Prompt 不需要审批");
                });
        assertThatThrownBy(() -> service.rejectPrompt(highRisk.id(), "reviewer", "late reject"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(exception.getMessage()).contains("已激活 Prompt 不能重新审批");
                });
    }

    private static CreatePromptRequest promptRequest(
            String promptKey,
            String name,
            String content,
            String changeNote,
            Boolean highRisk,
            Boolean activate
    ) {
        return new CreatePromptRequest(promptKey, name, content, changeNote, highRisk, activate);
    }
}
