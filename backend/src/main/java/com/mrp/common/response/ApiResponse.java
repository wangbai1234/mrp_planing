package com.mrp.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorBody error,
        String traceId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, null), null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, details), null, Instant.now());
    }

    public ApiResponse<T> withTraceId(String traceId) {
        return new ApiResponse<>(success, data, error, traceId, timestamp);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            Object details
    ) {}
}
