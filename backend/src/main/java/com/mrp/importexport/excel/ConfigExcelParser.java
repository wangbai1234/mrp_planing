package com.mrp.importexport.excel;

import com.mrp.mrp.domain.BusinessWeek;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.MaterialPolicy;
import com.mrp.mrp.domain.ProductionCalendarEntry;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工厂映射、物料策略、生产日历、业务周的导入。
 * 第 1 行表头、第 2 行说明跳过、第 3 行起为数据；首列以「示例」开头的行不导入。
 * 有任一错误则不返回行，整份不导入。
 */
@Component
public class ConfigExcelParser {

    private static final DataFormatter FORMATTER = new DataFormatter();
    private final com.mrp.mrp.service.FactoryMasterService factoryMaster;
    public ConfigExcelParser() { this(null); }
    @Autowired
    public ConfigExcelParser(com.mrp.mrp.service.FactoryMasterService factoryMaster) { this.factoryMaster = factoryMaster; }
    private static final Map<String, String> SUPPLY = Map.of("自制", "SELF_MADE", "外购", "PURCHASED", "委外", "OUTSOURCED");
    private static final Map<String, String> EXPLOSION = Map.of("继续下钻", "EXPLODE", "停止下钻", "STOP");

    public ParseResult<FactoryWarehouseMapping> parseFactoryWarehouse(InputStream input) throws IOException {
        List<FactoryWarehouseMapping> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        List<List<String>> rawRows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        Set<String> warehouses = new LinkedHashSet<>();
        read(input, 9, rawRows, (row, line) -> {
            String factory = text(row.getCell(0));
            if (factory.startsWith("示例")) return;
            String warehouse = text(row.getCell(1));
            String warehouseName = text(row.getCell(2));
            String manager = text(row.getCell(3));
            String supplier = text(row.getCell(4));
            String good = text(row.getCell(5));
            String attribute = text(row.getCell(6));
            String priority = text(row.getCell(7));
            String enabled = text(row.getCell(8));
            if (factory.isEmpty()) errors.add(ParseError.of(line, "工厂编码", "", "REQUIRED", "工厂编码不能为空"));
            else if (factoryMaster != null && !factoryMaster.enabled(factory))
                errors.add(ParseError.of(line, "工厂编码", factory, "INVALID_FACTORY", "工厂不存在或已停用"));
            if (warehouse.isEmpty()) errors.add(ParseError.of(line, "仓库编码", "", "REQUIRED", "仓库编码不能为空"));
            if (warehouseName.isEmpty()) errors.add(ParseError.of(line, "仓库名称", "", "REQUIRED", "仓库名称不能为空"));
            if (!Set.of("是", "否").contains(good)) errors.add(ParseError.of(line, "是否良品仓", good, "ENUM", "是否良品仓只能填是或否"));
            if (!Set.of("自有仓", "加工厂外协仓", "ODM仓", "NC-ODM虚拟仓", "海外三方仓", "OEM仓", "采购仓").contains(attribute))
                errors.add(ParseError.of(line, "仓库属性", attribute, "ENUM", "仓库属性不在可选范围内"));
            Integer priorityNo = null;
            if (!priority.isEmpty()) {
                try { priorityNo = Integer.valueOf(priority); if (priorityNo < 1) throw new NumberFormatException(); }
                catch (NumberFormatException ex) { errors.add(ParseError.of(line, "优先级", priority, "NUMBER", "优先级须为正整数")); }
            }
            if (!enabled.isEmpty() && !Set.of("启用", "停用", "是", "否").contains(enabled)) {
                errors.add(ParseError.of(line, "启用", enabled, "ENUM", "启用只能是启用、停用、是、否"));
            }
            if (!warehouse.isEmpty() && !warehouses.add(warehouse)) {
                errors.add(ParseError.of(line, "仓库编码", warehouse, "DUPLICATE", "文件内仓库重复: " + warehouse));
            }
            if (!factory.isEmpty() && !warehouse.isEmpty()) {
                FactoryWarehouseMapping mapping = new FactoryWarehouseMapping();
                mapping.setFactoryCode(factory);
                mapping.setFactoryName(factory);
                mapping.setWarehouseCode(warehouse);
                mapping.setWarehouseName(warehouseName);
                mapping.setWarehouseManager(manager.isEmpty() ? null : manager);
                mapping.setSupplierName(supplier.isEmpty() ? null : supplier);
                if (Set.of("是", "否").contains(good)) mapping.setGoodWarehouse("是".equals(good));
                mapping.setWarehouseAttribute(attribute);
                mapping.setPriorityNo(priorityNo);
                mapping.setEnabled(enabled.isEmpty() || "启用".equals(enabled) || "是".equals(enabled));
                if ("停用".equals(enabled) || "否".equals(enabled)) mapping.setEnabled(false);
                rows.add(mapping);
            }
        }, headers, errors, List.of("工厂编码", "仓库编码", "仓库名称", "仓库管理员", "对应供应商", "是否良品仓", "仓库属性", "优先级", "启用"));
        return finish(rows, errors, rawRows, headers);
    }

