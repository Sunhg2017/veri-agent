package com.songhg.veri.agent.common.web;

import com.songhg.veri.agent.common.api.ExamplePageRequest;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.openapi.ApiLifecycle;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@ApiVersion(lifecycle = ApiLifecycle.INTERNAL)
@RestController
@RequestMapping("/api/v1/examples")
public class ExampleController {

    @GetMapping("/paged")
    public PageResponse<ExampleItem> paged(
            @Valid ExamplePageRequest pageRequest
    ) {
        PageQuery pageQuery = pageRequest.toPageQuery();
        List<ExampleItem> items = List.of(
                new ExampleItem("sample_department", "示例部门", "ENABLED"),
                new ExampleItem("sample_project", pageQuery.search().isBlank() ? "示例项目" : pageQuery.search(), "ACTIVE")
        );
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), items.size());
    }

    @GetMapping("/error")
    public void error(@RequestParam(defaultValue = "INVALID_STATE") ErrorCode code) {
        throw new BusinessException(code, "示例业务错误");
    }

    public record ExampleItem(
            @Schema(description = "示例资源编码。")
            String code,
            @Schema(description = "示例资源名称。")
            String name,
            @Schema(description = "示例资源状态。")
            String status
    ) {
    }
}
