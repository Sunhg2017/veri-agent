package com.songhg.veri.agent.common.web;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/examples")
public class ExampleController {

    @GetMapping("/paged")
    public PageResponse<ExampleItem> paged(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "page_size", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "created_at") @Pattern(regexp = "created_at|name|code") String sort,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String order,
            @RequestParam(required = false) String keyword
    ) {
        List<ExampleItem> items = List.of(
                new ExampleItem("sample_department", "示例部门", "ENABLED"),
                new ExampleItem("sample_project", keyword == null ? "示例项目" : keyword, "ACTIVE")
        );
        return PageResponse.of(items, page, pageSize, items.size());
    }

    @GetMapping("/error")
    public void error(@RequestParam(defaultValue = "INVALID_STATE") ErrorCode code) {
        throw new BusinessException(code, "示例业务错误");
    }

    public record ExampleItem(
            String code,
            String name,
            String status
    ) {
    }
}
