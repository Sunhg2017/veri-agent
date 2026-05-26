package com.songhg.veri.agent.asset.domain;

import java.util.UUID;

public record TestCaseStep(
        /** 主键 ID */
        UUID id,
        /** 关联测试用例资产 ID */
        UUID caseId,
        /** 步骤序号 */
        int stepOrder,
        /** 操作动作 */
        String action,
        /** 预期结果 */
        String expectedResult
) {
}
