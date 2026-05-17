package com.songhg.veri.agent.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        @JsonProperty("page_size") int pageSize,
        long total
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        return new PageResponse<>(items, page, pageSize, total);
    }
}

