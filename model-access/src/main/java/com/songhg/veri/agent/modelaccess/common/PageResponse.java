package com.songhg.veri.agent.modelaccess.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        @JsonProperty("total_pages") int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }
}
