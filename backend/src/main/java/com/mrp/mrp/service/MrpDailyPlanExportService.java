package com.mrp.mrp.service;

import com.mrp.mrp.controller.MrpDailyPlanningController;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import com.mrp.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MrpDailyPlanExportService {
    private static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy年M月");
    private static final List<String> FIXED_HEADERS = List.of("整机料号", "来源月份", "末级需求物料", "类型 / 供应", "工厂 / 仓库", "单位", "合计");
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "MACHINE_OUTPUT", "整机产出", "FINISHED_GOOD", "整机", "HEAD", "机头",
            "HEAD_ASSEMBLY", "机头", "PCBA", "PCBA", "RAW_MATERIAL", "原材料", "OTHER", "其他");
    private static final Map<String, String> SUPPLY_LABELS = Map.of(
            "SELF_MADE", "自制", "PURCHASED", "外购", "OUTSOURCED", "委外", "UNMAINTAINED", "未配置");

    private final RollingPlanningQueryService query;

    public MrpDailyPlanExportService(RollingPlanningQueryService query) {
        this.query = query;
    }

    public byte[] export(Long batchId) throws IOException {
        return export(batchId, "MACHINE_OUTPUT");
    }

    public byte[] export(Long batchId, String view) throws IOException {
        if (!Objects.equals(view, "MACHINE_OUTPUT") && !Objects.equals(view, "TERMINAL"))
            throw new IllegalArgumentException("导出视图无效");
        if ("TERMINAL".equals(view) && !CurrentUser.hasPermission("mrp_shortage:view"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权导出物料需求");
        if ("MACHINE_OUTPUT".equals(view) && !CurrentUser.hasPermission("mrp_plan:export"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权导出整机排产");
        var first = query.grid(batchId, 1, PAGE_SIZE, null, null, view, false);
        if (first.totalRows == 0) return new byte[0];
        List<LocalDate> dates = first.calendarDays;
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            boolean terminalView = "TERMINAL".equals(view);
            Sheet sheet = workbook.createSheet(terminalView ? "物料缺料" : "整机排产");
            createHeader(workbook, sheet, dates, terminalView);
            CellStyle quantityStyle = quantityStyle(workbook);
            Map<String, ExportRow> machineRows = new LinkedHashMap<>();
            Map<String, ExportRow> terminalRows = new LinkedHashMap<>();
            int totalPages = Math.max(1, (first.totalRows + PAGE_SIZE - 1) / PAGE_SIZE);
            for (int currentPage = 1; currentPage <= totalPages; currentPage++) {
                var result = currentPage == 1 ? first : query.grid(batchId, currentPage, PAGE_SIZE, null, null, view, false);
                for (MrpDailyPlanningController.GridRow plan : result.rows) {
                    boolean machineOutput = "MACHINE_OUTPUT".equals(plan.terminalType);
                    Map<String, ExportRow> grouped = machineOutput ? machineRows : terminalRows;
                    String typeSupply = machineOutput ? "整机产出 / 台"
                            : label(TYPE_LABELS, plan.terminalType) + " / " + label(SUPPLY_LABELS, plan.supplyMode);
                    String sourceMonth = plan.sourceMonth == null ? "" : plan.sourceMonth.toString();
                    String key = machineOutput ? String.join("|", value(plan.rootMaterialCode), sourceMonth, "整机产出", value(plan.factoryCode))
                            : String.join("|", value(plan.rootMaterialCode), sourceMonth, value(plan.terminalMaterialCode), value(plan.terminalType), value(plan.supplyMode), value(plan.factoryCode));
                    ExportRow row = grouped.computeIfAbsent(key, ignored -> new ExportRow(value(plan.rootMaterialCode), sourceMonth,
                            machineOutput ? "整机产出" : value(plan.terminalMaterialCode), typeSupply,
                            (plan.warehouseName == null ? value(plan.warehouseCode) : plan.warehouseName) + " / " + value(plan.factoryCode),
                            value(plan.materialUnit), dates));
                    row.add(plan.dayQuantities);
                }
            }
            int rowNumber = writeRows(sheet, machineRows, dates, 3, quantityStyle);
            writeRows(sheet, terminalRows, dates, rowNumber, quantityStyle);
            sheet.createFreezePane(FIXED_HEADERS.size(), 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, sheet.getLastRowNum()), 0, FIXED_HEADERS.size() + dates.size() - 1));
            for (int column = 0; column < FIXED_HEADERS.size(); column++) sheet.setColumnWidth(column, column < 2 ? 22 * 256 : 16 * 256);
            for (int column = FIXED_HEADERS.size(); column < FIXED_HEADERS.size() + dates.size(); column++) sheet.setColumnWidth(column, 12 * 256);
            workbook.write(output);
            workbook.dispose();
            return output.toByteArray();
        }
    }

    private int writeRows(Sheet sheet, Map<String, ExportRow> grouped, List<LocalDate> dates, int rowNumber, CellStyle quantityStyle) {
        for (ExportRow value : grouped.values()) {
            Row row = sheet.createRow(rowNumber++);
            int column = 0;
            row.createCell(column++).setCellValue(value.root);
            row.createCell(column++).setCellValue(value.sourceMonth);
            row.createCell(column++).setCellValue(value.terminal);
            row.createCell(column++).setCellValue(value.typeSupply);
            row.createCell(column++).setCellValue(value.factoryWarehouse);
            row.createCell(column++).setCellValue(value.unit);
            setQuantity(row.createCell(column++), value.total.toPlainString(), quantityStyle);
            for (LocalDate date : dates) setQuantity(row.createCell(column++), value.quantities.getOrDefault(date.toString(), BigDecimal.ZERO).toPlainString(), quantityStyle);
        }
        return rowNumber;
    }

    private String label(Map<String, String> labels, String value) {
        if (value == null || value.isBlank()) return "未配置";
        return labels.getOrDefault(value, value);
    }

    private void createHeader(Workbook workbook, Sheet sheet, List<LocalDate> dates, boolean terminalView) {
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 15);
        titleStyle.setFont(titleFont); titleStyle.setAlignment(HorizontalAlignment.CENTER); titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        Row title = sheet.createRow(0); title.setHeightInPoints(28);
        title.createCell(0).setCellValue(terminalView ? "物料缺料 · 末级净需求" : "整机排产"); title.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, FIXED_HEADERS.size() + dates.size() - 1));

        CellStyle groupStyle = headerStyle(workbook, true);
        CellStyle dateStyle = headerStyle(workbook, false);
        Row months = sheet.createRow(1); Row headers = sheet.createRow(2);
        for (int column = 0; column < FIXED_HEADERS.size(); column++) {
            months.createCell(column).setCellStyle(groupStyle);
            Cell header = headers.createCell(column); header.setCellValue(FIXED_HEADERS.get(column)); header.setCellStyle(dateStyle);
        }
        int start = 0;
        while (start < dates.size()) {
            LocalDate month = dates.get(start).withDayOfMonth(1);
            int end = start;
            while (end + 1 < dates.size() && dates.get(end + 1).withDayOfMonth(1).equals(month)) end++;
            int from = FIXED_HEADERS.size() + start, to = FIXED_HEADERS.size() + end;
            Cell monthCell = months.createCell(from); monthCell.setCellValue(month.format(MONTH)); monthCell.setCellStyle(groupStyle);
            for (int i = from + 1; i <= to; i++) months.createCell(i).setCellStyle(groupStyle);
            if (to > from) sheet.addMergedRegion(new CellRangeAddress(1, 1, from, to));
            for (int i = start; i <= end; i++) {
                Cell dateCell = headers.createCell(FIXED_HEADERS.size() + i);
                dateCell.setCellValue(dates.get(i).toString()); dateCell.setCellStyle(dateStyle);
            }
            start = end + 1;
        }
    }

    private CellStyle headerStyle(Workbook workbook, boolean group) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setBold(true); style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER); style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN); style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    private void setQuantity(Cell cell, String value, CellStyle style) {
        if (value == null || value.isBlank() || new BigDecimal(value).signum() == 0) return;
        cell.setCellValue(new BigDecimal(value).doubleValue());
        cell.setCellStyle(style);
    }

    private CellStyle quantityStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.######"));
        style.setAlignment(HorizontalAlignment.RIGHT); return style;
    }

    private String value(String value) { return value == null ? "" : value; }

    private static final class ExportRow {
        private final String root;
        private final String sourceMonth;
        private final String terminal;
        private final String typeSupply;
        private final String factoryWarehouse;
        private final String unit;
        private final Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        private BigDecimal total = BigDecimal.ZERO;
        private ExportRow(String root, String sourceMonth, String terminal, String typeSupply, String factoryWarehouse, String unit, List<LocalDate> dates) {
            this.root = root; this.sourceMonth = sourceMonth; this.terminal = terminal; this.typeSupply = typeSupply; this.factoryWarehouse = factoryWarehouse; this.unit = unit;
            dates.forEach(date -> quantities.put(date.toString(), BigDecimal.ZERO));
        }
        private void add(Map<String, String> daily) {
            daily.forEach((date, value) -> {
                BigDecimal quantity = new BigDecimal(value);
                quantities.merge(date, quantity, BigDecimal::add);
                total = total.add(quantity);
            });
        }
    }
}
