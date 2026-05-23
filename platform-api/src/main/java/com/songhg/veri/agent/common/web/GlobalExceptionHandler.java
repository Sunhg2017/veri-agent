package com.songhg.veri.agent.common.web;

import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.api.FieldErrorItem;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.error.PlatformAccessDeniedException;
import com.songhg.veri.agent.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
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
        logHandledException(code, exception);
        return errorResponse(code, exception.getMessage(), null);
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
        logHandledException(ErrorCode.VALIDATION_ERROR, exception);
        return errorResponse(ErrorCode.VALIDATION_ERROR, "上传文件超过大小上限", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        logHandledException(ErrorCode.UNAUTHORIZED, exception);
        return errorResponse(ErrorCode.UNAUTHORIZED, "认证失败或会话已失效", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        if (exception instanceof PlatformAccessDeniedException platformException) {
            log.warn(
                    "Handled platform-api access denied, trace_id={}, code={}, permission={}, resourceType={}, resourceId={}",
                    TraceContext.getTraceId(),
                    platformException.getErrorCode().name(),
                    platformException.getPermission(),
                    platformException.getResourceType(),
                    platformException.getResourceId()
            );
        } else {
            logHandledException(ErrorCode.FORBIDDEN, exception);
        }
        return errorResponse(ErrorCode.FORBIDDEN, "权限不足", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        logHandledException(ErrorCode.NOT_FOUND, exception);
        return errorResponse(ErrorCode.NOT_FOUND, "资源不存在", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unexpected platform-api exception, trace_id={}", TraceContext.getTraceId(), exception);
        return errorResponse(ErrorCode.INTERNAL_ERROR, "系统异常", null);
    }

    private ResponseEntity<ApiResponse<Map<String, List<FieldErrorItem>>>> validationError(
            List<FieldErrorItem> fieldErrors
    ) {
        return errorResponse(
                ErrorCode.VALIDATION_ERROR,
                "请求字段校验失败",
                Map.of("fieldErrors", fieldErrors)
        );
    }

    private void logHandledException(ErrorCode code, Exception exception) {
        log.warn(
                "Handled platform-api exception, trace_id={}, code={}, type={}, message={}",
                TraceContext.getTraceId(),
                code.name(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> errorResponse(ErrorCode code, String message, T data) {
        return ResponseEntity
                .status(code.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(code.name(), message, TraceContext.getTraceId(), data));
    }
}