    public ParseResult<MaterialPolicy> parseMaterialPolicy(InputStream input) throws IOException {
        List<MaterialPolicy> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        List<List<String>> rawRows = new ArrayList<>();
        Set<String> materials = new LinkedHashSet<>();
        read(input, 3, rawRows, (row, line) -> {
            String material = text(row.getCell(0));
            if (material.startsWith("示例")) return;
            String supply = SUPPLY.getOrDefault(text(row.getCell(1)), text(row.getCell(1)));
            String explosion = EXPLOSION.getOrDefault(text(row.getCell(2)), text(row.getCell(2)));
            if (material.isEmpty()) errors.add(ParseError.of(line, "物料料号", "", "REQUIRED", "物料料号不能为空"));
            if (!SUPPLY.containsValue(supply)) errors.add(ParseError.of(line, "供应方式", supply, "ENUM", "供应方式只能是自制、外购、委外"));
            if (!EXPLOSION.containsValue(explosion)) errors.add(ParseError.of(line, "下钻策略", explosion, "ENUM", "下钻策略只能是继续下钻、停止下钻"));
            if (!material.isEmpty() && !materials.add(material)) {
                errors.add(ParseError.of(line, "物料料号", material, "DUPLICATE", "文件内物料重复: " + material));
            }
            if (!material.isEmpty() && SUPPLY.containsValue(supply) && EXPLOSION.containsValue(explosion)) {
                MaterialPolicy policy = new MaterialPolicy();
                policy.setMaterialCode(material);
                policy.setSupplyMode(supply);
                policy.setExplosionPolicy(explosion);
                rows.add(policy);
            }
        });
        return finish(rows, errors, rawRows, List.of("物料料号", "供应方式", "下钻策略"));
    }

