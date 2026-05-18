package com.songhg.veri.agent.common.api;

public record PageQuery(
        int index,
        int size,
        int offset,
        String search,
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
