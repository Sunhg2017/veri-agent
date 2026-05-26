package com.songhg.veri.agent.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PageResponse<T>(
        @Schema(description = "当前页数据列表。")
        List<T> items,
        @Schema(description = "分页页码，从 0 开始。")
        int index,
        @Schema(description = "每页条数。")
        int size,
        @Schema(description = "满足查询条件的数据总数。")
        long total
) {

    public static <T> PageResponse<T> of(List<T> items, int index, int size, long total) {
        return new PageResponse<>(items, index, size, total);
    }
}
