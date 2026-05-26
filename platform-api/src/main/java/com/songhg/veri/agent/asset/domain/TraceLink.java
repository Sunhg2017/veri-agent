package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record TraceLink(
        /** 主键 ID。 */
        UUID id,
        /** 关联需求 ID。 */
        UUID requirementId,
        /** 关联 API 资产 ID。 */
        UUID apiId,
        /** 关联页面资产 ID。 */
        UUID pageId,
        /** 关联业务流资产 ID。 */
        UUID flowId,
        /** 关联测试用例资产 ID。 */
        UUID caseId,
        /** 创建时间。 */
        Instant createdAt
) {
}
