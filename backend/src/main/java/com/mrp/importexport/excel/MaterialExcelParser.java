package com.mrp.importexport.excel;

import com.mrp.masterdata.domain.Material;
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
public class MaterialExcelParser {

    private static final Logger log = LoggerFactory.getLogger(MaterialExcelParser.class);

    public static final int MAX_ROWS = 50000;
    public static final long MAX_FILE_SIZE = 30 * 1024 * 1024; // 30MB

    private static final int HEADER_ROW = 0; // 表头行（第1行：展示名称）
    private static final int TIP_ROW = 1;    // 提示行（第2行：填写说明）
    private static final int DATA_START_ROW = 2; // 数据起始行（第3行起：示例数据/实际数据）

    // Expected header names (Chinese)
    private static final List<String> EXPECTED_HEADERS = List.of(
            "展示名称", "料号*", "物料名称*", "项目型号", "规格型号",
            "单位", "MOQ最小起订量", "MPQ最小包装", "所属区域", "属性",
            "物料类别", "是否有效*", "L-T提前期", "产地"
    );

    public ParseResult<Material> parse(Path file) {
        List<Material> rows = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        List<String> recognizedMonths = new ArrayList<>();
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
                return new ParseResult<>(rows, errors, recognizedMonths, 0, 0, 1, rawRows, headers);
            }

            InputStream sheetStream = sheets.next();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XMLReader xmlReader = factory.newSAXParser().getXMLReader();

