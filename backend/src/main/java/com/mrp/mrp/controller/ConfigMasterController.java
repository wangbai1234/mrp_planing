package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.domain.BusinessWeek;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.MaterialPolicy;
import com.mrp.mrp.domain.ProductionCalendarEntry;
import com.mrp.importexport.excel.ConfigExcelParser;
import com.mrp.importexport.excel.ParseError;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.StockPolicyExcelParser;
import com.mrp.importexport.excel.TemplateGenerator;
import com.mrp.mrp.domain.StockPolicy;
import com.mrp.mrp.service.ConfigMasterService;
import com.mrp.mrp.service.ConfigFactoryAccessService;
import com.mrp.mrp.service.StockPolicyAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** 五项配置的当前主数据。批次创建时自行复制快照，兼容版本接口仍执行同一写入校验。 */
@RestController
@RequestMapping("/api/v1/mrp")
public class ConfigMasterController {
    private final ConfigMasterService service;
    private final StockPolicyExcelParser stockPolicyParser;
    private final ConfigExcelParser configParser;
    private final TemplateGenerator templates;
    private final ConfigFactoryAccessService access;
    private final StockPolicyAccessService stockAccess;

    public ConfigMasterController(ConfigMasterService service, StockPolicyExcelParser stockPolicyParser,
                                  ConfigExcelParser configParser, TemplateGenerator templates,
                                  ConfigFactoryAccessService access, StockPolicyAccessService stockAccess) {
        this.service = service;
        this.stockPolicyParser = stockPolicyParser;
        this.configParser = configParser;
        this.templates = templates;
        this.access = access;
        this.stockAccess = stockAccess;
    }

    @GetMapping("/factory-warehouse-mappings")
    public ResponseEntity<ApiResponse<List<FactoryWarehouseMapping>>> listFactoryWarehouseMappings() {
        return ResponseEntity.ok(ApiResponse.ok(access.visibleMappings()));
    }

