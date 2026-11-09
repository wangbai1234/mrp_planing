package com.mrp.importexport.excel;

import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.StockPolicy;
import com.mrp.mrp.repository.ConfigMasterMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 库存策略导入。第 1 行表头，第 2 行填写说明（跳过），第 3 行起为数据；
 * 以「示例」开头的行是模板示例，不导入。列：仓库编码、物料料号、安全库存、预警库存。
 */
@Component
public class StockPolicyExcelParser {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private final ConfigMasterMapper mappings;
    private final MaterialMapper materials;

    public StockPolicyExcelParser(ConfigMasterMapper mappings, MaterialMapper materials) {
        this.mappings = mappings;
        this.materials = materials;
    }

    public ParseResult<StockPolicy> parse(InputStream input) throws IOException {
        List<StockPolicy> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        List<List<String>> rawRows = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();

        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                errors.add(ParseError.of(0, "sheet", "", "NO_SHEET", "文件没有工作表"));
                return new ParseResult<>(List.of(), errors, List.of(), 0, 0, errors.size(), List.of(), List.of());
            }
            if (sheet.getLastRowNum() > 50001) {
                errors.add(ParseError.of(0, "file", "", "TOO_LARGE", "文件超过 50000 行"));
                return new ParseResult<>(List.of(), errors, List.of(), 0, 0, 1, List.of(), List.of("仓库编码", "物料料号", "安全库存", "预警库存"));
            }
            int lastRow = sheet.getLastRowNum();
            Set<String> warehouses = enabledWarehouses();
            List<String> materialCodes = new ArrayList<>();
            for (int index = 2; index <= lastRow; index++) {
                Row row = sheet.getRow(index);
                if (row == null || blank(row) || text(row.getCell(0)).startsWith("示例")) continue;
                String material = text(row.getCell(1));
                if (!material.isEmpty()) materialCodes.add(material);
            }
            Set<String> knownMaterials = knownMaterials(materialCodes);
            for (int index = 2; index <= lastRow; index++) {
                Row row = sheet.getRow(index);
                if (row == null || blank(row)) continue;
                rawRows.add(List.of(text(row.getCell(0)), text(row.getCell(1)), text(row.getCell(2)), text(row.getCell(3))));
                int line = index + 1;
                String warehouse = text(row.getCell(0));
                if (warehouse.startsWith("示例")) continue;
                String material = text(row.getCell(1));
                BigDecimal safety = number(row.getCell(2), line, "安全库存", errors);
                BigDecimal warning = number(row.getCell(3), line, "预警库存", errors);
                if (warehouse.isEmpty()) errors.add(ParseError.of(line, "仓库编码", "", "REQUIRED", "仓库编码不能为空"));
                else if (!warehouses.contains(warehouse)) {
                    errors.add(ParseError.of(line, "仓库编码", warehouse, "UNKNOWN", "仓库不在工厂仓库映射中或未启用"));
                }
                if (material.isEmpty()) errors.add(ParseError.of(line, "物料料号", "", "REQUIRED", "物料料号不能为空"));
                else if (!knownMaterials.contains(material)) {
                    errors.add(ParseError.of(line, "物料料号", material, "UNKNOWN", "物料料号不存在"));
                }
                if (safety != null && warning != null && safety.compareTo(warning) > 0) {
                    errors.add(ParseError.of(line, "安全库存", safety.toPlainString(), "RANGE",
                            "安全库存不能大于预警库存"));
                }
                if (!warehouse.isEmpty() && !material.isEmpty() && !keys.add(warehouse + "|" + material)) {
                    errors.add(ParseError.of(line, "物料料号", material, "DUPLICATE",
                            "文件内仓库与物料重复: " + warehouse + "/" + material));
                }
                if (!warehouse.isEmpty() && warehouses.contains(warehouse)
                        && !material.isEmpty() && knownMaterials.contains(material)
                        && safety != null && warning != null && safety.compareTo(warning) <= 0) {
                    StockPolicy detail = new StockPolicy();
                    detail.setWarehouseCode(warehouse);
                    detail.setMaterialCode(material);
                    detail.setSafetyStockQty(safety);
                    detail.setWarningStockQty(warning);
                    rows.add(detail);
                }
            }
        }
        if (errors.isEmpty() && rows.isEmpty()) {
            errors.add(ParseError.of(0, "file", "", "EMPTY", "文件没有可导入的数据"));
        }
        return new ParseResult<>(errors.isEmpty() ? rows : List.of(), errors, List.of(),
                rows.size() + errors.size(), errors.isEmpty() ? rows.size() : 0, errors.size(), rawRows,
                List.of("仓库编码", "物料料号", "安全库存", "预警库存"));
    }

    private Set<String> enabledWarehouses() {
        Set<String> warehouses = new HashSet<>();
        for (FactoryWarehouseMapping mapping : mappings.selectFactoryWarehouseMappings()) {
            if (Boolean.TRUE.equals(mapping.getEnabled()) && mapping.getWarehouseCode() != null) {
                warehouses.add(mapping.getWarehouseCode());
            }
        }
        return warehouses;
    }

    private Set<String> knownMaterials(List<String> codes) {
        List<String> distinct = codes.stream().distinct().toList();
        if (distinct.isEmpty()) return Set.of();
        Set<String> known = new HashSet<>();
        for (Material material : materials.selectByCodes(distinct)) {
            if (Boolean.TRUE.equals(material.isActive()) && Boolean.FALSE.equals(material.isDeleted())) {
                known.add(material.materialCode());
            }
        }
        return known;
    }

    private boolean blank(Row row) {
        for (int column = 0; column < 4; column++) {
            if (!text(row.getCell(column)).isEmpty()) return false;
        }
        return true;
    }

    private String text(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    private BigDecimal number(Cell cell, int line, String column, List<ParseError> errors) {
        String raw = text(cell);
        if (raw.isEmpty()) {
            errors.add(ParseError.of(line, column, "", "REQUIRED", column + "不能为空"));
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.replace(",", ""));
            if (value.signum() < 0) {
                errors.add(ParseError.of(line, column, raw, "NEGATIVE", column + "不能为负数"));
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            errors.add(ParseError.of(line, column, raw, "NUMBER", column + "不是有效数字"));
            return null;
        }
    }
}