            MaterialSheetHandler handler = new MaterialSheetHandler(sst, styles, rows, errors, rawRows, headers);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(sheetStream));

            totalRows = handler.getDataRowCount();
            sheetStream.close();

            // Validate header
            if (!handler.isHeaderValid()) {
                errors.add(ParseError.of(1, "header", "", "HEADER_MISMATCH",
                        "表头与模板不匹配，请下载最新模板"));
                return new ParseResult<>(rows, errors, recognizedMonths, totalRows, 0, 1, rawRows, headers);
            }

            // Check row limit
            if (totalRows > MAX_ROWS) {
                errors.add(ParseError.of(0, "rows", String.valueOf(totalRows),
                        "ROW_LIMIT_EXCEEDED", "记录数超过" + MAX_ROWS + "条限制"));
                return new ParseResult<>(rows, errors, recognizedMonths, totalRows, 0, 1, rawRows, headers);
            }

        } catch (Exception e) {
            log.error("Failed to parse material Excel", e);
            errors.add(ParseError.of(0, "file", "", "PARSE_ERROR", e.getMessage()));
        }

        int errorRows = (int) errors.stream().map(ParseError::rowNumber).distinct().count();
        int successRows = rows.size();
        return new ParseResult<>(rows, errors, recognizedMonths, totalRows, successRows, errorRows, rawRows, headers);
    }

    private static class MaterialSheetHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final StylesTable styles;
        private final List<Material> rows;
        private final List<ParseError> errors;
        private final List<List<String>> rawRows;
        private final List<String> headers;

        private int currentRow = -1;
        private int currentCol = 0;
        private String cellType;
        private String cellValue;
        private boolean inValue;
        private StringBuilder valueBuilder = new StringBuilder();

        private List<Object> currentRowData = new ArrayList<>();
        private List<String> headerNames = new ArrayList<>();
        private boolean headerValid = false;
        private int dataRowCount = 0;

        MaterialSheetHandler(SharedStrings sst, StylesTable styles,
                            List<Material> rows, List<ParseError> errors,
                            List<List<String>> rawRows, List<String> headers) {
            this.sst = sst;
            this.styles = styles;
            this.rows = rows;
            this.errors = errors;
            this.rawRows = rawRows;
            this.headers = headers;
        }

        int getDataRowCount() { return dataRowCount; }
        boolean isHeaderValid() { return headerValid; }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            if ("row".equals(localName) || "row".equals(qName)) {
                currentRow++;
                currentCol = 0;
                currentRowData.clear();
                String rowNum = getAttr(attrs, "r");
                if (rowNum != null) {
                    currentRow = Integer.parseInt(rowNum) - 1;
                }
            } else if ("c".equals(localName) || "c".equals(qName)) {
                cellType = getAttr(attrs, "t");
                cellValue = null;
                inValue = false;
                valueBuilder.setLength(0);
                String ref = getAttr(attrs, "r");
                if (ref != null) {
                    int col = colFromRef(ref);
                    while (currentCol < col) {
                        currentRowData.add(null);
                        currentCol++;
                    }
                }
            } else if ("v".equals(localName) || "v".equals(qName) || "t".equals(localName) || "t".equals(qName)) {
                inValue = true;
                valueBuilder.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue) {
                valueBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("v".equals(localName) || "v".equals(qName) || "t".equals(localName) || "t".equals(qName)) {
                inValue = false;
                cellValue = valueBuilder.toString().trim();
            } else if ("c".equals(localName) || "c".equals(qName)) {
                String resolved = resolveCellValue(cellType, cellValue);
                currentRowData.add(resolved);
                currentCol++;
            } else if ("row".equals(localName) || "row".equals(qName)) {
                processRow();
            }
        }

        private void processRow() {
            // Save raw row data for error reports
            List<String> rawRow = new ArrayList<>();
            for (Object cell : currentRowData) {
                rawRow.add(cell != null ? cell.toString() : "");
            }

            // Row 0: Header - validate
            if (currentRow == HEADER_ROW) {
                validateHeader();
                return;
            }

            // Row 1 & 2: Field identifiers and tips - skip
            if (currentRow < DATA_START_ROW) return;
            if (currentRowData.isEmpty()) return;

            dataRowCount++;

            // Check row limit during parsing
            if (dataRowCount > MAX_ROWS) {
                rawRows.add(rawRow);
                return; // Will be checked after parse
            }

            // Skip sample rows (first column is "示例")
            String displayName = getString(0);
            if ("示例".equals(displayName)) {
                dataRowCount--;
                return;
            }

            rawRows.add(rawRow);

            String materialCode = getString(1); // 料号
            if (materialCode == null || materialCode.isBlank()) return;

            String materialName = getString(2); // 物料名称
            if (materialName == null || materialName.isBlank()) {
                errors.add(ParseError.of(currentRow + 1, "物料名称", "", "REQUIRED", "物料名称必填"));
                return;
            }

            String isActiveStr = getString(11); // 是否有效
            Boolean isActive = "是".equals(isActiveStr) || "1".equals(isActiveStr) || "true".equalsIgnoreCase(isActiveStr);

            Long moq = parseLong(getString(6));   // MOQ
            Long mpq = parseLong(getString(7));   // MPQ
            Integer leadTime = parseInteger(getString(12)); // L-T提前期

            rows.add(new Material(
                    null,
                    materialCode,
                    materialName,
                    getString(3),  // 项目型号
                    getString(4),  // 规格型号
                    getString(5),  // 单位
                    moq,
                    mpq,
                    getString(8),  // 所属区域
                    getString(9),  // 属性
                    getString(10), // 物料类别
                    isActive,
                    leadTime,
                    getString(13), // 产地
                    Material.SOURCE_EXCEL_IMPORT,
                    null,
                    false,
                    null,
                    null,
                    null  // materialCategoryId - to be set after category lookup
            ));
        }

        private void validateHeader() {
            headerNames.clear();
            headers.clear();
            for (int i = 0; i < currentRowData.size(); i++) {
                String val = getString(i);
                if (val != null) {
                    headerNames.add(val);
                    headers.add(val);
                } else {
                    headers.add("");
                }
            }

            // Check if header matches expected headers
            if (headerNames.size() < EXPECTED_HEADERS.size()) {
                headerValid = false;
                return;
            }

            for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
                if (!EXPECTED_HEADERS.get(i).equals(headerNames.get(i))) {
                    headerValid = false;
                    log.warn("Header mismatch at column {}: expected='{}', actual='{}'", 
                            i, EXPECTED_HEADERS.get(i), headerNames.get(i));
                    return;
                }
            }
            headerValid = true;
        }

        private String getString(int col) {
            if (col < 0 || col >= currentRowData.size()) return null;
            Object val = currentRowData.get(col);
            return val != null ? val.toString().trim() : null;
        }

        private Long parseLong(String val) {
            if (val == null || val.isBlank()) return null;
            try {
                return Long.parseLong(val.replaceAll("\\.0+$", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Integer parseInteger(String val) {
            if (val == null || val.isBlank()) return null;
            try {
                return Integer.parseInt(val.replaceAll("\\.0+$", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private String resolveCellValue(String type, String value) {
            if (value == null || value.isEmpty()) return null;
            if ("s".equals(type)) {
                try {
                    int idx = Integer.parseInt(value);
                    return sst.getItemAt(idx).getString();
                } catch (Exception e) {
                    return value;
                }
            }
            if ("str".equals(type)) return value;
            if ("b".equals(type)) return "1".equals(value) ? "true" : "false";
            return value;
        }

        private int colFromRef(String ref) {
            String letters = ref.replaceAll("\\d+", "");
            int col = 0;
            for (char c : letters.toCharArray()) {
                col = col * 26 + (c - 'A' + 1);
            }
            return col - 1;
        }

        private String getAttr(Attributes attrs, String name) {
            for (int i = 0; i < attrs.getLength(); i++) {
                if (name.equals(attrs.getLocalName(i)) || name.equals(attrs.getQName(i))) {
                    return attrs.getValue(i);
                }
            }
            return null;
        }
    }
}
