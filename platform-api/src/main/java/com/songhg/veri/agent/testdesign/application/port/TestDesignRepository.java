package com.songhg.veri.agent.testdesign.application.port;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTemplateQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WP5 用例生成与评审的持久化端口
 *
 * <p>服务层通过该端口屏蔽内存仓储和数据库仓储差异；所有返回对象都是当前时刻的领域快照。</p>
 */
public interface TestDesignRepository {

    /**
     * 按项目、启用状态、关键字和分页条件查询生成模板；项目过滤可包含平台全局模板。
     */
    List<TestDesignTemplate> templates(TestDesignTemplateQuery query);

    /**
     * 统计满足查询条件的生成模板数量。
     */
    long countTemplates(TestDesignTemplateQuery query);

    /**
     * 查询单个生成模板。
     */
    Optional<TestDesignTemplate> template(UUID id);

    /**
     * 按同一作用域下的模板名称查询，用于在写入前给出稳定冲突错误。
     */
    Optional<TestDesignTemplate> templateByScopeAndName(String projectId, String name);

    /**
     * 新增或更新生成模板快照。
     */
    TestDesignTemplate saveTemplate(TestDesignTemplate template);

    /**
     * 按项目、状态、关键字和分页条件查询任务列表
     */
    List<TestDesignTask> tasks(TestDesignTaskQuery query);

    /**
     * 统计满足查询条件的任务总数
     */
    long countTasks(TestDesignTaskQuery query);

    /**
     * 按任务状态统计聚合数量，用于编排健康检查，不返回任务明细。
     */
    long countTasksByStatus(TestDesignTaskStatus status);

    /**
     * 查询某状态下最早更新时间，用于队列滞留聚合指标，不返回任务标识或 payload。
     */
    Optional<Instant> oldestTaskUpdatedAtByStatus(TestDesignTaskStatus status);

    /**
     * 统计达到运行超时阈值的 RUNNING 任务数量，用于超时告警聚合指标。
     */
    long countStaleRunningTasks(Instant staleBefore);

    /**
     * 按候选状态统计聚合数量，用于异步发布编排健康和恢复扫描，不返回候选明细。
     */
    long countCandidatesByStatus(TestDesignCandidateStatus status);

    /**
     * 查询某候选状态下最早更新时间，用于发布队列滞留聚合指标，不返回候选标识或 payload。
     */
    Optional<Instant> oldestCandidateUpdatedAtByStatus(TestDesignCandidateStatus status);

    /**
     * 查询单个任务
     */
    Optional<TestDesignTask> task(UUID id);

    /**
     * 按项目内幂等键查询已创建任务，用于重复提交回放
     */
    Optional<TestDesignTask> taskByIdempotencyKey(String projectId, String idempotencyKey);

    /**
     * 当底层存储支持事务级锁时，串行化同项目同幂等键的创建请求，避免并发重复建任务
     */
    default void lockTaskIdempotencyKey(String projectId, String idempotencyKey) {
    }

    /**
     * 新增或更新任务快照
     */
    TestDesignTask saveTask(TestDesignTask task);

    /**
     * 按期望状态条件推进任务状态，用于异步事件重复投递时只允许一个消费者认领任务。
     */
    boolean markTaskStatus(UUID id, TestDesignTaskStatus expectedStatus, TestDesignTaskStatus nextStatus, Instant updatedAt);

    /**
     * 按期望状态条件推进候选状态，用于异步发布重复投递时只允许一个消费者认领单个候选。
     */
    boolean markCandidateStatus(
            UUID id,
            TestDesignCandidateStatus expectedStatus,
            TestDesignCandidateStatus nextStatus,
            Instant updatedAt
    );

    /**
     * 将长时间未更新的运行中任务标记为失败，用于进程中断或事件消费者异常退出后的恢复扫描。
     */
    int markStaleRunningTasksFailed(Instant failedAt, Instant staleBefore, String errorMessage, int limit);

    /**
     * 按任务、项目、需求、状态、覆盖类型、关键字和分页条件查询候选列表
     */
    List<TestDesignCandidate> candidates(TestDesignCandidateQuery query);

    /**
     * 统计满足查询条件的候选总数
     */
    long countCandidates(TestDesignCandidateQuery query);

    /**
     * 查询任务下的全部候选，用于任务详情、发布计划和统计重算
     */
    List<TestDesignCandidate> candidatesByTask(UUID taskId);

