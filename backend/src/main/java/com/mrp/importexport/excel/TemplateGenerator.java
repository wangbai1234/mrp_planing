package com.mrp.importexport.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import com.mrp.mrp.domain.ProductionCalendarEntry;

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
                new TemplateColumn("产地", "_origin_place", "请在此列填写产地", false),
                new TemplateColumn("物料二级分类编码", "_category_code", "请填写物料二级分类编码（如：08001、08002等）", false)
        );

        List<List<String>> sampleData = List.of(
                List.of("示例", "3308AA800180", "0201,贴片电容,330nF,10%,25V,X5R", "HM6801", "V334K0201X5R250NXT", "", "15000", "15000", "上海", "限制使用", "原材料", "是", "84", "中国", "08060"),
                List.of("示例", "6610AA800551", "PCB,硬板,DR2412,MAIN BOARD", "DR2412", "LBCM052F1-1(JFG&H)", "", "3000", "3000", "上海", "限制使用", "原材料", "是", "30", "中国", "08095")
        );

        return generate(columns, sampleData, "物料导入模板");
    }

    public byte[] generateStockPolicyTemplate() throws IOException {
        List<TemplateColumn> columns = List.of(
                new TemplateColumn("仓库编码*", "warehouse", "必填，与物料料号一起唯一", true),
                new TemplateColumn("物料料号*", "material", "必填", true),
                new TemplateColumn("安全库存*", "safety", "必填，数字，不能为负，且不能大于预警库存", true),
                new TemplateColumn("预警库存*", "warning", "必填，数字，不能为负", true));
        List<List<String>> sample = List.of(List.of("示例", "M001", "10", "40"));
        return generate(columns, sample, "库存控制策略");
    }

    public byte[] generateFactoryWarehouseTemplate() throws IOException {
        List<TemplateColumn> columns = List.of(
                new TemplateColumn("工厂编码*", "factory", "必填，须为已维护的工厂", true),
                new TemplateColumn("仓库编码*", "warehouse", "必填，一个仓库只能对应一个工厂", true),
                new TemplateColumn("仓库名称*", "warehouseName", "必填", true),
                new TemplateColumn("仓库管理员", "warehouseManager", "选填", false),
                new TemplateColumn("对应供应商", "supplierName", "选填", false),
                new TemplateColumn("是否良品仓*", "goodWarehouse", "必填：是或否", true),
                new TemplateColumn("仓库属性*", "warehouseAttribute", "必填：自有仓、加工厂外协仓、ODM仓、NC-ODM虚拟仓、海外三方仓、OEM仓、采购仓", true),
                new TemplateColumn("优先级", "priorityNo", "选填，正整数；数字越小越先扣，空值按导入顺序", false),
                new TemplateColumn("启用", "enabled", "选填：启用或停用，默认启用", false));
        return generate(columns, List.of(List.of("示例：永惠", "WH01", "示例仓", "", "", "是", "自有仓", "1", "启用")), "工厂仓库映射");
    }

    public byte[] generateMaterialPolicyTemplate() throws IOException {
        List<TemplateColumn> columns = List.of(
                new TemplateColumn("物料料号*", "material", "必填，一个物料一行", true),
                new TemplateColumn("供应方式*", "supply", "必填：自制、外购、委外", true),
                new TemplateColumn("下钻策略*", "explosion", "必填：继续下钻、停止下钻", true));
        return generate(columns, List.of(List.of("示例", "自制", "继续下钻")), "物料策略");
    }

    public byte[] generateProductionCalendarTemplate() throws IOException {
        return generate(calendarColumns(), List.of(List.of("示例爱培科", "2026-10-01", "是", "1000", "台", "示例行不导入")), "生产日历");
    }

    private List<TemplateColumn> calendarColumns() {
        return List.of(new TemplateColumn("工厂编码*", "factory", "必填：已维护且启用的工厂", true),
                new TemplateColumn("日期*", "date", "必填，格式 yyyy-MM-dd", true),
                new TemplateColumn("生产日*", "production", "必填，填是或否", true),
                new TemplateColumn("日产能*", "capacity", "整机台/日；生产日填正整数，休息日填0", true),
                new TemplateColumn("单位", "capacityUnit", "生产日必填，固定填台；旧单位须重新确认", false),
                new TemplateColumn("备注", "reason", "选填，最多256个字符", false));
    }

    public byte[] generateProductionCalendarExport(List<ProductionCalendarEntry> rows) throws IOException {
        return generate(calendarColumns(), rows.stream().map(row -> List.of(row.getFactoryCode(), row.getCalendarDate().toString(),
                Boolean.TRUE.equals(row.getProductionDay()) ? "是" : "否",
                row.getDailyCapacity() == null ? "" : row.getDailyCapacity().toPlainString(),
                row.getCapacityUnit() == null ? "" : row.getCapacityUnit(), row.getReason() == null ? "" : row.getReason())).toList(), "生产日历");
    }

    public byte[] generateBusinessWeekTemplate() throws IOException {
        List<TemplateColumn> columns = List.of(
                new TemplateColumn("业务月份*", "month", "必填，格式 yyyy-MM；出现的月份必须整月覆盖", true),
                new TemplateColumn("周编码*", "week", "必填，同月内不能重复", true),
                new TemplateColumn("开始日期*", "start", "必填，格式 yyyy-MM-dd", true),
                new TemplateColumn("结束日期*", "end", "必填，格式 yyyy-MM-dd，各周连续且不重叠", true));
        return generate(columns, List.of(
                List.of("示例", "W1", "2026-10-01", "2026-10-07"),
                List.of("示例", "W2", "2026-10-08", "2026-10-31")), "业务周期间");
    }

    public byte[] generateConfigErrorReport(List<String> headers, List<List<String>> rawRows, List<ParseError> errors) throws IOException {
        return generateInventoryErrorReport(headers, rawRows, errors);
    }

    public byte[] generateForecastTemplate() throws IOException {
        List<TemplateColumn> columns = new ArrayList<>();
        columns.add(new TemplateColumn("工厂", "factory", "必填，填写已维护且启用的工厂编码", true));
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
                List.of("永惠", "整机", "3308AA800180", "模具A", "正常", "1000", "1200", "1100", "1300", "1400", "1500", "7500"),
                List.of("爱培科", "整机", "6610AA800551", "", "", "500", "600", "550", "650", "700", "750", "3750")
        );

        return generate(columns, sampleData, "经营计划导入模板");
    }

    public byte[] generateInventoryTemplate() throws IOException {
        List<TemplateColumn> columns = List.of(
                new TemplateColumn("仓库编码*", "warehouse_code", "请填写工厂仓库映射中已发布的仓库编码（本列必填）", true),
                new TemplateColumn("仓库名称", "warehouse_name", "请填写仓库名称（可选）", false),
                new TemplateColumn("库存分类", "inventory_category", "请填写库存分类（非必填，仅供展示和查询）", false),
                new TemplateColumn("料号*", "material_id", "请在此列填写料号（本列必填）", true),
                new TemplateColumn("供应商名称", "supplier_name", "请填写供应商名称（非必填）", false),
                new TemplateColumn("库存数量*", "inventory_qty", "请输入该仓库库存数量；品质由仓库主数据决定（本列必填）", true),
                new TemplateColumn("备注", "remark", "请填写备注（非必填，最多512个字符）", false)
        );

        List<List<String>> sampleData = List.of(
                List.of("XA378_YONGHUI", "XA378永惠成品仓", "原材料", "3308AA800180", "供应商A", "1000", "常规库存"),
                List.of("XA400_APK", "XA400爱培科仓", "", "6610AA800551", "", "25", "")
        );

        return generate(columns, sampleData, "库存导入模板");
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
            String headerError = errorMap.get(1);
            errorHeaderCell.setCellValue(headerError == null ? "错误原因" : "错误原因：" + headerError);
            errorHeaderCell.setCellStyle(headerError == null ? headerStyle : errorStyle);

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
            String headerError = errorMap.get(1);
            errorHeaderCell.setCellValue(headerError == null ? "错误原因" : "错误原因：" + headerError);
            errorHeaderCell.setCellStyle(headerError == null ? headerStyle : errorStyle);

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
