package com.songhg.veri.agent.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record PageQuery(
        /** 当前页码，从 0 开始。 */
        @Schema(description = "当前页码，从 0 开始。")
        int index,
        /** 每页数量，已按平台上限归一化。 */
        @Schema(description = "每页数量，已按平台上限归一化。")
        int size,
        /** SQL 查询偏移量，由页码和每页数量计算得到。 */
        @Schema(description = "SQL 查询偏移量，由页码和每页数量计算得到。")
        int offset,
        /** 归一化后的搜索关键字。 */
        @Schema(description = "归一化后的搜索关键字。")
        String search,
        /** 带通配符的搜索表达式，供 MyBatis LIKE 条件使用。 */
        @Schema(description = "带通配符的搜索表达式，供 MyBatis LIKE 条件使用。")
        String searchPattern
) {

    public static PageQuery of(int index, int size, String search) {
        int safeIndex = Math.max(0, index);
        int safeSize = Math.min(100, Math.max(1, size));
        String keyword = search == null ? "" : search.trim();
        return new PageQuery(safeIndex, safeSize, safeIndex * safeSize, keyword, "%" + keyword + "%");
    }

    public static PageQuery of(int index, int size) {
        return of(index, size, "");
    }
}
