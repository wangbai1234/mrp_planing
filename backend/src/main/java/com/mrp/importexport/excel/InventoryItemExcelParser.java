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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class InventoryItemExcelParser {

    private static final Logger log = LoggerFactory.getLogger(InventoryItemExcelParser.class);

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

        } catch (Exception e) {
            log.error("Failed to parse inventory item Excel", e);
            errors.add(ParseError.of(0, "file", "", "PARSE_ERROR", e.getMessage()));
        }

        return new ParseResult<>(rows, errors, List.of(), totalRows, rows.size(), errors.size(), rawRows, headers);
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
        private List<Object> currentRowData = new ArrayList<>();

        // Column mapping (from template, 0-indexed)
        private static final int COL_PRODUCT_LINE = 0;
        private static final int COL_INVENTORY_CATEGORY = 1;
        private static final int COL_MATERIAL_ID = 2;
        private static final int COL_SUPPLIER_NAME = 3;
        private static final int COL_PRODUCT_MODE = 4;
        private static final int COL_ODM_SUPPLIER_QTY = 5;
        private static final int COL_XA400_QTY = 6;
        private static final int COL_XA378_QTY = 7;
        private static final int COL_XA226_QTY = 8;
        private static final int COL_CLASSIFICATION = 9;
        private static final int COL_BARCODE = 10;
        private static final int COL_REMARK = 11;
        private static final int COL_ORDER_PENDING_QTY = 12;
        private static final int COL_SALES_STATUS = 13;
        private static final int COL_PARENT_RECORD = 14;

        private static final int TOTAL_COLS = 15;
        private static final int HEADER_ROW = 0;
        private static final int TIP_ROW = 1;
        private static final int DATA_START_ROW = 2;

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
            } else if ("c".equals(ln)) {
                cellType = getAttr(attrs, "t");
                inValue = false;
                valueBuilder.setLength(0);
            } else if ("v".equals(ln) || "t".equals(ln)) {
                inValue = true;
                valueBuilder.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue) valueBuilder.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String ln = localName.isEmpty() ? qName : localName;
            if ("v".equals(ln) || "t".equals(ln)) {
                inValue = false;
            } else if ("c".equals(ln)) {
                String val = resolveCellValue(cellType, valueBuilder.toString().trim());
                currentRowData.add(val);
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
                return;
            }

            // Skip tip row
            if (currentRow == TIP_ROW) return;

            // Save raw data for error report
            List<String> rawRow = new ArrayList<>();
            for (int i = 0; i < TOTAL_COLS; i++) {
                if (i < currentRowData.size()) {
                    Object cell = currentRowData.get(i);
                    rawRow.add(cell != null ? cell.toString() : "");
                } else {
                    rawRow.add("");
                }
            }
            rawRows.add(rawRow);

            // Skip empty rows
            if (currentRowData.size() < 4) return;
            String materialId = getString(COL_MATERIAL_ID);
            if (materialId == null || materialId.isBlank()) return;

            int rowNum = currentRow + 1; // Excel row number (1-indexed)

            // Validate required fields
            String inventoryCategory = getString(COL_INVENTORY_CATEGORY);
            String supplierName = getString(COL_SUPPLIER_NAME);

            if (inventoryCategory == null || inventoryCategory.isBlank()) {
                errors.add(ParseError.of(rowNum, "inventory_category", "", "REQUIRED_FIELD", "库存分类不能为空"));
            }
            if (supplierName == null || supplierName.isBlank()) {
                errors.add(ParseError.of(rowNum, "supplier_name", "", "REQUIRED_FIELD", "供应商名称不能为空"));
            }

            // Parse quantities
            Long odmQty = parseQty(COL_ODM_SUPPLIER_QTY, rowNum);
            Long xa400Qty = parseQty(COL_XA400_QTY, rowNum);
            Long xa378Qty = parseQty(COL_XA378_QTY, rowNum);
            Long xa226Qty = parseQty(COL_XA226_QTY, rowNum);
            Long orderPendingQty = parseQty(COL_ORDER_PENDING_QTY, rowNum);

            // If any error occurred for this row, don't add to rows
            if (errors.stream().anyMatch(e -> e.rowNumber() == rowNum)) return;

            // Calculate shipping available qty
            long shippingAvailable = (odmQty != null ? odmQty : 0)
                    + (xa400Qty != null ? xa400Qty : 0)
                    + (xa378Qty != null ? xa378Qty : 0)
                    + (xa226Qty != null ? xa226Qty : 0);

            rows.add(new InventoryItem(
                    null, null,
                    getString(COL_PRODUCT_LINE),
                    inventoryCategory,
                    materialId,
                    null, // projectModel - will be filled by service
                    null, // materialName - will be filled by service
                    supplierName,
                    getString(COL_PRODUCT_MODE),
                    odmQty != null ? odmQty : 0,
                    xa400Qty != null ? xa400Qty : 0,
                    xa378Qty != null ? xa378Qty : 0,
                    xa226Qty != null ? xa226Qty : 0,
                    shippingAvailable,
                    getString(COL_CLASSIFICATION),
                    getString(COL_BARCODE),
                    getString(COL_REMARK),
                    orderPendingQty != null ? orderPendingQty : 0,
                    getString(COL_SALES_STATUS),
                    getString(COL_PARENT_RECORD),
                    null
            ));
        }

        private String getString(int col) {
            if (col < 0 || col >= currentRowData.size()) return null;
            Object val = currentRowData.get(col);
            return val != null ? val.toString().trim() : null;
        }

        private Long parseQty(int col, int rowNum) {
            String str = getString(col);
            if (str == null || str.isBlank()) return null;
            try {
                double d = Double.parseDouble(str);
                long qty = (long) d;
                if (qty < 0) {
                    errors.add(ParseError.of(rowNum, getColName(col), str, "NEGATIVE_QTY", "数量不能为负"));
                    return null;
                }
                return qty;
            } catch (NumberFormatException e) {
                errors.add(ParseError.of(rowNum, getColName(col), str, "INVALID_QTY", "数量格式错误"));
                return null;
            }
        }

        private String getColName(int col) {
            return switch (col) {
                case COL_ODM_SUPPLIER_QTY -> "odm_supplier_qty";
                case COL_XA400_QTY -> "xa400_qty";
                case COL_XA378_QTY -> "xa378_qty";
                case COL_XA226_QTY -> "xa226_qty";
                case COL_ORDER_PENDING_QTY -> "order_pending_qty";
                default -> "unknown";
            };
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
    }
}
