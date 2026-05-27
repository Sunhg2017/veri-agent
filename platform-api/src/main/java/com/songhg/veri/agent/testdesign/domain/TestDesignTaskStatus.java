package com.songhg.veri.agent.testdesign.domain;

import java.util.Set;

/**
 * WP5 用例生成任务生命周期状态
 *
 * <p>任务状态描述生成和发布阶段的整体进度；单个候选的评审状态由
 * {@link TestDesignCandidateStatus} 表达。</p>
 */
public enum TestDesignTaskStatus {
    /** 任务已创建但尚未开始生成 */
    DRAFT,
    /** 任务已持久化并等待异步生成事件消费 */
    QUEUED,
    /** 候选生成中 */
    RUNNING,
    /** 候选生成全部成功 */
    SUCCEEDED,
    /** 部分需求或候选生成失败，仍可评审成功部分 */
    PARTIAL_SUCCESS,
    /** 生成失败且没有可用候选 */
    FAILED,
    /** 用户取消生成任务 */
    CANCELLED,
    /** 正在执行发布到 WP3 的动作 */
    PUBLISHING,
    /** 任务内可发布候选已经完成发布 */
    PUBLISHED;

    /**
     * 返回所有数据库和接口允许的任务状态代码
     */
    public static Set<String> codes() {
        return Set.of(
                DRAFT.name(),
                QUEUED.name(),
                RUNNING.name(),
                SUCCEEDED.name(),
                PARTIAL_SUCCESS.name(),
                FAILED.name(),
                CANCELLED.name(),
                PUBLISHING.name(),
                PUBLISHED.name()
        );
    }
}
