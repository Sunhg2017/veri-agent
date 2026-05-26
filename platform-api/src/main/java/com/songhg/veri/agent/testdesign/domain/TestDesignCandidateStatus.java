package com.songhg.veri.agent.testdesign.domain;

import java.util.Set;

/**
 * WP5 候选用例生命周期状态
 *
 * <p>候选必须先通过人工确认才能正式发布；FAILED 代表发布尝试失败，
 * 后续可以按失败候选重新发布或排查错误。</p>
 */
public enum TestDesignCandidateStatus {
    /** 已生成但尚未人工编辑或评审 */
    GENERATED,
    /** 人工编辑后等待确认 */
    EDITED,
    /** 已确认，可进入发布池 */
    CONFIRMED,
    /** 已驳回，不进入发布池 */
    REJECTED,
    /** 已忽略，不进入发布池但保留在任务记录中 */
    IGNORED,
    /** 已成功发布到 WP3 测试用例资产 */
    PUBLISHED,
    /** 发布或重试失败，需要保留错误原因以便后续处理 */
    FAILED;

    /**
     * 返回所有数据库和接口允许的候选状态代码
     */
    public static Set<String> codes() {
        return Set.of(
                GENERATED.name(),
                EDITED.name(),
                CONFIRMED.name(),
                REJECTED.name(),
                IGNORED.name(),
                PUBLISHED.name(),
                FAILED.name()
        );
    }
}
