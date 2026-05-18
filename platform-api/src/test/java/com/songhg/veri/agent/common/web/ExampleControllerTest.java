package com.songhg.veri.agent.common.web;

import com.songhg.veri.agent.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        PlatformHealthController.class,
        ExampleController.class
})
@Import({
        ResponseEnvelopeAdvice.class,
        GlobalExceptionHandler.class,
        TraceIdFilter.class,
        SecurityConfig.class
})
class ExampleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsUnifiedResponseAndTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", startsWith("trc_")))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("platform-api"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void pagedExampleUsesContractPageShape() throws Exception {
        mockMvc.perform(get("/api/v1/examples/paged")
                        .param("index", "2")
                        .param("size", "10")
                        .param("sort", "name")
                        .param("order", "asc")
                        .param("keyword", "department-a")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.index").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[1].name").value("department-a"));
    }

    @Test
    void validationErrorUsesContractErrorShape() throws Exception {
        mockMvc.perform(get("/api/v1/examples/paged")
                        .param("index", "0")
                        .param("size", "101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求字段校验失败"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.fieldErrors").isArray());
    }

    @Test
    void businessErrorUsesMappedHttpStatusAndCode() throws Exception {
        mockMvc.perform(get("/api/v1/examples/error")
                        .param("code", "INVALID_STATE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("示例业务错误"));
    }

    @Test
    void missingResourceUsesNotFoundEnvelopeInsteadOfInternalError() throws Exception {
        mockMvc.perform(get("/api/v1/examples/missing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")));
    }

    @Test
    void incomingTraceIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Trace-Id", "trc_user_supplied")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trc_user_supplied"))
                .andExpect(jsonPath("$.traceId").value("trc_user_supplied"));
    }
}