    public ParseResult<ProductionCalendarEntry> parseProductionCalendar(InputStream input) throws IOException {
        List<ProductionCalendarEntry> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>(); List<List<String>> rawRows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        List<String> expected = List.of("工厂编码", "日期", "生产日", "日产能", "单位", "备注");
        read(input, 6, rawRows, (row, line) -> {
            String factory = text(row.getCell(0)); if (factory.startsWith("示例")) return;
            LocalDate date = date(row.getCell(1), line, "日期", errors);
            String production = text(row.getCell(2)); String unit = text(row.getCell(4)); String reason = text(row.getCell(5));
            BigDecimal capacity = decimal(row.getCell(3), line, "日产能", false, errors);
            if (factory.isBlank() || (factoryMaster != null && !factoryMaster.enabled(factory))) errors.add(ParseError.of(line, "工厂编码", factory, "INVALID_FACTORY", "工厂不存在或已停用"));
            if (!Set.of("是", "否").contains(production)) errors.add(ParseError.of(line, "生产日", production, "ENUM", "生产日必填是或否"));
            if ("是".equals(production) && (capacity == null || capacity.signum() <= 0)) errors.add(ParseError.of(line, "日产能", text(row.getCell(3)), "CAPACITY", "生产日必须配置正日产能"));
            if ("否".equals(production) && capacity != null && capacity.signum() != 0) errors.add(ParseError.of(line, "日产能", capacity.toPlainString(), "CAPACITY", "休息日的日产能必须为0"));
            if ("是".equals(production) && unit.isBlank()) errors.add(ParseError.of(line, "单位", unit, "REQUIRED", "生产日必须配置产能单位"));
            if (unit != null && !unit.isBlank() && !"台".equals(unit)) errors.add(ParseError.of(line, "单位", unit, "UNIT", "整机日产能单位必须为台"));
            if (capacity != null && (capacity.stripTrailingZeros().scale() > 0 || capacity.precision() - capacity.scale() > 12)) errors.add(ParseError.of(line, "日产能", capacity.toPlainString(), "NUMBER", "整机日产能须为最多12位的整数台"));
            if (unit.length() > 32) errors.add(ParseError.of(line, "单位", unit, "LENGTH", "单位不能超过32个字符"));
            if (reason.length() > 256) errors.add(ParseError.of(line, "备注", reason, "LENGTH", "备注不能超过256个字符"));
            if (date != null && !keys.add(factory + "|" + date)) errors.add(ParseError.of(line, "日期", date.toString(), "DUPLICATE", "文件内工厂与日期重复"));
            if (date != null) {
                ProductionCalendarEntry entry = new ProductionCalendarEntry(); entry.setFactoryCode(factory); entry.setCalendarDate(date);
                entry.setProductionDay("是".equals(production)); entry.setAllocationWeight(BigDecimal.ONE);
                entry.setDailyCapacity(capacity); entry.setCapacityUnit(unit.isBlank() && "否".equals(production) ? "台" : unit); entry.setReason(reason); rows.add(entry);
            }
        }, headers, errors, expected);
        return finish(rows, errors, rawRows, headers);
    }

    public ParseResult<BusinessWeek> parseBusinessWeeks(InputStream input) throws IOException {
        List<BusinessWeek> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        List<List<String>> rawRows = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        read(input, 4, rawRows, (row, line) -> {
            String monthText = text(row.getCell(0));
            if (monthText.startsWith("示例")) return;
            LocalDate month = month(row.getCell(0), line, errors);
            String week = text(row.getCell(1));
            LocalDate start = date(row.getCell(2), line, "开始日期", errors);
            LocalDate end = date(row.getCell(3), line, "结束日期", errors);
            if (week.isEmpty()) errors.add(ParseError.of(line, "周编码", "", "REQUIRED", "周编码不能为空"));
            if (month != null && !week.isEmpty() && !keys.add(month + "|" + week)) {
                errors.add(ParseError.of(line, "周编码", week, "DUPLICATE", "文件内同月周编码重复"));
            }
            if (month != null && !week.isEmpty() && start != null && end != null) {
                BusinessWeek item = new BusinessWeek();
                item.setBusinessMonth(month);
                item.setWeekCode(week);
                item.setStartDate(start);
                item.setEndDate(end);
                rows.add(item);
            }
        });
        return finish(rows, errors, rawRows, List.of("业务月份", "周编码", "开始日期", "结束日期"));
    }

    private interface RowHandler { void accept(Row row, int line); }

    private void read(InputStream input, int columns, List<List<String>> rawRows, RowHandler handler) throws IOException {
        read(input, columns, rawRows, handler, null, null, null);
    }

