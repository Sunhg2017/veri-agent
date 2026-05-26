package com.songhg.veri.agent.modelaccess.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import java.time.Instant;
import java.util.UUID;

public record InvocationQuery(
        /** 所属项目 ID 过滤条件。 */
        String projectId,
        /** 所属应用 ID 过滤条件。 */
        String applicationId,
        /** 敏感级别过滤条件。 */
        String sensitivityLevel,
        /** 调用状态过滤条件。 */
        InvocationStatus status,
        /** 模型提供方 ID 过滤条件。 */
        UUID providerId,
        /** 调用方服务编码过滤条件。 */
        String actorService,
        /** 调用开始时间起点。 */
        Instant startTime,
        /** 调用开始时间终点。 */
        Instant endTime,
        /** 分页参数。 */
        PageQuery pageQuery
) {

    public int index() {
        return pageQuery.index();
    }

    public int size() {
        return pageQuery.size();
    }

    public int offset() {
        return pageQuery.offset();
    }
}
