package com.mrp.common.exception;

import java.io.Serial;

public class NotFoundException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotFoundException(String resource, Object id) {
        super("MRP_NOT_FOUND", "%s not found: %s".formatted(resource, id));
    }
}
