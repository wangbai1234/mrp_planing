package com.mrp.importexport.excel;

public record ParseError(
        int rowNumber,
        String column,
        String originalValue,
        String errorCode,
        String message
) {
    public static ParseError of(int row, String col, String value, String code, String msg) {
        return new ParseError(row, col, value, code, msg);
    }
}
