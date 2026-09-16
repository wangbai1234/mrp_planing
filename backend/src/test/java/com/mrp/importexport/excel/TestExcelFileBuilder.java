package com.mrp.importexport.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestExcelFileBuilder {

    /**
     * Creates a forecast Excel file matching the parser's expected column layout:
     * Col 0: 业务线/工厂 (factory)
     * Col 1: 形态 (form type)
     * Col 2: 料号 (material ID)
     * Col 3: 模具 (mold)
     * Col 4: 状态 (status)
     * Col 5-10: 6 month fcst headers (dates) + data (quantities)
     */
    public static Path createForecastFile(Path dir, String name, String[][] dataRows, String... monthHeaders) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("经营计划导入");
            CreationHelper helper = wb.getCreationHelper();

            // Row 0: Header (columns 0-4 for data, 5-10 for months)
            String[] headers = {"业务线/工厂", "形态", "料号", "模具", "状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            // Month columns at 5-10 as formatted text strings (e.g. "2026-12")
            for (int i = 0; i < monthHeaders.length && i < 6; i++) {
                headerRow.createCell(5 + i).setCellValue(monthHeaders[i]);
            }

            // Row 1: Tips
            Row tipsRow = sheet.createRow(1);
            for (int i = 0; i < 6; i++) {
                tipsRow.createCell(5 + i).setCellValue("fcst");
            }

            // Data rows (columns 0-4 text, 5-10 numeric quantities)
            for (int r = 0; r < dataRows.length; r++) {
                Row dataRow = sheet.createRow(2 + r);
                String[] row = dataRows[r];
                for (int c = 0; c < row.length; c++) {
                    if (row[c] != null) {
                        if (c >= 5 && c <= 10) {
                            try {
                                dataRow.createCell(c).setCellValue(Double.parseDouble(row[c]));
                            } catch (NumberFormatException e) {
                                dataRow.createCell(c).setCellValue(row[c]);
                            }
                        } else {
                            Cell cell = dataRow.createCell(c);
                            cell.setCellValue(row[c]);
                        }
                    }
                }
            }

            Path file = dir.resolve(name);
            Files.createDirectories(dir);
            try (var os = Files.newOutputStream(file)) {
                wb.write(os);
            }
            return file;
        }
    }

    public static Path createInventoryFile(Path dir, String name, String[][] dataRows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("库存快照导入");

            String[] headers = {"物料编码", "仓库编码", "仓库名称", "仓库类型", "工厂编码", "数量"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            for (int r = 0; r < dataRows.length; r++) {
                Row dataRow = sheet.createRow(1 + r);
                String[] row = dataRows[r];
                for (int c = 0; c < row.length; c++) {
                    if (row[c] != null) {
                        if (c == 5) {
                            try {
                                dataRow.createCell(c).setCellValue(Long.parseLong(row[c]));
                            } catch (NumberFormatException e) {
                                dataRow.createCell(c).setCellValue(row[c]);
                            }
                        } else {
                            dataRow.createCell(c).setCellValue(row[c]);
                        }
                    }
                }
            }

            Path file = dir.resolve(name);
            Files.createDirectories(dir);
            try (var os = Files.newOutputStream(file)) {
                wb.write(os);
            }
            return file;
        }
    }

    public static Path createEmptyInventoryFile(Path dir) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("库存快照导入");

            String[] headers = {"物料编码", "仓库编码", "仓库名称", "仓库类型", "工厂编码", "数量"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Path file = dir.resolve("inventory-empty.xlsx");
            Files.createDirectories(dir);
            try (var os = Files.newOutputStream(file)) {
                wb.write(os);
            }
            return file;
        }
    }
}
