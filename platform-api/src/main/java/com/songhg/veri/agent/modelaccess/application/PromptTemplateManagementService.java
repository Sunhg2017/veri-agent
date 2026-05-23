package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.api.request.CreatePromptRequest;
import com.songhg.veri.agent.modelaccess.domain.PromptApprovalStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PromptTemplateManagementService {

    private final ModelAccessRepository repository;

    public PromptTemplateManagementService(ModelAccessRepository repository) {
        this.repository = repository;
    }

    public List<PromptTemplate> prompts(String promptKey) {
        return repository.prompts(trimToNull(promptKey));
    }

    public PromptTemplate createPrompt(CreatePromptRequest request) {
        String promptKey = request.promptKey().trim();
        int nextVersion = repository.prompts(promptKey)
                .stream()
                .map(PromptTemplate::version)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        boolean highRisk = Boolean.TRUE.equals(request.highRisk());
        PromptStatus status = Boolean.TRUE.equals(request.activate()) && !highRisk
                ? PromptStatus.ACTIVE
                : PromptStatus.DRAFT;
        PromptApprovalStatus approvalStatus = highRisk
                ? PromptApprovalStatus.PENDING
                : PromptApprovalStatus.NOT_REQUIRED;
        if (status == PromptStatus.ACTIVE) {
            repository.deactivateActivePrompts(promptKey);
        }
        Instant now = Instant.now();
        return repository.savePrompt(new PromptTemplate(
                UUID.randomUUID(),
                promptKey,
                request.name().trim(),
                nextVersion,
                request.content(),
                status,
                trimToNull(request.changeNote()),
                highRisk,
                approvalStatus,
                null,
                null,
                null,
                now,
                now
        ));
    }

    public PromptTemplate activatePrompt(UUID id) {
        PromptTemplate prompt = repository.prompt(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prompt 版本不存在"));
        if (prompt.highRisk() && prompt.approvalStatus() != PromptApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "高风险 Prompt 需审批通过后才能激活");
        }
        repository.deactivateActivePrompts(prompt.promptKey());
        return repository.savePrompt(new PromptTemplate(
                prompt.id(),
                prompt.promptKey(),
                prompt.name(),
                prompt.version(),
                prompt.content(),
                PromptStatus.ACTIVE,
                prompt.changeNote(),
                prompt.highRisk(),
                prompt.approvalStatus(),
                prompt.approvedBy(),
                prompt.approvedAt(),
                prompt.approvalNote(),
                prompt.createdAt(),
                Instant.now()
        ));
    }

    public PromptTemplate approvePrompt(UUID id, String approvedBy, String reviewNote) {
        PromptTemplate prompt = promptForReview(id);
        return repository.savePrompt(reviewedPrompt(
                prompt,
                PromptApprovalStatus.APPROVED,
                approvedBy,
                reviewNote
        ));
    }

    public PromptTemplate rejectPrompt(UUID id, String approvedBy, String reviewNote) {
        PromptTemplate prompt = promptForReview(id);
        return repository.savePrompt(reviewedPrompt(
                prompt,
                PromptApprovalStatus.REJECTED,
                approvedBy,
                reviewNote
        ));
    }

    public int activePromptCount() {
        return (int) repository.prompts(null).stream().filter(prompt -> prompt.status() == PromptStatus.ACTIVE).count();
    }

    private PromptTemplate promptForReview(UUID id) {
        PromptTemplate prompt = repository.prompt(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prompt 版本不存在"));
        if (!prompt.highRisk()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "低风险 Prompt 不需要审批");
        }
        if (prompt.status() == PromptStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已激活 Prompt 不能重新审批");
        }
        return prompt;
    }

    private PromptTemplate reviewedPrompt(
            PromptTemplate prompt,
            PromptApprovalStatus approvalStatus,
            String approvedBy,
            String reviewNote
    ) {
        Instant now = Instant.now();
        return new PromptTemplate(
                prompt.id(),
                prompt.promptKey(),
                prompt.name(),
                prompt.version(),
                prompt.content(),
                prompt.status(),
                prompt.changeNote(),
                prompt.highRisk(),
                approvalStatus,
                trimToNull(approvedBy) == null ? "system" : approvedBy.trim(),
                now,
                trimToNull(reviewNote),
                prompt.createdAt(),
                now
        );
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
