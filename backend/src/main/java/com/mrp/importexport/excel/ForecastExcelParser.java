package com.mrp.importexport.excel;

import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ForecastExcelParser {

    private static final Logger log = LoggerFactory.getLogger(ForecastExcelParser.class);

    private final MaterialMapper materialMapper;

    public ForecastExcelParser(MaterialMapper materialMapper) {
        this.materialMapper = materialMapper;
    }

    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 2; // Row 0=header, Row 1=tips, Row 2+=data
    private static final int COL_FACTORY = 0;
    private static final int COL_FORM_TYPE = 1;
    private static final int COL_MATERIAL_ID = 2;
    private static final int COL_MOLD = 3;
    private static final int COL_STATUS = 4;
    private static final int COL_FCST_START = 5;
    private static final int FCST_MONTHS = 6;

    public ParseResult<ForecastDetail> parse(Path file, Long versionId) {
        List<ForecastDetail> rows = new ArrayList<>();
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

            ForecastSheetHandler handler = new ForecastSheetHandler(sst, styles, versionId, rows, errors, recognizedMonths, materialMapper, rawRows, headers);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(sheetStream));

            totalRows = handler.getDataRowCount();
            sheetStream.close();

        } catch (Exception e) {
            log.error("Failed to parse forecast Excel", e);
            errors.add(ParseError.of(0, "file", "", "PARSE_ERROR", e.getMessage()));
        }

        int errorRows = errors.size();
        int successRows = rows.size();
        return new ParseResult<>(rows, errors, recognizedMonths, totalRows, successRows, errorRows, rawRows, headers);
    }

    private static class ForecastSheetHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final StylesTable styles;
        private final Long versionId;
        private final List<ForecastDetail> rows;
        private final List<ParseError> errors;
        private final List<String> recognizedMonths;
        private final MaterialMapper materialMapper;
        private final List<List<String>> rawRows;
        private final List<String> headers;

        private int currentRow = -1;
        private int currentCol = 0;
        private String cellType;
        private String cellValue;
        private boolean inValue;
        private StringBuilder valueBuilder = new StringBuilder();

        private List<String> headerMonths = new ArrayList<>();
        private List<Object> currentRowData = new ArrayList<>();

        ForecastSheetHandler(SharedStrings sst, StylesTable styles, Long versionId,
                            List<ForecastDetail> rows, List<ParseError> errors, List<String> recognizedMonths,
                            MaterialMapper materialMapper, List<List<String>> rawRows, List<String> headers) {
            this.sst = sst;
            this.styles = styles;
            this.versionId = versionId;
            this.rows = rows;
            this.errors = errors;
            this.recognizedMonths = recognizedMonths;
            this.materialMapper = materialMapper;
            this.rawRows = rawRows;
            this.headers = headers;
        }

        int getDataRowCount() { return currentRow; }

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
                // Handle missing cells (sparse)
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
            if (currentRow == HEADER_ROW) {
                parseHeader();
                // Save headers
                headers.clear();
                for (Object cell : currentRowData) {
                    headers.add(cell != null ? cell.toString() : "");
                }
                return;
            }
            if (currentRow < DATA_START_ROW) return;
            if (currentRowData.isEmpty()) return;

            // Save raw row data for error report (pad to expected column count)
            List<String> rawRow = new ArrayList<>();
            int totalCols = COL_FCST_START + FCST_MONTHS + 1; // 5 + 6 + 1(fcst-total) = 12
            for (int i = 0; i < totalCols; i++) {
                if (i < currentRowData.size()) {
                    Object cell = currentRowData.get(i);
                    rawRow.add(cell != null ? cell.toString() : "");
                } else {
                    rawRow.add("");
                }
            }
            rawRows.add(rawRow);

            String materialId = getString(COL_MATERIAL_ID);
            if (materialId == null || materialId.isBlank()) return;

            // Validate material_id is text, not date/scientific
            if (materialId.matches("\\d{4}-\\d{2}-\\d{2}.*") || materialId.matches("\\d+\\.\\d+E.*")) {
                errors.add(ParseError.of(currentRow + 1, "material_id", materialId, "INVALID_MATERIAL_ID", "料号被Excel转为日期或科学计数法"));
                return;
            }

            // Validate material exists in material master data
            Material material = materialMapper.selectByCode(materialId);
            if (material == null) {
                errors.add(ParseError.of(currentRow + 1, "material_id", materialId, "MATERIAL_NOT_FOUND", "未找到该料号，请先维护再导入"));
                return;
            }

            String factoryCode = getString(COL_FACTORY);
            String formType = getString(COL_FORM_TYPE);
            String materialName = material.materialName();
            String project = material.projectModel();
            String platform = material.specModel();
            String mold = getString(COL_MOLD);
            String status = getString(COL_STATUS);

            for (int i = 0; i < FCST_MONTHS && i < headerMonths.size(); i++) {
                String monthStr = headerMonths.get(i);
                YearMonth ym = parseMonth(monthStr);
                if (ym == null) {
                    errors.add(ParseError.of(currentRow + 1, "month_" + i, monthStr, "INVALID_MONTH", "无法识别月份"));
                    continue;
                }

                String qtyStr = getString(COL_FCST_START + i);
                BigDecimal qty = BigDecimal.ZERO;
                if (qtyStr != null && !qtyStr.isBlank()) {
                    try {
                        qty = new BigDecimal(qtyStr);
                    } catch (NumberFormatException e) {
                        errors.add(ParseError.of(currentRow + 1, "fcst_" + monthStr, qtyStr, "INVALID_QTY", "数量格式错误"));
                        continue;
                    }
                }

                rows.add(new ForecastDetail(
                        null, versionId, factoryCode, materialId, materialName,
                        null, formType, project, platform, mold, status,
                        ym.atDay(1), qty
                ));
            }
        }

        private void parseHeader() {
            headerMonths.clear();
            for (int i = COL_FCST_START; i < COL_FCST_START + FCST_MONTHS; i++) {
                String val = getString(i);
                if (val != null) {
                    headerMonths.add(val);
                    recognizedMonths.add(val);
                }
            }
        }

        private YearMonth parseMonth(String val) {
            if (val == null || val.isBlank()) return null;

            // Try Excel date serial number (e.g. 46388 = 2027-01-01)
            try {
                double serial = Double.parseDouble(val);
                LocalDate date = LocalDate.of(1900, 1, 1).plusDays((long) serial - 2);
                return YearMonth.from(date);
            } catch (Exception ignored) {}

            // Try yyyy-MM format
            try {
                return YearMonth.parse(val, DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (Exception ignored) {}

            // Try yyyy年M月 format
            try {
                String normalized = val.replace("年", "-").replace("月", "").trim();
                return YearMonth.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M"));
            } catch (Exception ignored) {}

            // Try Excel date format like 2027/1/1
            try {
                LocalDate date = LocalDate.parse(val.replace("/", "-"));
                return YearMonth.from(date);
            } catch (Exception ignored) {}

            return null;
        }

        private String getString(int col) {
            if (col < 0 || col >= currentRowData.size()) return null;
            Object val = currentRowData.get(col);
            return val != null ? val.toString().trim() : null;
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