    @GetMapping("/factory-warehouse-mappings/page")
    public ResponseEntity<ApiResponse<PageResult<FactoryWarehouseMapping>>> factoryWarehouseMappingPage(
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String quality,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(service.factoryWarehouseMappingPage(
                access.visibleMasterCodes(), keyword, quality, page, pageSize)));
    }

    @PostMapping("/factory-warehouse-mappings")
    public ResponseEntity<ApiResponse<FactoryWarehouseMapping>> createFactoryWarehouseMapping(
            @RequestBody FactoryWarehouseMapping row) {
        access.requireFactory(row.getFactoryCode());
        return ResponseEntity.ok(ApiResponse.ok(service.createFactoryWarehouseMapping(row)));
    }

    @PutMapping("/factory-warehouse-mappings/{id}")
    public ResponseEntity<ApiResponse<FactoryWarehouseMapping>> updateFactoryWarehouseMapping(
            @PathVariable Long id, @RequestBody FactoryWarehouseMapping row) {
        access.requireMapping(id);
        return ResponseEntity.ok(ApiResponse.ok(service.updateFactoryWarehouseMapping(id, row)));
    }

    @DeleteMapping("/factory-warehouse-mappings/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFactoryWarehouseMapping(@PathVariable Long id) {
        access.requireMapping(id);
        service.deleteFactoryWarehouseMapping(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/factory-warehouse-mappings/template")
    public ResponseEntity<byte[]> factoryWarehouseTemplate() throws IOException {
        return template(templates.generateFactoryWarehouseTemplate(), "factory-warehouse-template.xlsx");
    }

    @PostMapping("/factory-warehouse-mappings/import/preview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewFactoryWarehouseImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        ParseResult<FactoryWarehouseMapping> parsed = configParser.parseFactoryWarehouse(file.getInputStream());
        access.requireMappings(parsed.rows());
        return preview(service.validateFactoryWarehouseImport(parsed));
    }

    @PostMapping("/factory-warehouse-mappings/import")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> importFactoryWarehouseMappings(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        ParseResult<FactoryWarehouseMapping> parsed = configParser.parseFactoryWarehouse(file.getInputStream());
        reject(parsed);
        access.requireMappings(parsed.rows());
        reject(service.validateFactoryWarehouseImport(parsed));
        return ResponseEntity.ok(ApiResponse.ok(service.importFactoryWarehouseMappings(parsed.rows(), file.getOriginalFilename())));
    }

    @GetMapping("/stock-policies")
    public ResponseEntity<ApiResponse<List<StockPolicy>>> listStockPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(stockAccess.list()));
    }

    @GetMapping("/stock-policies/warehouses")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> stockPolicyWarehouses() {
        return ResponseEntity.ok(ApiResponse.ok(stockAccess.warehouses()));
    }

    @GetMapping("/stock-policies/materials")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> stockPolicyMaterials(@RequestParam String keyword) {
        return ResponseEntity.ok(ApiResponse.ok(service.searchStockPolicyMaterials(keyword)));
    }

    @GetMapping({"/stock-policies/materials/page", "/material-policies/materials/page"})
    public ResponseEntity<ApiResponse<PageResult<Map<String, String>>>> policyMaterials(
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(service.searchPolicyMaterials(keyword, page, pageSize)));
    }

    @GetMapping("/material-policies/page")
    public ResponseEntity<ApiResponse<PageResult<MaterialPolicy>>> materialPolicyPage(
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String supplyMode,
            @RequestParam(defaultValue = "") String explosionPolicy, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(service.materialPolicyPage(keyword, supplyMode, explosionPolicy, page, pageSize)));
    }

    @GetMapping("/stock-policies/page")
    public ResponseEntity<ApiResponse<PageResult<StockPolicy>>> stockPolicyPage(
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String warehouseCode,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(stockAccess.page(keyword, warehouseCode, page, pageSize)));
    }

    @PostMapping("/stock-policies")
    public ResponseEntity<ApiResponse<StockPolicy>> createStockPolicy(@RequestBody StockPolicy row) {
        return ResponseEntity.ok(ApiResponse.ok(stockAccess.create(row)));
    }

    @PutMapping("/stock-policies/{id}")
    public ResponseEntity<ApiResponse<StockPolicy>> updateStockPolicy(@PathVariable Long id, @RequestBody StockPolicy row) {
        return ResponseEntity.ok(ApiResponse.ok(stockAccess.update(id, row)));
    }

    @DeleteMapping("/stock-policies/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStockPolicy(@PathVariable Long id) {
        stockAccess.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/stock-policies/template")
    public ResponseEntity<byte[]> stockPolicyTemplate() throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=stock-policy-template.xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(templates.generateStockPolicyTemplate());
    }

    @PostMapping("/stock-policies/import/preview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewStockPolicyImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return preview(stockPolicyParser.parse(file.getInputStream()));
    }

    @PostMapping("/stock-policies/import")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> importStockPolicies(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        ParseResult<StockPolicy> parsed = stockPolicyParser.parse(file.getInputStream());
        if (parsed.hasErrors()) {
            throw new IllegalStateException(parsed.errors().get(0).message());
        }
        return ResponseEntity.ok(ApiResponse.ok(stockAccess.importRows(parsed.rows(), file.getOriginalFilename())));
    }

    @GetMapping("/material-policies/template")
    public ResponseEntity<byte[]> materialPolicyTemplate() throws IOException {
        return template(templates.generateMaterialPolicyTemplate(), "material-policy-template.xlsx");
    }

    @PostMapping("/material-policies/import/preview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewMaterialPolicyImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return preview(service.validateMaterialPolicyImport(configParser.parseMaterialPolicy(file.getInputStream())));
    }

    @PostMapping("/material-policies/import")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> importMaterialPolicies(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        ParseResult<MaterialPolicy> parsed = service.validateMaterialPolicyImport(configParser.parseMaterialPolicy(file.getInputStream()));
        reject(parsed);
        return ResponseEntity.ok(ApiResponse.ok(service.importMaterialPolicies(parsed.rows(), file.getOriginalFilename())));
    }

    @GetMapping("/production-calendars/template")
    public ResponseEntity<byte[]> productionCalendarTemplate() throws IOException {
        return template(templates.generateProductionCalendarTemplate(), "production-calendar-template.xlsx");
    }

    @PostMapping("/production-calendars/import/preview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewProductionCalendarImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return preview(configParser.parseProductionCalendar(file.getInputStream()));
    }

    @PostMapping("/production-calendars/import")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> importProductionCalendar(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        ParseResult<ProductionCalendarEntry> parsed = configParser.parseProductionCalendar(file.getInputStream());
        reject(parsed);
        access.requireCalendars(parsed.rows());
        return ResponseEntity.ok(ApiResponse.ok(service.importProductionCalendar(parsed.rows(), file.getOriginalFilename())));
    }

    @GetMapping("/business-weeks/template")
    public ResponseEntity<byte[]> businessWeekTemplate() throws IOException {
        return template(templates.generateBusinessWeekTemplate(), "business-week-template.xlsx");
    }

    @PostMapping("/business-weeks/import/preview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewBusinessWeekImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return preview(configParser.parseBusinessWeeks(file.getInputStream()));
    }

    @PostMapping("/business-weeks/import")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> importBusinessWeeks(
            @RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        ParseResult<BusinessWeek> parsed = configParser.parseBusinessWeeks(file.getInputStream());
        reject(parsed);
        return ResponseEntity.ok(ApiResponse.ok(service.importBusinessWeeks(parsed.rows(), file.getOriginalFilename())));
    }


    private void checkSize(MultipartFile file) {
        if (file.getSize() > 30L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件大小超过 30MB");
        }
    }

    private ResponseEntity<byte[]> errorWorkbook(ParseResult<?> parsed, String filename) throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + filename)
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(templates.generateConfigErrorReport(parsed.headers(), parsed.rawRows(), parsed.errors()));
    }

    private ResponseEntity<byte[]> template(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + filename)
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(body);
    }

    private <T> ResponseEntity<ApiResponse<Map<String, Object>>> preview(ParseResult<T> parsed) {
        List<Map<String, Object>> previewRows = new java.util.ArrayList<>();
        int shown = Math.min(10, parsed.rawRows().size());
        java.util.SortedSet<Integer> shownIndexes = new java.util.TreeSet<>();
        for (int index = 0; index < shown; index++) shownIndexes.add(index);
        java.util.Map<Integer, String> messages = new java.util.HashMap<>();
        for (ParseError error : parsed.errors()) {
            messages.merge(error.rowNumber(), error.message(), (left, right) -> left + "；" + right);
            int index = error.rowNumber() - 3;
            if (index >= 0 && index < parsed.rawRows().size()) shownIndexes.add(index);
        }
        for (int index : shownIndexes) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("row", index + 3);
            item.put("cells", parsed.rawRows().get(index));
            String message = messages.get(index + 3);
            item.put("type", message == null ? "success" : "error");
            item.put("message", message == null ? "" : message);
            previewRows.add(item);
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "rows", parsed.rows(),
                "errors", parsed.errors().stream().map(ParseError::message).toList(),
                "totalRows", parsed.totalRows(),
                "successRows", parsed.successRows(),
                "errorRows", parsed.errorRows(),
                "previewRows", previewRows)));
    }

    private <T> void reject(ParseResult<T> parsed) {
        if (parsed.hasErrors()) throw new IllegalStateException(parsed.errors().get(0).message());
    }


    @PostMapping("/factory-warehouse-mappings/import/errors")
    public ResponseEntity<byte[]> factoryWarehouseErrors(@RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        ParseResult<FactoryWarehouseMapping> parsed = configParser.parseFactoryWarehouse(file.getInputStream());
        access.requireMappings(parsed.rows());
        return errorWorkbook(service.validateFactoryWarehouseImport(parsed), "factory-warehouse-errors.xlsx");
    }

    @PostMapping("/stock-policies/import/errors")
    public ResponseEntity<byte[]> stockPolicyErrors(@RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return errorWorkbook(stockPolicyParser.parse(file.getInputStream()), "stock-policy-errors.xlsx");
    }

    @PostMapping("/material-policies/import/errors")
    public ResponseEntity<byte[]> materialPolicyErrors(@RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return errorWorkbook(service.validateMaterialPolicyImport(configParser.parseMaterialPolicy(file.getInputStream())), "material-policy-errors.xlsx");
    }

    @PostMapping("/production-calendars/import/errors")
    public ResponseEntity<byte[]> productionCalendarErrors(@RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return errorWorkbook(configParser.parseProductionCalendar(file.getInputStream()), "production-calendar-errors.xlsx");
    }

    @PostMapping("/business-weeks/import/errors")
    public ResponseEntity<byte[]> businessWeekErrors(@RequestParam("file") MultipartFile file) throws IOException {
        checkSize(file);
        return errorWorkbook(configParser.parseBusinessWeeks(file.getInputStream()), "business-week-errors.xlsx");
    }

    @GetMapping("/material-policies")
    public ResponseEntity<ApiResponse<List<MaterialPolicy>>> listMaterialPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(service.listMaterialPolicies()));
    }

    @PostMapping("/material-policies")
    public ResponseEntity<ApiResponse<MaterialPolicy>> createMaterialPolicy(@RequestBody MaterialPolicy row) {
        return ResponseEntity.ok(ApiResponse.ok(service.createMaterialPolicy(row)));
    }

    @PutMapping("/material-policies/{id}")
    public ResponseEntity<ApiResponse<MaterialPolicy>> updateMaterialPolicy(@PathVariable Long id, @RequestBody MaterialPolicy row) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateMaterialPolicy(id, row)));
    }

    @DeleteMapping("/material-policies/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMaterialPolicy(@PathVariable Long id) {
        service.deleteMaterialPolicy(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/production-calendars")
    public ResponseEntity<ApiResponse<List<ProductionCalendarEntry>>> listProductionCalendar(
            @RequestParam(required = false) String factoryCode) {
        return ResponseEntity.ok(ApiResponse.ok(access.visibleCalendar(factoryCode)));
    }

    @GetMapping("/factories")
    public ResponseEntity<ApiResponse<List<String>>> factories() {
        return ResponseEntity.ok(ApiResponse.ok(access.visibleFactories()));
    }

    @GetMapping("/production-calendars/month")
    public ResponseEntity<ApiResponse<List<ProductionCalendarEntry>>> calendarMonth(@RequestParam String factoryCode,
            @RequestParam String month) {
        access.requireFactory(factoryCode);
        return ResponseEntity.ok(ApiResponse.ok(service.productionCalendarMonth(factoryCode, parseMonth(month))));
    }

    @GetMapping("/production-calendars/page")
    public ResponseEntity<ApiResponse<PageResult<ProductionCalendarEntry>>> calendarPage(@RequestParam String factoryCode,
            @RequestParam(required = false) String month, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        access.requireFactory(factoryCode);
        return ResponseEntity.ok(ApiResponse.ok(service.productionCalendarPage(factoryCode,
                month == null || month.isBlank() ? null : parseMonth(month), page, pageSize, keyword, status)));
    }

    @PostMapping("/production-calendars/batch/preview")
    public ResponseEntity<ApiResponse<ConfigMasterService.CalendarBatchPreview>> previewCalendarBatch(
            @RequestBody ConfigMasterService.CalendarBatchRequest request) {
        access.requireBatch(request);
        return ResponseEntity.ok(ApiResponse.ok(service.previewCalendarBatch(request)));
    }

    @PostMapping("/production-calendars/batch")
    public ResponseEntity<ApiResponse<ConfigMasterService.CalendarBatchPreview>> commitCalendarBatch(
            @RequestBody ConfigMasterService.CalendarBatchRequest request) {
        access.requireBatch(request);
        return ResponseEntity.ok(ApiResponse.ok(service.commitCalendarBatch(request)));
    }

    @GetMapping("/production-calendars/export")
    public ResponseEntity<byte[]> exportCalendar(@RequestParam String factoryCode, @RequestParam String month) throws IOException {
        access.requireFactory(factoryCode);
        return template(templates.generateProductionCalendarExport(service.productionCalendarMonth(factoryCode, parseMonth(month))
                .stream().filter(row -> row.getId() != null).toList()), "production-calendar-" + month + ".xlsx");
    }

    private YearMonth parseMonth(String month) {
        try { return YearMonth.parse(month); }
        catch (java.time.format.DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月份格式应为yyyy-MM"); }
    }

    @PostMapping("/production-calendars")
    public ResponseEntity<ApiResponse<ProductionCalendarEntry>> createProductionCalendar(
            @RequestBody ProductionCalendarEntry row) {
        access.requireFactory(row.getFactoryCode());
        return ResponseEntity.ok(ApiResponse.ok(service.createProductionCalendar(row)));
    }

    @PutMapping("/production-calendars/{id}")
    public ResponseEntity<ApiResponse<ProductionCalendarEntry>> updateProductionCalendar(
            @PathVariable Long id, @RequestBody ProductionCalendarEntry row) {
        access.requireCalendar(id);
        return ResponseEntity.ok(ApiResponse.ok(service.updateProductionCalendar(id, row)));
    }

    @DeleteMapping("/production-calendars/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProductionCalendar(@PathVariable Long id) {
        access.requireCalendar(id);
        service.deleteProductionCalendar(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/business-weeks")
    public ResponseEntity<ApiResponse<List<BusinessWeek>>> listBusinessWeeks(
            @RequestParam(required = false) LocalDate businessMonth) {
        return ResponseEntity.ok(ApiResponse.ok(service.listBusinessWeeks(businessMonth)));
    }

    @PutMapping("/business-weeks")
    public ResponseEntity<ApiResponse<List<BusinessWeek>>> saveBusinessWeeks(@RequestBody BusinessWeekSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveBusinessWeeks(request.businessMonth(), request.weeks())));
    }

    @DeleteMapping("/business-weeks")
    public ResponseEntity<ApiResponse<Void>> deleteBusinessWeeks(@RequestParam LocalDate businessMonth) {
        service.deleteBusinessWeeks(businessMonth);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    public record BusinessWeekSaveRequest(LocalDate businessMonth, List<BusinessWeek> weeks) {}
}
