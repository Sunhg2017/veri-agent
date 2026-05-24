package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.common.api.PageQuery;

public class ManagementPageRequest extends BasePageRequest {

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
