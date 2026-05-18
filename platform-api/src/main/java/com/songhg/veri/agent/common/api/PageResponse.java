package com.songhg.veri.agent.common.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int index,
        int size,
        long total
) {

    public static <T> PageResponse<T> of(List<T> items, int index, int size, long total) {
        return new PageResponse<>(items, index, size, total);
    }
}
