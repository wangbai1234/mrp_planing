package com.mrp.common.exception;

import com.mrp.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("Business exception at {}: {} - {}", req.getRequestURI(), ex.getCode(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>error(ex.getCode(), ex.getMessage())
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        log.warn("Not found at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>error(ex.getCode(), ex.getMessage())
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ValidationError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>error("MRP_VALIDATION_ERROR", "Validation failed", details)
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(ConcurrentChangeException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentChange(ConcurrentChangeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>error("MRP_CONCURRENT_CHANGE", ex.getMessage())
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        log.warn("Illegal state at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>error("MRP_ILLEGAL_STATE", ex.getMessage())
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Invalid argument at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>error("MRP_VALIDATION_ERROR", ex.getMessage())
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        log.warn("HTTP exception at {}: {} - {}", req.getRequestURI(), ex.getStatusCode(), ex.getReason());
        String code = switch (ex.getStatusCode().value()) {
            case 403 -> "FORBIDDEN";
            case 404 -> "MRP_NOT_FOUND";
            case 400 -> "MRP_VALIDATION_ERROR";
            default -> "MRP_HTTP_ERROR";
        };
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.<Void>error(code, ex.getReason() == null ? "Request failed" : ex.getReason())
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.validation.BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>error("MRP_VALIDATION_ERROR", "请求参数缺失或格式错误")
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMethod() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.<Void>error("MRP_METHOD_NOT_ALLOWED", "请求方法不支持")
                        .withTraceId(MDC.get("traceId")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error at {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>error("MRP_INTERNAL_ERROR", "Internal server error")
                        .withTraceId(MDC.get("traceId")));
    }

    public record ValidationError(String field, String message) {}
}
