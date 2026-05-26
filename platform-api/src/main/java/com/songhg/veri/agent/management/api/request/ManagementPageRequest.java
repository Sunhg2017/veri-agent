package com.songhg.veri.agent.management.api.request;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

public class ManagementPageRequest extends BasePageRequest {

    @Schema(description = "搜索关键字")
    private String search = "";

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search == null ? "" : search;
    }

    public PageQuery toPageQuery() {
        return PageQuery.of(getIndex(), getSize(), search);
    }
}
