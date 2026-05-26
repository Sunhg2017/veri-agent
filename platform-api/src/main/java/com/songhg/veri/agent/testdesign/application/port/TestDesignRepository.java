package com.songhg.veri.agent.testdesign.application.port;

import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
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
     * 按项目、状态、关键字和分页条件查询任务列表
     */
    List<TestDesignTask> tasks(TestDesignTaskQuery query);

    /**
     * 统计满足查询条件的任务总数
     */
    long countTasks(TestDesignTaskQuery query);

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
     * 追加一条候选发布或预发布记录
     */
    TestDesignPublishRecord savePublishRecord(TestDesignPublishRecord record);

    /**
     * 查询任务下的发布和预发布记录
     */
    List<TestDesignPublishRecord> publishRecords(UUID taskId);
}
