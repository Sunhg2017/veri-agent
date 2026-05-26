package com.songhg.veri.agent.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class BasePageRequest {

    @Min(0)
    @Schema(description = "分页页码，从 0 开始")
    private int index = 0;

    @Min(1)
    @Max(100)
    @Schema(description = "每页条数")
    private int size = 20;

    public int getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index == null ? 0 : index;
    }

    public int getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size == null ? 20 : size;
    }

    public int offset() {
        return index * size;
    }

    public PageQuery toPageQuery() {
        return PageQuery.of(index, size);
    }
}
