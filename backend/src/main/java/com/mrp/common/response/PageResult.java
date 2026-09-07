package com.mrp.common.response;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        int total,
        int page,
        int pageSize,
        int totalPages
) {
    public PageResult(List<T> items, int total, int page, int pageSize) {
        this(items, total, page, pageSize, (total + pageSize - 1) / pageSize);
    }
}
