package com.mrp.importexport.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TemplateGenerator {

    public byte[] generateMaterialTemplate() throws IOException {
        List<TemplateColumn> columns = List.of(
                new TemplateColumn("展示名称", "_display_name", "本列内容不可修改", false),
                new TemplateColumn("料号*", "_code", "请在此列填写料号（本列必填）", true),
                new TemplateColumn("物料名称*", "_name", "请在此列填写物料名称（本列必填）", true),
                new TemplateColumn("项目型号", "_project_model", "请在此列填写项目型号", false),
                new TemplateColumn("规格型号", "_spec_model", "请在此列填写规格型号", false),
                new TemplateColumn("单位", "_unit", "请在此列填写单位", false),
                new TemplateColumn("MOQ最小起订量", "_moq", "请输入数字", false),
                new TemplateColumn("MPQ最小包装", "_mpq", "请输入数字", false),
                new TemplateColumn("所属区域", "_region", "请在此列填写所属区域的选项，选项内容分别为：上海、惠州", false),
                new TemplateColumn("属性", "_attribute", "请在此列填写属性（限制使用、差异、通用）", false),
                new TemplateColumn("物料类别", "_category", "请从下拉菜单中选择（原材料、整机等）", false),
                new TemplateColumn("是否有效*", "_is_active", "请从下拉菜单中选择（本列必填）：是/否", true),
                new TemplateColumn("L-T提前期", "_lead_time", "请输入数字（天）", false),
                new TemplateColumn("产地", "_origin_place", "请在此列填写产地", false)
        );

        List<List<String>> sampleData = List.of(
                List.of("示例", "3308AA800180", "0201,贴片电容,330nF,10%,25V,X5R", "HM6801", "V334K0201X5R250NXT", "", "15000", "15000", "上海", "限制使用", "原材料", "是", "84", "中国"),
                List.of("示例", "6610AA800551", "PCB,硬板,DR2412,MAIN BOARD", "DR2412", "LBCM052F1-1(JFG&H)", "", "3000", "3000", "上海", "限制使用", "原材料", "是", "30", "中国")
        );

        return generate(columns, sampleData, "物料导入模板");
    }

    public byte[] generateForecastTemplate() throws IOException {
        List<TemplateColumn> columns = new ArrayList<>();
        columns.add(new TemplateColumn("工厂", "factory", "请在此列填写工厂代码（本列必填）", true));
        columns.add(new TemplateColumn("形态", "form_type", "请在此列填写形态（本列必填）", true));
        columns.add(new TemplateColumn("料号", "material_id", "请在此列填写料号（本列必填）", true));
        columns.add(new TemplateColumn("模具", "mold", "请在此列填写模具（非必填）", false));
        columns.add(new TemplateColumn("状态", "status", "请在此列填写状态（非必填）", false));

        // 从当前月份开始生成6个月份列
        YearMonth currentMonth = YearMonth.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 0; i < 6; i++) {
            YearMonth month = currentMonth.plusMonths(i);
            String monthStr = month.format(formatter);
            columns.add(new TemplateColumn(monthStr, "fcst_" + monthStr, "请输入数量", false));
        }

        columns.add(new TemplateColumn("fcst-total", "fcst_total", "系统自动计算，无需填写", false));

        List<List<String>> sampleData = List.of(
                List.of("F001", "整机", "3308AA800180", "模具A", "正常", "1000", "1200", "1100", "1300", "1400", "1500", "7500"),
                List.of("F002", "组件", "6610AA800551", "", "", "500", "600", "550", "650", "700", "750", "3750")
        );

        return generate(columns, sampleData, "经营计划导入模板");
    }

    public byte[] generateInventoryTemplate() throws IOException {
        List<TemplateColumn> columns = List.of(
                new TemplateColumn("产品线", "product_line", "请在此列填写产品线", false),
                new TemplateColumn("库存分类*", "inventory_category", "请在此列填写库存分类（本列必填）", true),
                new TemplateColumn("料号*", "material_id", "请在此列填写料号（本列必填）", true),
                new TemplateColumn("供应商名称*", "supplier_name", "请在此列填写供应商名称（本列必填）", true),
                new TemplateColumn("产品模式", "product_mode", "请在此列填写产品模式", false),
                new TemplateColumn("ODM供应商仓", "odm_supplier_qty", "请输入数量", false),
                new TemplateColumn("XA400咪哈成品仓", "xa400_qty", "请输入数量", false),
                new TemplateColumn("XA378永惠成品仓", "xa378_qty", "请输入数量", false),
                new TemplateColumn("XA226惠州仓", "xa226_qty", "请输入数量", false),
                new TemplateColumn("分类", "classification", "请在此列填写分类", false),
                new TemplateColumn("产品条形码", "barcode", "请在此列填写产品条形码", false),
                new TemplateColumn("备注", "remark", "请在此列填写备注", false),
                new TemplateColumn("订单未交数量", "order_pending_qty", "请输入数量", false),
                new TemplateColumn("销售状态", "sales_status", "请在此列填写销售状态", false),
                new TemplateColumn("父记录", "parent_record", "请在此列填写父记录", false)
        );

        return generate(columns, List.of(), "库存导入模板");
    }

    public byte[] generateInventoryErrorReport(List<String> headers, List<List<String>> rawRows, List<ParseError> errors) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("错误数据");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Error style (red font)
            CellStyle errorStyle = workbook.createCellStyle();
            Font errorFont = workbook.createFont();
            errorFont.setColor(IndexedColors.RED.getIndex());
            errorStyle.setFont(errorFont);

            // Build error map: rowNumber -> error message
            Map<Integer, String> errorMap = new HashMap<>();
            for (ParseError error : errors) {
                String existing = errorMap.get(error.rowNumber());
                String msg = error.column() + ": " + error.message();
                errorMap.put(error.rowNumber(), existing != null ? existing + "; " + msg : msg);
            }

            // Row 1: Headers + "错误原因"
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            Cell errorHeaderCell = headerRow.createCell(headers.size());
            errorHeaderCell.setCellValue("错误原因");
            errorHeaderCell.setCellStyle(headerStyle);

            // Data rows
            for (int rowIdx = 0; rowIdx < rawRows.size(); rowIdx++) {
                Row dataRow = sheet.createRow(rowIdx + 1);
                List<String> rowData = rawRows.get(rowIdx);
                for (int colIdx = 0; colIdx < rowData.size(); colIdx++) {
                    dataRow.createCell(colIdx).setCellValue(rowData.get(colIdx));
                }
                // Add error message for this row (rowNumber = rowIdx + DATA_START_ROW + 1)
                // Template: row 0 = header, row 1 = tips, row 2+ = data
                int rowNum = rowIdx + 3;
                String errorMsg = errorMap.get(rowNum);
                Cell errorCell = dataRow.createCell(rowData.size());
                if (errorMsg != null) {
                    errorCell.setCellValue(errorMsg);
                    errorCell.setCellStyle(errorStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i <= headers.size(); i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateForecastErrorReport(List<String> headers, List<List<String>> rawRows, List<ParseError> errors) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("错误数据");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Error style (red background)
            CellStyle errorStyle = workbook.createCellStyle();
            Font errorFont = workbook.createFont();
            errorFont.setColor(IndexedColors.RED.getIndex());
            errorStyle.setFont(errorFont);

            // Build error map: rowNumber -> error message
            Map<Integer, String> errorMap = new HashMap<>();
            for (ParseError error : errors) {
                String existing = errorMap.get(error.rowNumber());
                String msg = error.column() + ": " + error.message();
                errorMap.put(error.rowNumber(), existing != null ? existing + "; " + msg : msg);
            }

            // Row 1: Headers + "错误原因"
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            Cell errorHeaderCell = headerRow.createCell(headers.size());
            errorHeaderCell.setCellValue("错误原因");
            errorHeaderCell.setCellStyle(headerStyle);

            // Data rows
            for (int rowIdx = 0; rowIdx < rawRows.size(); rowIdx++) {
                Row dataRow = sheet.createRow(rowIdx + 1);
                List<String> rowData = rawRows.get(rowIdx);
                for (int colIdx = 0; colIdx < rowData.size(); colIdx++) {
                    dataRow.createCell(colIdx).setCellValue(rowData.get(colIdx));
                }
                // Add error message for this row (rowNumber = rowIdx + DATA_START_ROW + 1)
                int rowNum = rowIdx + 3; // Row 0=header, Row 1=tips, Row 2+=data, so data starts at row 3 in Excel
                String errorMsg = errorMap.get(rowNum);
                Cell errorCell = dataRow.createCell(rowData.size());
                if (errorMsg != null) {
                    errorCell.setCellValue(errorMsg);
                    errorCell.setCellStyle(errorStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i <= headers.size(); i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] generate(List<TemplateColumn> columns, List<List<String>> sampleData, String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Required style (red background)
            CellStyle requiredStyle = workbook.createCellStyle();
            Font requiredFont = workbook.createFont();
            requiredFont.setColor(IndexedColors.RED.getIndex());
            requiredStyle.setFont(requiredFont);

            // Tip style (gray)
            CellStyle tipStyle = workbook.createCellStyle();
            Font tipFont = workbook.createFont();
            tipFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            tipFont.setItalic(true);
            tipStyle.setFont(tipFont);

            // Row 1: Display names (Chinese)
            Row displayRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = displayRow.createCell(i);
                cell.setCellValue(columns.get(i).displayName());
                cell.setCellStyle(headerStyle);
            }

            // Row 2: Tips (skip field identifiers row)
            Row tipRow = sheet.createRow(1);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = tipRow.createCell(i);
                cell.setCellValue(columns.get(i).tip());
                if (columns.get(i).required()) {
                    cell.setCellStyle(requiredStyle);
                } else {
                    cell.setCellStyle(tipStyle);
                }
            }

            // Sample data rows
            for (int rowIdx = 0; rowIdx < sampleData.size(); rowIdx++) {
                Row dataRow = sheet.createRow(2 + rowIdx);
                List<String> rowData = sampleData.get(rowIdx);
                for (int colIdx = 0; colIdx < rowData.size(); colIdx++) {
                    dataRow.createCell(colIdx).setCellValue(rowData.get(colIdx));
                }
            }

            // Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
                // Min width
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public record TemplateColumn(String displayName, String fieldName, String tip, boolean required) {}
}
