package com.songhg.veri.agent.common.api;

import jakarta.validation.constraints.Pattern;

public class ExamplePageRequest extends BasePageRequest {

    @Pattern(regexp = "created_at|name|code")
    private String sort = "created_at";

    @Pattern(regexp = "asc|desc")
    private String order = "desc";

    private String keyword = "";

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort == null ? "created_at" : sort;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order == null ? "desc" : order;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
    }

    public PageQuery toPageQuery() {
        return PageQuery.of(getIndex(), getSize(), keyword);
    }
}
