package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentWebhookEventQuery(
        /** webhook 来源 ID 过滤条件 */
        UUID sourceId,
        /** webhook 来源编码过滤条件 */
        String sourceCode,
        /** webhook 事件类型过滤条件 */
        String eventType,
        /** webhook 事件状态过滤条件 */
        WebhookEventStatus status,
        /** 接收时间起点 */
        Instant receivedFrom,
        /** 接收时间终点 */
        Instant receivedTo,
        /** 分页参数 */
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
