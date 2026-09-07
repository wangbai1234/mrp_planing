package com.mrp.common.exception;

import java.io.Serial;

public class ValidationException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super("MRP_VALIDATION_ERROR", message);
    }

    public ValidationException(String message, Throwable cause) {
        super("MRP_VALIDATION_ERROR", message, cause);
    }
}