    private void read(InputStream input, int columns, List<List<String>> rawRows, RowHandler handler,
                      List<String> headers, List<ParseError> errors, List<String> expected) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) throw new IOException("文件没有工作表");
            if (headers != null) {
                Row header = sheet.getRow(0);
                for (int col = 0; col < columns; col++) headers.add(header == null ? "" : text(header.getCell(col)));
                for (int col = 0; col < columns; col++) if (!headers.get(col).replace("*", "").equals(expected.get(col))) {
                    errors.add(ParseError.of(1, expected.get(col), headers.get(col), "HEADER_MISMATCH", "表头与模板不匹配，请下载最新模板"));
                }
            }
            int last = Math.min(sheet.getLastRowNum(), 50001);
            if (sheet.getLastRowNum() > 50001) throw new IOException("文件超过 50000 行");
            for (int index = 2; index <= last; index++) {
                Row row = sheet.getRow(index);
                rawRows.add(row == null ? java.util.Collections.nCopies(columns, "") : cells(row, columns));
                if (row == null || blank(row, columns)) continue;
                handler.accept(row, index + 1);
            }
        }
    }

    private List<String> cells(Row row, int columns) {
        List<String> values = new ArrayList<>();
        for (int column = 0; column < columns; column++) values.add(text(row.getCell(column)));
        return values;
    }

    private <T> ParseResult<T> finish(List<T> rows, List<ParseError> errors, List<List<String>> rawRows, List<String> headers) {
        if (errors.isEmpty() && rows.isEmpty()) errors.add(ParseError.of(0, "file", "", "EMPTY", "文件没有可导入的数据"));
        int total = (int) rawRows.stream().filter(row -> row.stream().anyMatch(value -> !value.isBlank()) && !row.getFirst().startsWith("示例")).count();
        int bad = (int) errors.stream().map(ParseError::rowNumber).distinct().count();
        return new ParseResult<>(errors.isEmpty() ? rows : List.of(), errors, List.of(),
                total, errors.isEmpty() ? rows.size() : 0, bad, rawRows, headers);
    }

    private boolean blank(Row row, int columns) {
        for (int column = 0; column < columns; column++) if (!text(row.getCell(column)).isEmpty()) return false;
        return true;
    }

    private String text(Cell cell) { return cell == null ? "" : FORMATTER.formatCellValue(cell).trim(); }

    private LocalDate date(Cell cell, int line, String column, List<ParseError> errors) {
        LocalDate parsed = calendarDate(cell);
        if (parsed != null) return parsed;
        String raw = text(cell);
        if (raw.isEmpty()) { errors.add(ParseError.of(line, column, "", "REQUIRED", column + "不能为空")); return null; }
        errors.add(ParseError.of(line, column, raw, "DATE", column + "不是有效日期"));
        return null;
    }

    private LocalDate month(Cell cell, int line, List<ParseError> errors) {
        LocalDate parsed = calendarDate(cell);
        if (parsed != null) return parsed.withDayOfMonth(1);
        String raw = text(cell);
        if (raw.isEmpty()) { errors.add(ParseError.of(line, "业务月份", "", "REQUIRED", "业务月份不能为空")); return null; }
        if (raw.length() == 7) {
            try { return LocalDate.parse(raw + "-01"); }
            catch (DateTimeParseException exception) { /* 按格式错误统一提示 */ }
        }
        errors.add(ParseError.of(line, "业务月份", raw, "DATE", "业务月份格式应为 yyyy-MM"));
        return null;
    }

    /** 日期格在 Excel 里是数值，DataFormatter 会按单元格格式输出 9月-27、10/13/26，不能当文本解析。 */
    private LocalDate calendarDate(Cell cell) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = text(cell);
        if (raw.length() < 10) return null;
        try { return LocalDate.parse(raw.substring(0, 10)); }
        catch (DateTimeParseException exception) { return null; }
    }

    private BigDecimal decimal(Cell cell, int line, String column, boolean optional, List<ParseError> errors) {
        String raw = text(cell);
        if (raw.isEmpty()) {
            if (!optional) errors.add(ParseError.of(line, column, "", "REQUIRED", column + "不能为空"));
            return optional ? null : null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.replace(",", ""));
            if (value.signum() < 0) { errors.add(ParseError.of(line, column, raw, "NEGATIVE", column + "不能为负")); return null; }
            return value;
        } catch (NumberFormatException exception) {
            errors.add(ParseError.of(line, column, raw, "NUMBER", column + "不是有效数字"));
            return null;
        }
    }
}
