package com.songhg.veri.agent.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    OK(HttpStatus.OK),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),
    SECRET_REQUIRED(HttpStatus.BAD_REQUEST),
    SECRET_POLICY_VIOLATION(HttpStatus.BAD_REQUEST),
    SECRET_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
    MODEL_POLICY_VIOLATION(HttpStatus.BAD_REQUEST),
    BUDGET_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    SENSITIVE_CONTENT_BLOCKED(HttpStatus.BAD_REQUEST),
    MODEL_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    AUDIT_WRITE_PENDING(HttpStatus.ACCEPTED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
