package com.songhg.veri.agent.common.web;

import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.api.FieldErrorItem;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity
                .status(code.httpStatus())
                .body(ApiResponse.error(code.name(), exception.getMessage(), TraceContext.getTraceId(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, List<FieldErrorItem>>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<FieldErrorItem> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();

        return validationError(fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, List<FieldErrorItem>>>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<FieldErrorItem> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new FieldErrorItem(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .sorted(Comparator.comparing(FieldErrorItem::field))
                .toList();

        return validationError(fieldErrors);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.name(),
                        "上传文件超过大小上限",
                        TraceContext.getTraceId(),
                        null
                ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity
                .status(ErrorCode.UNAUTHORIZED.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.UNAUTHORIZED.name(),
                        "认证失败或会话已失效",
                        TraceContext.getTraceId(),
                        null
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.FORBIDDEN.name(),
                        "权限不足",
                        TraceContext.getTraceId(),
                        null
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        return ResponseEntity
                .status(ErrorCode.NOT_FOUND.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.NOT_FOUND.name(),
                        "资源不存在",
                        TraceContext.getTraceId(),
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unexpected platform-api exception, trace_id={}", TraceContext.getTraceId(), exception);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_ERROR.name(),
                        "系统异常",
                        TraceContext.getTraceId(),
                        null
                ));
    }

    private ResponseEntity<ApiResponse<Map<String, List<FieldErrorItem>>>> validationError(
            List<FieldErrorItem> fieldErrors
    ) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.name(),
                        "请求字段校验失败",
                        TraceContext.getTraceId(),
                        Map.of("fieldErrors", fieldErrors)
                ));
    }
}
