package com.mrp.importexport.excel;

import java.util.List;

public record ParseResult<T>(
        List<T> rows,
        List<ParseError> errors,
        List<String> recognizedMonths,
        int totalRows,
        int successRows,
        int errorRows,
        List<List<String>> rawRows,
        List<String> headers
) {
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isSuccess() {
        return errorRows == 0 && successRows > 0;
    }
}
