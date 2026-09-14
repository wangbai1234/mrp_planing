package com.mrp.importexport.excel;

import com.mrp.inventory.domain.InventoryDetail;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class InventoryExcelParser {

    private static final Logger log = LoggerFactory.getLogger(InventoryExcelParser.class);

    public ParseResult<InventoryDetail> parse(Path file, Long snapshotId) {
        List<InventoryDetail> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        int totalRows = 0;

        try (OPCPackage pkg = OPCPackage.open(file.toFile())) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) {
                errors.add(ParseError.of(0, "sheet", "", "NO_SHEET", "No sheet found"));
                return new ParseResult<>(rows, errors, List.of(), 0, 0, 1, List.of(), List.of());
            }

            InputStream sheetStream = sheets.next();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XMLReader xmlReader = factory.newSAXParser().getXMLReader();

            InventorySheetHandler handler = new InventorySheetHandler(sst, snapshotId, rows, errors);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(sheetStream));

            totalRows = handler.getDataRowCount();
            sheetStream.close();

        } catch (Exception e) {
            log.error("Failed to parse inventory Excel", e);
            errors.add(ParseError.of(0, "file", "", "PARSE_ERROR", e.getMessage()));
        }

        return new ParseResult<>(rows, errors, List.of(), totalRows, rows.size(), errors.size(), List.of(), List.of());
    }

    private static class InventorySheetHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final Long snapshotId;
        private final List<InventoryDetail> rows;
        private final List<ParseError> errors;

        private int currentRow = -1;
        private int currentCol = 0;
        private String cellType;
        private boolean inValue;
        private StringBuilder valueBuilder = new StringBuilder();
        private List<Object> currentRowData = new ArrayList<>();

        // Column mapping (from template)
        private static final int COL_MATERIAL_ID = 0;
        private static final int COL_WAREHOUSE_CODE = 1;
        private static final int COL_WAREHOUSE_NAME = 2;
        private static final int COL_WAREHOUSE_TYPE = 3;
        private static final int COL_FACTORY_CODE = 4;
        private static final int COL_QUANTITY = 5;

        InventorySheetHandler(SharedStrings sst, Long snapshotId,
                            List<InventoryDetail> rows, List<ParseError> errors) {
            this.sst = sst;
            this.snapshotId = snapshotId;
            this.rows = rows;
            this.errors = errors;
        }

        int getDataRowCount() { return currentRow; }

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
            if (currentRow == 0) return; // skip header
            if (currentRowData.size() < 6) return;

            String materialId = getString(COL_MATERIAL_ID);
            String warehouseCode = getString(COL_WAREHOUSE_CODE);
            String warehouseName = getString(COL_WAREHOUSE_NAME);
            String warehouseType = getString(COL_WAREHOUSE_TYPE);
            String factoryCode = getString(COL_FACTORY_CODE);
            String qtyStr = getString(COL_QUANTITY);

            if (materialId == null || materialId.isBlank()) return;
            if (factoryCode == null || factoryCode.isBlank()) {
                errors.add(ParseError.of(currentRow + 1, "factory_code", "", "REQUIRED", "工厂不能为空"));
                return;
            }

            boolean isInCalc = !"不良品".equals(warehouseType) && !"不良品仓".equals(warehouseName);

            long qty = 0;
            if (qtyStr != null && !qtyStr.isBlank()) {
                try {
                    qty = Long.parseLong(qtyStr.replaceAll("\\.0+$", ""));
                    if (qty < 0) {
                        errors.add(ParseError.of(currentRow + 1, "quantity", qtyStr, "NEGATIVE_QTY", "数量不能为负"));
                        return;
                    }
                } catch (NumberFormatException e) {
                    errors.add(ParseError.of(currentRow + 1, "quantity", qtyStr, "INVALID_QTY", "数量格式错误"));
                    return;
                }
            }

            rows.add(new InventoryDetail(
                    null, snapshotId, factoryCode, materialId, warehouseCode,
                    warehouseName, warehouseType, qty, isInCalc
            ));
        }

        private String getString(int col) {
            if (col < 0 || col >= currentRowData.size()) return null;
            Object val = currentRowData.get(col);
            return val != null ? val.toString().trim() : null;
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
