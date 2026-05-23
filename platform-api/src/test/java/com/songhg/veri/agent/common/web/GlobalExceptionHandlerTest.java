package com.songhg.veri.agent.common.web;

import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.error.PlatformAccessDeniedException;
import com.songhg.veri.agent.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void businessExceptionUsesUnifiedEnvelope() {
        TraceContext.setTraceId("trc_exception_contract");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.INVALID_STATE, "状态不允许")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_STATE");
        assertThat(response.getBody().message()).isEqualTo("状态不允许");
        assertThat(response.getBody().traceId()).isEqualTo("trc_exception_contract");
    }

    @Test
    void platformAccessDeniedKeepsPublicForbiddenMessage() {
        TraceContext.setTraceId("trc_access_denied_contract");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(
                new PlatformAccessDeniedException("asset:manage", "PROJECT", "asset:manage@PROJECT:project-1")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("权限不足");
        assertThat(response.getBody().traceId()).isEqualTo("trc_access_denied_contract");
    }

    @Test
    void rawSpringAccessDeniedKeepsBackwardCompatibleForbiddenEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(
                new AccessDeniedException("raw security rejection")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("权限不足");
        assertThat(response.getBody().traceId()).startsWith("trc_");
    }
}
