package com.songhg.veri.agent.execution.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

public class ExecutionRunLogPageRequest extends BasePageRequest {

    @Schema(description = "日志级别")
    private String level;

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public ExecutionRunLogQuery toQuery() {
        return new ExecutionRunLogQuery(
                StringUtils.hasText(level) ? level.trim().toUpperCase() : null,
                getSize(),
                offset()
        );
    }
}