    /**
     * 查询已排队发布候选，恢复扫描按任务聚合后重发事件，不能扩大发布候选范围。
     */
    List<TestDesignCandidate> publishQueuedCandidates(int limit);

    /**
     * 将长时间未更新的发布中候选标记为失败，用于进程中断或事件消费者异常退出后的恢复扫描。
     */
    int markStalePublishingCandidatesFailed(Instant failedAt, Instant staleBefore, String errorMessage, int limit);

    /**
     * 统计达到发布运行超时阈值的候选数量，用于超时告警聚合指标。
     */
    long countStalePublishingCandidates(Instant staleBefore);

    /**
     * 查询具备 WP3 用例引用但仍处于 FAILED 的候选，用于受限发布补偿后台。
     *
     * <p>只返回存在资产引用且尚无成功发布记录、尚未执行自动补偿记录的候选，避免后台反复重试同一失败项。
     */
    List<TestDesignCandidate> publishCompensationCandidates(int limit);

    /**
     * 当底层存储支持事务级锁时，串行化同一候选的自动发布补偿，避免多实例调度重复修复或重复记账。
     */
    default void lockPublishCompensationCandidate(UUID candidateId) {
    }

    /**
     * 查询单个候选
     */
    Optional<TestDesignCandidate> candidate(UUID id);

    /**
     * 新增或更新候选快照
     */
    TestDesignCandidate saveCandidate(TestDesignCandidate candidate);

    /**
     * 追加一条候选编辑或评审审计记录
     */
    TestDesignReviewRecord saveReviewRecord(TestDesignReviewRecord record);

    /**
     * 查询任务下的候选编辑和评审记录
     */
    List<TestDesignReviewRecord> reviewRecords(UUID taskId, PageQuery pageQuery);

    /**
     * 查询任务下的全部候选编辑和评审记录，用于任务级报告汇总。
     */
    List<TestDesignReviewRecord> reviewRecordsByTask(UUID taskId);

    /**
     * 统计任务下的候选编辑和评审记录总数
     */
    long countReviewRecords(UUID taskId);

    /**
     * 追加一条候选发布或预发布记录
     */
    TestDesignPublishRecord savePublishRecord(TestDesignPublishRecord record);

    /**
     * 查询任务下的发布和预发布记录
     */
    List<TestDesignPublishRecord> publishRecords(UUID taskId);

    /**
     * 保存任务报告 manifest 聚合记录，用于归档核验，不包含报告行内容或候选/trace/audit 标识。
     */
    TestDesignReportManifest saveReportManifest(TestDesignReportManifest manifest);

    /**
     * 查询任务下已保存的报告 manifest 聚合记录，按创建时间倒序返回。
     */
    List<TestDesignReportManifest> reportManifestsByTask(UUID taskId);

    /**
     * 查询任务级跨 WP 审计链聚合计数，不返回审计事件、trace、模型调用、候选或资产明细标识。
     */
    TestDesignAuditChainAggregate auditChainAggregate(UUID taskId);

    /**
     * 保存项目或环境级上下文策略覆盖请求，包含有界裁剪数字、审批工单和策略正文版本。
     */
    TestDesignContextPolicyOverride saveContextPolicyOverride(TestDesignContextPolicyOverride override);

    /**
     * 追加上下文策略审批工单备注。
     */
    TestDesignContextPolicyNote saveContextPolicyNote(TestDesignContextPolicyNote note);

    /**
     * 查询上下文策略覆盖记录，用于审批状态流和脱敏运营查询。
     */
    Optional<TestDesignContextPolicyOverride> contextPolicyOverride(UUID id);

    /**
     * 查询项目下的上下文策略覆盖记录，包含项目级记录和可选环境级记录。
     */
    List<TestDesignContextPolicyOverride> contextPolicyOverrides(String projectId, String environmentKey);

    /**
     * 查询上下文策略审批工单备注时间线。
     */
    List<TestDesignContextPolicyNote> contextPolicyNotes(UUID overrideId);

    /**
     * 查询一个项目下最新已批准的项目级上下文策略覆盖。
     */
    Optional<TestDesignContextPolicyOverride> latestApprovedProjectContextPolicyOverride(String projectId);

    /**
     * 查询一个项目环境下最新已批准的环境级上下文策略覆盖。
     */
    Optional<TestDesignContextPolicyOverride> latestApprovedEnvironmentContextPolicyOverride(
            String projectId,
            String environmentKey
    );
}
