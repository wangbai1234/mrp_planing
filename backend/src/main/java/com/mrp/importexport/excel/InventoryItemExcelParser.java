package com.mrp.importexport.excel;

import com.mrp.inventory.domain.InventoryItem;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class InventoryItemExcelParser {

    private static final Logger log = LoggerFactory.getLogger(InventoryItemExcelParser.class);
    private static final Map<String, String> QUALITY_ALIASES = Map.of(
            "GOOD", "GOOD",
            "DEFECTIVE", "DEFECTIVE",
            "良品", "GOOD",
            "不良品", "DEFECTIVE"
    );

    public ParseResult<InventoryItem> parse(Path file) {
        List<InventoryItem> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        List<List<String>> rawRows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        int totalRows = 0;

        try (OPCPackage pkg = OPCPackage.open(file.toFile())) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) {
                errors.add(ParseError.of(0, "sheet", "", "NO_SHEET", "No sheet found"));
                return new ParseResult<>(rows, errors, List.of(), 0, 0, 1, rawRows, headers);
            }

            InputStream sheetStream = sheets.next();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XMLReader xmlReader = factory.newSAXParser().getXMLReader();

            InventoryItemSheetHandler handler = new InventoryItemSheetHandler(sst, rows, errors, rawRows, headers);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(sheetStream));

            totalRows = handler.getDataRowCount();
            sheetStream.close();
            if (rows.isEmpty() && errors.isEmpty()) {
                errors.add(ParseError.of(1, "file", "", "NO_DATA", "没有可导入的库存数据"));
            }

        } catch (Exception e) {
            log.error("Failed to parse inventory item Excel", e);
            errors.add(ParseError.of(0, "file", "", "PARSE_ERROR", e.getMessage()));
        }

        int errorRows = (int) errors.stream().map(ParseError::rowNumber).distinct().count();
        return new ParseResult<>(rows, errors, List.of(), totalRows, rows.size(), errorRows, rawRows, headers);
    }

    private static class InventoryItemSheetHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final List<InventoryItem> rows;
        private final List<ParseError> errors;
        private final List<List<String>> rawRows;
        private final List<String> headers;

        private int currentRow = -1;
        private int currentCol = 0;
        private String cellType;
        private boolean inValue;
        private StringBuilder valueBuilder = new StringBuilder();
        private boolean inFormula;
        private boolean formulaCell;
        private StringBuilder formulaBuilder = new StringBuilder();
        private List<Object> currentRowData = new ArrayList<>();
        private List<String> currentRowCellTypes = new ArrayList<>();

        private static final int COL_WAREHOUSE_CODE = 0;
        private static final int COL_WAREHOUSE_NAME = 1;
        private static final int COL_INVENTORY_CATEGORY = 2;
        private static final int COL_MATERIAL_ID = 3;
        private static final int COL_INVENTORY_QUALITY = 4;
        private static final int COL_SUPPLIER_NAME = 5;
        private static final int COL_INVENTORY_QTY = 6;

        private static final int COL_REMARK = 7;
        private static final int TOTAL_COLS = 8;
        private static final int HEADER_ROW = 0;
        private static final int TIP_ROW = 1;
        private static final int DATA_START_ROW = 2;
        private boolean legacyQualityColumn;

        InventoryItemSheetHandler(SharedStrings sst, List<InventoryItem> rows,
                                  List<ParseError> errors, List<List<String>> rawRows,
                                  List<String> headers) {
            this.sst = sst;
            this.rows = rows;
            this.errors = errors;
            this.rawRows = rawRows;
            this.headers = headers;
        }

        int getDataRowCount() { return Math.max(0, currentRow - DATA_START_ROW + 1); }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            String ln = localName.isEmpty() ? qName : localName;
            if ("row".equals(ln)) {
                currentRow++;
                currentCol = 0;
                currentRowData.clear();
                currentRowCellTypes.clear();
            } else if ("c".equals(ln)) {
                String cellRef = getAttr(attrs, "r");
                currentCol = cellRef == null ? currentRowData.size() : columnIndex(cellRef);
                while (currentRowData.size() <= currentCol) currentRowData.add(null);
                while (currentRowCellTypes.size() <= currentCol) currentRowCellTypes.add(null);
                cellType = getAttr(attrs, "t");
                inValue = false;
                inFormula = false;
                formulaCell = false;
                valueBuilder.setLength(0);
                formulaBuilder.setLength(0);
            } else if ("f".equals(ln)) {
                inFormula = true;
                formulaCell = true;
            } else if ("v".equals(ln) || "t".equals(ln)) {
                inValue = true;
                valueBuilder.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inFormula) formulaBuilder.append(ch, start, length);
            if (inValue) valueBuilder.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String ln = localName.isEmpty() ? qName : localName;
            if ("f".equals(ln)) {
                inFormula = false;
            } else if ("v".equals(ln) || "t".equals(ln)) {
                inValue = false;
            } else if ("c".equals(ln)) {
                String val = formulaCell ? "=" + formulaBuilder : resolveCellValue(cellType, valueBuilder.toString().trim());
                while (currentRowData.size() <= currentCol) currentRowData.add(null);
                currentRowData.set(currentCol, val);
                currentRowCellTypes.set(currentCol, cellType);
                currentCol++;
            } else if ("row".equals(ln)) {
                processRow();
            }
        }

        private void processRow() {
            // Save header row
            if (currentRow == HEADER_ROW) {
                for (Object cell : currentRowData) {
                    headers.add(cell != null ? cell.toString() : "");
                }
                legacyQualityColumn = headers.size() > 4 && headers.get(4).contains("品质");
                if (!legacyQualityColumn && (headers.size() < 7 || !headers.get(0).contains("仓库编码")
                        || !headers.get(3).contains("料号") || !headers.get(4).contains("供应商")
                        || !headers.get(6).contains("备注"))) {
                    errors.add(ParseError.of(1, "header", "", "HEADER_MISMATCH", "表头不匹配，请下载最新模板"));
                }
                // Preserve actual input headers for the error report.
                while (headers.size() < TOTAL_COLS) {
                    if (legacyQualityColumn && headers.size() == COL_REMARK) headers.add("备注");
                    else break;
                }
                return;
            }

            // Skip tip row
            if (currentRow == TIP_ROW) return;

            // Save raw data for error report
            List<String> rawRow = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                if (i < currentRowData.size()) {
                    Object cell = currentRowData.get(i);
                    rawRow.add(cell != null ? cell.toString() : "");
                } else {
                    rawRow.add("");
                }
            }
            rawRows.add(rawRow);

            // Ignore only completely empty rows; partial rows must report missing required fields.
            if (currentRowData.stream().allMatch(cell -> cell == null || cell.toString().isBlank())) return;
            String materialId = getString(COL_MATERIAL_ID);

            int rowNum = currentRow + 1; // Excel row number (1-indexed)

            // Validate required fields
            String inventoryCategory = getString(COL_INVENTORY_CATEGORY);
            String supplierName = getString(legacyQualityColumn ? COL_SUPPLIER_NAME : 4);
            String inventoryQuality = legacyQualityColumn ? getString(COL_INVENTORY_QUALITY) : null;
            String warehouseCode = getString(COL_WAREHOUSE_CODE);
            String warehouseName = getString(COL_WAREHOUSE_NAME);
            String remark = getString(legacyQualityColumn ? COL_REMARK : 6);

            if (warehouseCode == null || warehouseCode.isBlank()) {
                errors.add(ParseError.of(rowNum, "warehouse_code", "", "REQUIRED_FIELD", "仓库编码不能为空"));
            } else if (warehouseCode.startsWith("=")) {
                errors.add(ParseError.of(rowNum, "warehouse_code", warehouseCode, "INVALID_WAREHOUSE_CODE", "仓库编码不能使用公式"));
            }
            if (materialId == null || materialId.isBlank()) {
                errors.add(ParseError.of(rowNum, "material_id", "", "REQUIRED_FIELD", "物料料号不能为空"));
            } else if (materialId.startsWith("=")) {
                errors.add(ParseError.of(rowNum, "material_id", materialId, "INVALID_MATERIAL_ID", "物料料号不能使用公式"));
            } else {
                String materialCellType = currentRowCellTypes.size() > COL_MATERIAL_ID
                        ? currentRowCellTypes.get(COL_MATERIAL_ID) : null;
                if (!"s".equals(materialCellType) && !"inlineStr".equals(materialCellType) && !"str".equals(materialCellType)) {
                    errors.add(ParseError.of(rowNum, "material_id", materialId, "INVALID_MATERIAL_ID",
                            "料号必须设置为文本格式，数值或日期单元格可能丢失前导零或精度"));
                } else if (materialId.matches("\\d{4}-\\d{2}-\\d{2}.*")
                        || materialId.matches("(?i)[+-]?\\d+(?:\\.\\d+)?E[+-]?\\d+")) {
                    errors.add(ParseError.of(rowNum, "material_id", materialId, "INVALID_MATERIAL_ID",
                            "料号被Excel转为日期或科学计数法"));
                }
            }
            if (remark != null && remark.codePointCount(0, remark.length()) > 512) {
                errors.add(ParseError.of(rowNum, "remark", remark, "TOO_LONG", "备注不能超过512个字符"));
            }
            String normalizedQuality = normalizeQuality(inventoryQuality);
            if (legacyQualityColumn && (inventoryQuality == null || inventoryQuality.isBlank())) {
                errors.add(ParseError.of(rowNum, "inventory_quality", "", "REQUIRED_FIELD", "品质不能为空，请填写良品或不良品"));
            } else if (legacyQualityColumn && normalizedQuality == null) {
                errors.add(ParseError.of(rowNum, "inventory_quality", inventoryQuality, "INVALID_QUALITY", "品质必须是良品或不良品"));
            }

            int quantityColumn = legacyQualityColumn ? COL_INVENTORY_QTY : 5;
            BigDecimal inventoryQty = parseQty(quantityColumn, rowNum);
            if (inventoryQty == null && (getString(quantityColumn) == null
                    || getString(quantityColumn).isBlank())) {
                errors.add(ParseError.of(rowNum, "inventory_qty", "", "REQUIRED_FIELD", "库存数量不能为空"));
            }

            // If any error occurred for this row, don't add to rows
            if (errors.stream().anyMatch(e -> e.rowNumber() == rowNum)) return;

            rows.add(new InventoryItem(
                    null, null,
                    null,
                    inventoryCategory,
                    materialId,
                    normalizedQuality,
                    warehouseCode,
                    warehouseName,
                    null, // projectModel - will be filled by service
                    null, // materialName - will be filled by service
                    supplierName,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    inventoryQty,
                    null,
                    null,
                    remark,
                    BigDecimal.ZERO,
                    null,
                    null,
                    null
            ));
        }

        private String getString(int col) {
            if (col < 0 || col >= currentRowData.size()) return null;
            Object val = currentRowData.get(col);
            return val != null ? val.toString().trim() : null;
        }

        private String normalizeQuality(String quality) {
            if (quality == null || quality.isBlank()) return null;
            return QUALITY_ALIASES.get(quality.trim().toUpperCase(Locale.ROOT));
        }

        private BigDecimal parseQty(int col, int rowNum) {
            String str = getString(col);
            if (str == null || str.isBlank()) return null;
            if (str.startsWith("=")) {
                errors.add(ParseError.of(rowNum, getColName(col), str, "INVALID_QTY", "数量不能使用公式，请填写数值"));
                return null;
            }
            try {
                BigDecimal qty = new BigDecimal(str.replaceAll("\\.0+$", ""));
                if (qty.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(ParseError.of(rowNum, getColName(col), str, "NEGATIVE_QTY", "数量不能为负"));
                    return null;
                }
                if (qty.stripTrailingZeros().scale() > 6 || qty.precision() - qty.scale() > 12) {
                    errors.add(ParseError.of(rowNum, getColName(col), str, "INVALID_QTY",
                            "数量最多6位小数、12位整数"));
                    return null;
                }
                return qty;
            } catch (NumberFormatException e) {
                errors.add(ParseError.of(rowNum, getColName(col), str, "INVALID_QTY", "数量格式错误"));
                return null;
            }
        }

        private String getColName(int col) {
            return col == COL_INVENTORY_QTY || col == 5 ? "inventory_qty" : "unknown";
        }

        private String resolveCellValue(String type, String value) {
            if (value == null || value.isEmpty()) return null;
            if ("s".equals(type)) {
                try { return sst.getItemAt(Integer.parseInt(value)).getString(); }
                catch (Exception e) { return value; }
            }
            return value;
        }

        private String getAttr(Attributes attrs, String name) {
            for (int i = 0; i < attrs.getLength(); i++) {
                if (name.equals(attrs.getLocalName(i))) return attrs.getValue(i);
            }
            return null;
        }

        private int columnIndex(String cellRef) {
            int result = 0;
            int i = 0;
            while (i < cellRef.length() && Character.isLetter(cellRef.charAt(i))) {
                result = result * 26 + (Character.toUpperCase(cellRef.charAt(i)) - 'A' + 1);
                i++;
            }
            return Math.max(0, result - 1);
        }
    }
}
