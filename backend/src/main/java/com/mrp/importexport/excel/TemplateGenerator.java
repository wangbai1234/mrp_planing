package com.mrp.importexport.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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
