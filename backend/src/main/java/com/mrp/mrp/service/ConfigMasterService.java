package com.mrp.mrp.service;

import com.mrp.audit.service.AuditService;
import com.mrp.common.response.PageResult;
import com.mrp.common.exception.ConcurrentChangeException;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.ParseError;
import com.mrp.mrp.domain.MaterialPolicyDetail;
import com.mrp.common.security.CurrentUser;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.BusinessWeek;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.MaterialPolicy;
import com.mrp.mrp.domain.ProductionCalendarEntry;
import com.mrp.mrp.domain.StockPolicy;
import com.mrp.mrp.repository.ConfigMasterMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

/** 五项配置的当前主数据。写入即生效，并记一条 audit_log。 */
@Service
public class ConfigMasterService {
    static final String FACTORY_WAREHOUSE = "factory_warehouse_mapping";
    static final String STOCK_POLICY = "stock_policy";
    static final String MATERIAL_POLICY = "material_policy";
    static final String CALENDAR = "mrp_production_calendar";
    static final String BUSINESS_WEEK = "business_week";
    private final ConfigMasterMapper mapper;
    private final AuditService audit;
    private final MaterialMapper materials;
    private final FactoryMasterService factories;

    public ConfigMasterService(ConfigMasterMapper mapper, AuditService audit, MaterialMapper materials, FactoryMasterService factories) {
        this.mapper = mapper;
        this.audit = audit;
        this.materials = materials;
        this.factories = factories;
    }

    public List<FactoryWarehouseMapping> listFactoryWarehouseMappings() { return mapper.selectFactoryWarehouseMappings(); }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResult<FactoryWarehouseMapping> factoryWarehouseMappingPage(
            List<String> factoryCodes, String keyword, String quality, int page, int pageSize) {
        int safePage = Math.max(1, page), safeSize = Math.max(1, Math.min(100, pageSize));
        if (factoryCodes.isEmpty()) return new PageResult<>(List.of(), 0, safePage, safeSize);
        int total = mapper.countFactoryWarehouseMappingPage(factoryCodes, keyword == null ? "" : keyword.trim(), quality);
        var items = mapper.selectFactoryWarehouseMappingPage(factoryCodes, keyword == null ? "" : keyword.trim(),
                quality, (long) (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    @Transactional
    public FactoryWarehouseMapping createFactoryWarehouseMapping(FactoryWarehouseMapping row) {
        row.setFactoryCode(requireCode(row.getFactoryCode(), "工厂编码"));
        validateFactory(row.getFactoryCode());
        if (row.getEnabled() == null) row.setEnabled(true);
        validateWarehouse(row, null);
        row.setWarehouseCode(requireCode(row.getWarehouseCode(), "仓库编码"));
        stampNew(row);
        insertUnique(() -> mapper.insertFactoryWarehouseMapping(row), "仓库编码已存在");
        log("CREATE", FACTORY_WAREHOUSE, row.getFactoryCode() + "|" + row.getWarehouseCode(), null, row);
        return mapper.selectFactoryWarehouseMappingById(row.getId());
    }

    @Transactional
    public FactoryWarehouseMapping updateFactoryWarehouseMapping(Long id, FactoryWarehouseMapping row) {
        FactoryWarehouseMapping before = require(mapper.selectFactoryWarehouseMappingById(id), "工厂仓库映射不存在");
        row.setId(id);
        row.setFactoryCode(before.getFactoryCode());
        validateFactory(row.getFactoryCode());
        row.setFactoryName(before.getFactoryName());
        row.setWarehouseCode(before.getWarehouseCode());
        if (row.getEnabled() == null) row.setEnabled(true);
        validateWarehouse(row, id);
        row.setUpdatedBy(CurrentUser.getUserId());
        updateUnique(() -> mapper.updateFactoryWarehouseMapping(row), "仓库已映射到其他工厂");
        FactoryWarehouseMapping after = mapper.selectFactoryWarehouseMappingById(id);
        log("UPDATE", FACTORY_WAREHOUSE, before.getFactoryCode() + "|" + before.getWarehouseCode(), before, after);
        return after;
    }

    @Transactional
    public void deleteFactoryWarehouseMapping(Long id) {
        FactoryWarehouseMapping before = require(mapper.selectFactoryWarehouseMappingById(id), "工厂仓库映射不存在");
        if (mapper.countWarehouseReferences(before.getWarehouseCode()) > 0)
            throw new IllegalStateException("仓库已有库存、策略或排产引用，请停用而非删除");
        mapper.deleteFactoryWarehouseMapping(id);
        log("DELETE", FACTORY_WAREHOUSE, before.getFactoryCode() + "|" + before.getWarehouseCode(), before, null);
    }

    public List<StockPolicy> listStockPolicies() { return mapper.selectStockPolicies(); }

    public List<Map<String, String>> searchStockPolicyMaterials(String keyword) {
        return searchPolicyMaterials(keyword, 1, 20).items();
    }

    public PageResult<Map<String, String>> searchPolicyMaterials(String keyword, int page, int pageSize) {
        page = Math.max(1, page); pageSize = Math.max(1, Math.min(100, pageSize));
        String query = keyword == null ? "" : keyword.trim();
        List<Map<String, String>> items = materials.selectPage(null, null, true, query, null,
                (page - 1) * pageSize, pageSize).stream().map(material -> Map.of(
                    "materialCode", material.materialCode(),
                    "materialName", material.materialName() == null ? "" : material.materialName(),
                    "specModel", material.specModel() == null ? "" : material.specModel())).toList();
        return new PageResult<>(items, materials.countTotal(null, null, true, query, null), page, pageSize);
    }

    public PageResult<MaterialPolicy> materialPolicyPage(String keyword, String supply, String explosion, int page, int size) {
        page = Math.max(1, page); size = Math.max(1, Math.min(100, size));
        return new PageResult<>(mapper.selectMaterialPolicyPage(keyword, supply, explosion, (page-1)*size, size),
                mapper.countMaterialPolicyPage(keyword, supply, explosion), page, size);
    }

    public PageResult<StockPolicy> stockPolicyPage(String keyword, String warehouse, int page, int size) {
        page = Math.max(1, page); size = Math.max(1, Math.min(100, size));
        return new PageResult<>(mapper.selectStockPolicyPage(keyword, warehouse, (page-1)*size, size),
                mapper.countStockPolicyPage(keyword, warehouse), page, size);
    }

    /** 新增时仓库下拉只列出工厂仓库映射里启用的仓库，名称优先。 */
    public List<Map<String, String>> listStockPolicyWarehouses() {
        Map<String, String> warehouses = new LinkedHashMap<>();
        for (FactoryWarehouseMapping mapping : mapper.selectFactoryWarehouseMappings()) {
            if (!Boolean.TRUE.equals(mapping.getEnabled()) || mapping.getWarehouseCode() == null) continue;
            warehouses.putIfAbsent(mapping.getWarehouseCode(),
                    mapping.getWarehouseName() == null || mapping.getWarehouseName().isBlank()
                            ? mapping.getWarehouseCode() : mapping.getWarehouseName());
        }
        List<Map<String, String>> result = new ArrayList<>();
        warehouses.forEach((code, name) -> result.add(Map.of("code", code, "name", name)));
        return result;
    }

    @Transactional
    public StockPolicy createStockPolicy(StockPolicy row) {
        normalize(row);
        requireKnownWarehouse(row.getWarehouseCode());
        requireKnownMaterial(row.getMaterialCode());
        stampNew(row);
        insertUnique(() -> mapper.insertStockPolicy(row), "该仓库与物料的库存策略已存在");
        logStockPolicy("CREATE", row, null, row);
        return mapper.selectStockPolicyById(row.getId());
    }

    @Transactional
    public StockPolicy updateStockPolicy(Long id, StockPolicy row) {
        StockPolicy before = require(mapper.selectStockPolicyById(id), "库存策略不存在");
        normalize(row);
        row.setId(id);
        row.setWarehouseCode(before.getWarehouseCode());
        row.setMaterialCode(before.getMaterialCode());
        row.setUpdatedBy(CurrentUser.getUserId());
        mapper.updateStockPolicy(row);
        StockPolicy after = mapper.selectStockPolicyById(id);
        logStockPolicy("UPDATE", before, before, after);
        return after;
    }

    @Transactional
    public void deleteStockPolicy(Long id) {
        StockPolicy before = require(mapper.selectStockPolicyById(id), "库存策略不存在");
        mapper.deleteStockPolicy(id);
        logStockPolicy("DELETE", before, before, null);
    }

    /** 导入按仓库+物料新增或更新，不删除文件里没有的行，按业务键记录导入变更并保留汇总。 */
    @Transactional
    public Map<String, Integer> importStockPolicies(List<StockPolicy> rows, String fileName) {
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("导入文件没有有效数据");
        int inserted = 0;
        int updated = 0;
        Long userId = CurrentUser.getUserId();
        Set<String> warehouses = enabledWarehouses();
        Set<String> knownMaterials = knownMaterialCodes(rows.stream().map(StockPolicy::getMaterialCode).toList());
        for (StockPolicy row : rows) {
            normalize(row);
            if (!warehouses.contains(row.getWarehouseCode())) {
                throw new IllegalStateException("仓库不在工厂仓库映射中或未启用: " + row.getWarehouseCode());
            }
            if (!knownMaterials.contains(row.getMaterialCode())) {
                throw new IllegalStateException("物料料号不存在: " + row.getMaterialCode());
            }
            StockPolicy existing = mapper.selectStockPolicyByKey(row.getWarehouseCode(), row.getMaterialCode());
            if (existing == null) {
                row.setCreatedBy(userId);
                row.setUpdatedBy(userId);
                mapper.insertStockPolicy(row);
                inserted++;
            } else {
                row.setId(existing.getId());
                row.setUpdatedBy(userId);
                mapper.updateStockPolicy(row);
                updated++;
            }
            logStockPolicy("IMPORT", row, existing, Map.of("fileName", fileName, "data", row));
        }
        logImportSummary(STOCK_POLICY, rows.stream().map(row -> stockFactory(row.getWarehouseCode())).distinct().toList(), fileName, inserted, updated);
        return Map.of("inserted", inserted, "updated", updated);
    }

    @Transactional(readOnly = true)
    public ParseResult<FactoryWarehouseMapping> validateFactoryWarehouseImport(ParseResult<FactoryWarehouseMapping> parsed) {
        if (parsed.hasErrors()) return parsed;
        List<ParseError> errors = new ArrayList<>();
        Set<String> priorities = new HashSet<>();
        Set<String> warehouses = new HashSet<>();
        for (FactoryWarehouseMapping row : parsed.rows()) {
            int line = 0;
            for (int i = 0; i < parsed.rawRows().size(); i++) {
                List<String> raw = parsed.rawRows().get(i);
                if (raw.size() > 1 && row.getWarehouseCode().equals(raw.get(1).trim())) {
                    line = i + 3;
                    break;
                }
            }
            try { validateFactoryWarehouseImportRow(row, priorities, warehouses); }
            catch (IllegalStateException ex) {
                errors.add(ParseError.of(line, "仓库编码", row.getWarehouseCode(), "INVALID_WAREHOUSE", ex.getMessage()));
            }
        }
        if (errors.isEmpty()) return parsed;
        return new ParseResult<>(List.of(), errors, parsed.recognizedMonths(), parsed.totalRows(), 0,
                (int) errors.stream().map(ParseError::rowNumber).distinct().count(), parsed.rawRows(), parsed.headers());
    }

    /** 按仓库编码新增或更新，不删除文件里没有的仓库，按业务键记录导入变更并保留汇总。 */
    @Transactional
    public Map<String, Integer> importFactoryWarehouseMappings(List<FactoryWarehouseMapping> rows, String fileName) {
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("导入文件没有有效数据");
        // Validate the entire file before writing its first row.
        Set<String> incomingPriorities = new HashSet<>();
        Set<String> incomingWarehouses = new HashSet<>();
        for (FactoryWarehouseMapping row : rows) {
            validateFactoryWarehouseImportRow(row, incomingPriorities, incomingWarehouses);
        }
        int inserted = 0;
        int updated = 0;
        Long userId = CurrentUser.getUserId();
        for (FactoryWarehouseMapping row : rows) {
            row.setFactoryCode(requireCode(row.getFactoryCode(), "工厂编码"));
            row.setWarehouseCode(requireCode(row.getWarehouseCode(), "仓库编码"));
            if (row.getEnabled() == null) row.setEnabled(true);
            FactoryWarehouseMapping existing = mapper.selectFactoryWarehouseMappingByWarehouse(row.getWarehouseCode());
            if (existing != null && !existing.getFactoryCode().equals(row.getFactoryCode()))
                throw new IllegalStateException("仓库已映射到其他工厂: " + row.getWarehouseCode());
            if (existing == null) {
                row.setCreatedBy(userId);
                row.setUpdatedBy(userId);
                insertUnique(() -> mapper.insertFactoryWarehouseMapping(row), "仓库编码已存在");
                inserted++;
            } else {
                row.setId(existing.getId());
                row.setUpdatedBy(userId);
                updateUnique(() -> mapper.updateFactoryWarehouseMapping(row), "仓库已映射到其他工厂");
                updated++;
            }
            logImport(FACTORY_WAREHOUSE, row.getFactoryCode() + "|" + row.getWarehouseCode(), existing, row, fileName);
        }
        logImportSummary(FACTORY_WAREHOUSE, rows.stream().map(FactoryWarehouseMapping::getFactoryCode).distinct().toList(), fileName, inserted, updated);
        return Map.of("inserted", inserted, "updated", updated);
    }

    private void validateFactoryWarehouseImportRow(FactoryWarehouseMapping row, Set<String> priorities,
            Set<String> warehouses) {
        row.setFactoryCode(requireCode(row.getFactoryCode(), "工厂编码"));
        validateFactory(row.getFactoryCode());
        row.setWarehouseCode(requireCode(row.getWarehouseCode(), "仓库编码"));
        if (!warehouses.add(row.getWarehouseCode()))
            throw new IllegalStateException("导入文件内仓库编码重复: " + row.getWarehouseCode());
        FactoryWarehouseMapping old = mapper.selectFactoryWarehouseMappingByWarehouse(row.getWarehouseCode());
        if (old != null && !old.getFactoryCode().equals(row.getFactoryCode()))
            throw new IllegalStateException("仓库已映射到其他工厂: " + row.getWarehouseCode());
        if (row.getEnabled() == null) row.setEnabled(true);
        validateWarehouse(row, old == null ? null : old.getId());
        if (Boolean.TRUE.equals(row.getEnabled()) && Boolean.TRUE.equals(row.getGoodWarehouse())
                && row.getPriorityNo() != null
                && !priorities.add(row.getFactoryCode() + "|" + row.getPriorityNo()))
            throw new IllegalStateException("导入文件中同一工厂的良品仓优先级不可重复");
    }

    /** 按物料新增或更新，不删除文件里没有的物料，按业务键记录导入变更并保留汇总。 */
    @Transactional
    public Map<String, Integer> importMaterialPolicies(List<MaterialPolicy> rows, String fileName) {
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("导入文件没有有效数据");
        for (MaterialPolicy row : rows) normalize(row);
        Set<String> known = knownMaterialCodes(rows.stream().map(MaterialPolicy::getMaterialCode).toList());
        for (MaterialPolicy row : rows) {
            if (!known.contains(row.getMaterialCode())) throw new IllegalStateException("物料不存在、停用或已删除: " + row.getMaterialCode());
        }
        int inserted = 0;
        int updated = 0;
        Long userId = CurrentUser.getUserId();
        for (MaterialPolicy row : rows) {
            normalize(row);
            MaterialPolicy existing = mapper.selectMaterialPolicyByMaterial(row.getMaterialCode());
            if (existing == null) {
                row.setCreatedBy(userId);
                row.setUpdatedBy(userId);
                mapper.insertMaterialPolicy(row);
                inserted++;
            } else {
                row.setId(existing.getId());
                row.setUpdatedBy(userId);
                mapper.updateMaterialPolicy(row);
                updated++;
            }
            logImport(MATERIAL_POLICY, row.getMaterialCode(), existing, row, fileName);
        }
        log("IMPORT", MATERIAL_POLICY, "IMPORT", null, Map.of("fileName", fileName, "inserted", inserted, "updated", updated));
        return Map.of("inserted", inserted, "updated", updated);
    }

    /** 按工厂+日期新增或更新，不删除文件里没有的日期，按业务键记录导入变更并保留汇总。 */
    @Transactional
    public Map<String, Integer> importProductionCalendar(List<ProductionCalendarEntry> rows, String fileName) {
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("导入文件没有有效数据");
        int inserted = 0;
        int updated = 0;
        Long userId = CurrentUser.getUserId();
        for (ProductionCalendarEntry row : rows) {
            normalize(row);
            ProductionCalendarEntry existing = mapper.selectProductionCalendarByKey(row.getFactoryCode(), row.getCalendarDate());
            if (existing == null) {
                row.setCreatedBy(userId);
                row.setUpdatedBy(userId);
                row.setRevision(0);
                insertCalendarUnique(() -> mapper.insertProductionCalendar(row), "日历已变化，请重新导入");
                inserted++;
            } else {
                row.setId(existing.getId());
                row.setRevision(existing.getRevision());
                row.setUpdatedBy(userId);
                if (mapper.updateProductionCalendar(row) != 1) throw new ConcurrentChangeException("日历已变化，请重新导入");
                row.setRevision(existing.getRevision() + 1);
                updated++;
            }
            logImport(CALENDAR, calendarKey(row), existing, row, fileName);
        }
        logImportSummary(CALENDAR, rows.stream().map(ProductionCalendarEntry::getFactoryCode).distinct().toList(), fileName, inserted, updated);
        return Map.of("inserted", inserted, "updated", updated);
    }

    /**
     * 按业务月整月导入：文件里出现的每个月必须覆盖整月，未出现的月份不动。
     * 每个月记一条日志。
     */
    @Transactional
    public Map<String, Integer> importBusinessWeeks(List<BusinessWeek> rows, String fileName) {
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("导入文件没有有效数据");
        Map<LocalDate, List<BusinessWeek>> byMonth = new java.util.LinkedHashMap<>();
        for (BusinessWeek row : rows) {
            if (row.getBusinessMonth() == null) throw new IllegalStateException("业务月份不能为空");
            byMonth.computeIfAbsent(row.getBusinessMonth().withDayOfMonth(1), key -> new java.util.ArrayList<>()).add(row);
        }
        Long userId = CurrentUser.getUserId();
        int inserted = 0;
        int updated = 0;
        for (Map.Entry<LocalDate, List<BusinessWeek>> entry : byMonth.entrySet()) {
            LocalDate month = entry.getKey();
            List<BusinessWeek> before = mapper.selectBusinessWeeks(month);
            List<BusinessWeek> sorted = validateBusinessWeeks(month, entry.getValue());
            for (BusinessWeek row : sorted) {
                row.setBusinessMonth(month);
                row.setCreatedBy(userId);
                row.setUpdatedBy(userId);
            }
            mapper.deleteBusinessWeeksByMonth(month);
            mapper.insertBusinessWeeks(sorted);
            List<BusinessWeek> after = mapper.selectBusinessWeeks(month);
            logImport(BUSINESS_WEEK, month.toString(), before, after, fileName);
            if (before.isEmpty()) inserted += sorted.size(); else updated += sorted.size();
        }
        return Map.of("inserted", inserted, "updated", updated);
    }

    public List<MaterialPolicy> listMaterialPolicies() { return mapper.selectMaterialPolicies(); }

    @Transactional
    public MaterialPolicy createMaterialPolicy(MaterialPolicy row) {
        normalize(row);
        requireKnownMaterial(row.getMaterialCode());
        stampNew(row);
        insertUnique(() -> mapper.insertMaterialPolicy(row), "该物料的策略已存在");
        log("CREATE", MATERIAL_POLICY, row.getMaterialCode(), null, row);
        return mapper.selectMaterialPolicyById(row.getId());
    }

    @Transactional
    public MaterialPolicy updateMaterialPolicy(Long id, MaterialPolicy row) {
        MaterialPolicy before = require(mapper.selectMaterialPolicyById(id), "物料策略不存在");
        row.setMaterialCode(before.getMaterialCode());
        normalize(row);
        requireKnownMaterial(row.getMaterialCode());
        row.setId(id);
        row.setUpdatedBy(CurrentUser.getUserId());
        mapper.updateMaterialPolicy(row);
        MaterialPolicy after = mapper.selectMaterialPolicyById(id);
        log("UPDATE", MATERIAL_POLICY, before.getMaterialCode(), before, after);
        return after;
    }

    @Transactional
    public void deleteMaterialPolicy(Long id) {
        MaterialPolicy before = require(mapper.selectMaterialPolicyById(id), "物料策略不存在");
        mapper.deleteMaterialPolicy(id);
        log("DELETE", MATERIAL_POLICY, before.getMaterialCode(), before, null);
    }

    public List<ProductionCalendarEntry> listProductionCalendar(String factoryCode) {
        return mapper.selectProductionCalendar(blankToNull(factoryCode));
    }

    /** Only the standalone parser's historical test constructor uses this fallback; runtime reads mrp_factory. */
    public static final List<String> FACTORIES = List.of("爱培科", "永惠");

    public record CalendarBatchRequest(String factoryCode, LocalDate startDate, LocalDate endDate,
            List<Integer> weekdays, Boolean productionDay, BigDecimal dailyCapacity, String capacityUnit,
            String reason, boolean overwrite, String copyFromFactory, String expectedFingerprint) {}
    public record CalendarBatchPreview(int inserted, int updated, int preserved, String fingerprint,
                                       List<ProductionCalendarEntry> rows) {}

    public List<ProductionCalendarEntry> productionCalendarMonth(String factoryCode, YearMonth month) {
        validateFactory(factoryCode);
        Map<LocalDate, ProductionCalendarEntry> configured = new LinkedHashMap<>();
        mapper.selectProductionCalendarRange(factoryCode, month.atDay(1), month.atEndOfMonth())
                .forEach(row -> configured.put(row.getCalendarDate(), row));
        List<ProductionCalendarEntry> result = new ArrayList<>();
        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
            ProductionCalendarEntry row = configured.get(date);
            if (row == null) { row = new ProductionCalendarEntry(); row.setFactoryCode(factoryCode); row.setCalendarDate(date); }
            result.add(row);
        }
        return result;
    }

    public PageResult<ProductionCalendarEntry> productionCalendarPage(String factoryCode, YearMonth month,
            int page, int pageSize, String keyword, String status) {
        validateFactory(factoryCode);
        if (status != null && !status.isBlank() && !Set.of("PRODUCTION", "REST", "UNCONFIGURED").contains(status)) throw new IllegalStateException("日历状态无效");
        page = Math.max(1, page); pageSize = Math.max(1, Math.min(100, pageSize));
        YearMonth selected = month == null ? YearMonth.now() : month;
        LocalDate start = selected.atDay(1), end = selected.atEndOfMonth();
        return new PageResult<>(mapper.selectProductionCalendarPage(factoryCode, start, end, blankToNull(keyword), blankToNull(status),
                (page - 1) * pageSize, pageSize), mapper.countProductionCalendarPage(factoryCode, start, end, blankToNull(keyword), blankToNull(status)), page, pageSize);
    }

    @Transactional(readOnly = true)
    public CalendarBatchPreview previewCalendarBatch(CalendarBatchRequest request) {
        validateBatch(request);
        List<ProductionCalendarEntry> current = mapper.selectProductionCalendarRange(request.factoryCode(), request.startDate(), request.endDate());
        List<ProductionCalendarEntry> source = copySource(request) == null ? List.of() :
                mapper.selectProductionCalendarRange(copySource(request), request.startDate(), request.endDate());
        return buildBatchPreview(request, current, source);
    }

    private CalendarBatchPreview buildBatchPreview(CalendarBatchRequest r, List<ProductionCalendarEntry> current,
                                                   List<ProductionCalendarEntry> source) {
        Map<LocalDate, ProductionCalendarEntry> targetByDate = new LinkedHashMap<>(), sourceByDate = new LinkedHashMap<>();
        current.forEach(row -> targetByDate.put(row.getCalendarDate(), row));
        source.forEach(row -> sourceByDate.put(row.getCalendarDate(), row));
        List<ProductionCalendarEntry> rows = new ArrayList<>(); int inserted = 0, updated = 0, preserved = 0;
        for (LocalDate date = r.startDate(); !date.isAfter(r.endDate()); date = date.plusDays(1)) {
            if (!r.weekdays().contains(date.getDayOfWeek().getValue())) continue;
            ProductionCalendarEntry existing = targetByDate.get(date);
            if (existing != null && !r.overwrite()) { rows.add(existing); preserved++; continue; }
            ProductionCalendarEntry row = new ProductionCalendarEntry();
            row.setFactoryCode(r.factoryCode()); row.setCalendarDate(date);
            row.setId(existing == null ? null : existing.getId()); row.setRevision(existing == null ? 0 : existing.getRevision());
            ProductionCalendarEntry sourceRow = sourceByDate.get(date);
            if (copySource(r) != null && sourceRow == null) throw new IllegalStateException("来源工厂" + date + "未配置，请先维护来源日历");
            row.setProductionDay(sourceRow == null ? r.productionDay() : sourceRow.getProductionDay());
            row.setDailyCapacity(sourceRow == null ? r.dailyCapacity() : sourceRow.getDailyCapacity());
            row.setCapacityUnit(sourceRow == null ? r.capacityUnit() : sourceRow.getCapacityUnit());
            row.setReason(sourceRow == null ? r.reason() : sourceRow.getReason());
            normalize(row); rows.add(row);
            if (existing == null) inserted++; else updated++;
        }
        return new CalendarBatchPreview(inserted, updated, preserved, batchFingerprint(r, current, source), rows);
    }

    @Transactional
    public CalendarBatchPreview commitCalendarBatch(CalendarBatchRequest request) {
        validateBatch(request);
        if (request.expectedFingerprint() == null || request.expectedFingerprint().isBlank()) throw new IllegalStateException("请先预览生产日历修改");
        // Lock in factory order so copying in opposite directions does not invert lock order.
        List<String> factories = new ArrayList<>(); factories.add(request.factoryCode());
        if (copySource(request) != null) factories.add(copySource(request)); factories.sort(String::compareTo);
        Map<String, List<ProductionCalendarEntry>> locked = new LinkedHashMap<>();
        for (String factory : factories) locked.put(factory, mapper.lockProductionCalendarRange(factory, request.startDate(), request.endDate()));
        CalendarBatchPreview preview = buildBatchPreview(request, locked.get(request.factoryCode()),
                copySource(request) == null ? List.of() : locked.get(copySource(request)));
        if (!request.expectedFingerprint().equals(preview.fingerprint())) throw new ConcurrentChangeException("生产日历已发生变化，请重新预览");
        Map<LocalDate, ProductionCalendarEntry> before = new LinkedHashMap<>();
        locked.get(request.factoryCode()).forEach(row -> before.put(row.getCalendarDate(), row));
        for (ProductionCalendarEntry row : preview.rows()) {
            ProductionCalendarEntry existing = before.get(row.getCalendarDate());
            if (existing != null && !request.overwrite()) continue;
            if (existing == null) { stampNew(row); insertCalendarUnique(() -> mapper.insertProductionCalendar(row), "日历已变化，请重新预览"); }
            else {
                row.setUpdatedBy(CurrentUser.getUserId());
                if (mapper.updateProductionCalendar(row) != 1) throw new ConcurrentChangeException("日历已变化，请重新预览");
                row.setRevision(existing.getRevision() + 1);
                row.setCreatedBy(existing.getCreatedBy()); row.setCreatedAt(existing.getCreatedAt());
            }
            log(existing == null ? "CREATE" : "UPDATE", CALENDAR, calendarKey(row), existing, row);
        }
        return preview;
    }

    private static String copySource(CalendarBatchRequest request) { return blankToNull(request.copyFromFactory()); }
    private void validateBatch(CalendarBatchRequest r) {
        validateFactory(r.factoryCode());
        if (r.startDate() == null || r.endDate() == null || r.endDate().isBefore(r.startDate())) throw new IllegalStateException("日期范围无效");
        if (java.time.temporal.ChronoUnit.DAYS.between(r.startDate(), r.endDate()) >= 366) throw new IllegalStateException("日期范围不能超过366天");
        if (r.weekdays() == null || r.weekdays().isEmpty() || r.weekdays().stream().anyMatch(day -> day == null || day < 1 || day > 7)) throw new IllegalStateException("请选择有效星期");
        if (copySource(r) != null) { validateFactory(copySource(r)); if (copySource(r).equals(r.factoryCode())) throw new IllegalStateException("来源工厂不能与目标工厂相同"); }
        else { ProductionCalendarEntry row = new ProductionCalendarEntry(); row.setFactoryCode(r.factoryCode()); row.setCalendarDate(r.startDate()); row.setProductionDay(r.productionDay()); row.setDailyCapacity(r.dailyCapacity()); row.setCapacityUnit(r.capacityUnit()); row.setReason(r.reason()); normalize(row); }
    }
    private void validateFactory(String factory) { if (!factories.enabled(factory)) throw new IllegalStateException("工厂不存在或已停用: " + factory); }

    private void validateWarehouse(FactoryWarehouseMapping row, Long excludeId) {
        row.setWarehouseCode(requireCode(row.getWarehouseCode(), "仓库编码"));
        row.setWarehouseName(requireCode(row.getWarehouseName(), "仓库名称"));
        if (row.getGoodWarehouse() == null) throw new IllegalStateException("是否良品仓必填");
        if (!Set.of("自有仓", "加工厂外协仓", "ODM仓", "NC-ODM虚拟仓", "海外三方仓", "OEM仓", "采购仓")
                .contains(row.getWarehouseAttribute())) throw new IllegalStateException("仓库属性无效");
        if (row.getPriorityNo() != null && row.getPriorityNo() < 1) throw new IllegalStateException("优先级须为正整数");
        if (Boolean.TRUE.equals(row.getEnabled()) && Boolean.TRUE.equals(row.getGoodWarehouse())
                && row.getPriorityNo() != null
                && mapper.countWarehousePriority(row.getFactoryCode(), row.getPriorityNo(), excludeId) > 0)
            throw new IllegalStateException("同一工厂的良品仓优先级不可重复");
        row.setQualityConfigured(true);
    }
    private String batchFingerprint(CalendarBatchRequest r, List<ProductionCalendarEntry> target, List<ProductionCalendarEntry> source) {
        String parameters = encoded(r.factoryCode(), r.startDate(), r.endDate(), r.weekdays(), r.productionDay(),
                r.dailyCapacity(), r.capacityUnit(), r.reason(), r.overwrite(), copySource(r));
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String text = parameters + "\n" + fingerprintRows(target) + "\nSOURCE\n" + fingerprintRows(source);
            return java.util.HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("无法生成日历指纹", e); }
    }
    private String fingerprintRows(List<ProductionCalendarEntry> rows) {
        return rows.stream().sorted(Comparator.comparing(ProductionCalendarEntry::getCalendarDate))
                .map(r -> encoded(r.getId(), r.getCalendarDate(), r.getProductionDay(), r.getDailyCapacity(),
                        r.getCapacityUnit(), r.getRevision(), r.getReason()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
    private static String encoded(Object... values) {
        StringBuilder text = new StringBuilder();
        for (Object value : values) {
            if (value == null) text.append("-1:");
            else { String field = value.toString(); text.append(field.length()).append(':').append(field); }
        }
        return text.toString();
    }

    @Transactional
    public ProductionCalendarEntry createProductionCalendar(ProductionCalendarEntry row) {
        normalize(row);
        row.setRevision(0);
        stampNew(row);
        insertCalendarUnique(() -> mapper.insertProductionCalendar(row), "该工厂的这一天已有日历");
        log("CREATE", CALENDAR, calendarKey(row), null, row);
        return mapper.selectProductionCalendarById(row.getId());
    }

    @Transactional
    public ProductionCalendarEntry updateProductionCalendar(Long id, ProductionCalendarEntry row) {
        ProductionCalendarEntry before = require(mapper.selectProductionCalendarById(id), "生产日历不存在");
        row.setId(id);
        row.setFactoryCode(before.getFactoryCode());
        row.setCalendarDate(before.getCalendarDate());
        normalize(row);
        if (row.getRevision() == null || !row.getRevision().equals(before.getRevision())) throw new ConcurrentChangeException("日历已变化，请刷新后重试");
        row.setUpdatedBy(CurrentUser.getUserId());
        if (mapper.updateProductionCalendar(row) != 1) throw new ConcurrentChangeException("日历已变化，请刷新后重试");
        ProductionCalendarEntry after = mapper.selectProductionCalendarById(id);
        log("UPDATE", CALENDAR, calendarKey(before), before, after);
        return after;
    }

    @Transactional
    public void deleteProductionCalendar(Long id) {
        ProductionCalendarEntry before = require(mapper.selectProductionCalendarById(id), "生产日历不存在");
        mapper.deleteProductionCalendar(id);
        log("DELETE", CALENDAR, calendarKey(before), before, null);
    }

    public List<BusinessWeek> listBusinessWeeks(LocalDate businessMonth) {
        return mapper.selectBusinessWeeks(businessMonth == null ? null : businessMonth.withDayOfMonth(1));
    }

    /** 业务周按月整体保存，同月记一条日志。 */
    @Transactional
    public List<BusinessWeek> saveBusinessWeeks(LocalDate businessMonth, List<BusinessWeek> rows) {
        if (businessMonth == null) throw new IllegalStateException("业务月份不能为空");
        LocalDate month = businessMonth.withDayOfMonth(1);
        List<BusinessWeek> before = mapper.selectBusinessWeeks(month);
        List<BusinessWeek> sorted = validateBusinessWeeks(month, rows);
        Long userId = CurrentUser.getUserId();
        for (BusinessWeek row : sorted) {
            row.setBusinessMonth(month);
            row.setCreatedBy(userId);
            row.setUpdatedBy(userId);
        }
        mapper.deleteBusinessWeeksByMonth(month);
        mapper.insertBusinessWeeks(sorted);
        List<BusinessWeek> after = mapper.selectBusinessWeeks(month);
        log(before.isEmpty() ? "CREATE" : "UPDATE", BUSINESS_WEEK, month.toString(), before, after);
        return after;
    }

    @Transactional
    public void deleteBusinessWeeks(LocalDate businessMonth) {
        if (businessMonth == null) throw new IllegalStateException("业务月份不能为空");
        LocalDate month = businessMonth.withDayOfMonth(1);
        List<BusinessWeek> before = mapper.selectBusinessWeeks(month);
        if (before.isEmpty()) throw new IllegalStateException("该月没有业务周期间");
        mapper.deleteBusinessWeeksByMonth(month);
        log("DELETE", BUSINESS_WEEK, month.toString(), before, null);
    }

    private List<BusinessWeek> validateBusinessWeeks(LocalDate month, List<BusinessWeek> rows) {
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("业务周期间至少需要一周");
        List<BusinessWeek> sorted = rows.stream().sorted(Comparator.comparing(BusinessWeek::getStartDate)).toList();
        Set<String> weekCodes = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            BusinessWeek row = sorted.get(i);
            row.setWeekCode(requireCode(row.getWeekCode(), "周编码").toUpperCase());
            if (!weekCodes.add(row.getWeekCode())) throw new IllegalStateException("周编码重复: " + row.getWeekCode());
            if (row.getStartDate() == null || row.getEndDate() == null || row.getStartDate().isAfter(row.getEndDate())) {
                throw new IllegalStateException("业务周开始日期不能晚于结束日期");
            }
            if (i > 0 && !sorted.get(i - 1).getEndDate().plusDays(1).equals(row.getStartDate())) {
                throw new IllegalStateException("业务周日期必须连续且不能重叠");
            }
            row.setSortNo(i + 1);
        }
        LocalDate monthEnd = month.with(TemporalAdjusters.lastDayOfMonth());
        if (sorted.get(0).getStartDate().isAfter(month) || sorted.get(sorted.size() - 1).getEndDate().isBefore(monthEnd)) {
            throw new IllegalStateException("业务周范围必须完整覆盖业务月份，可跨自然月");
        }
        return sorted;
    }

    private Set<String> enabledWarehouses() {
        Set<String> warehouses = new HashSet<>();
        for (FactoryWarehouseMapping mapping : mapper.selectFactoryWarehouseMappings()) {
            if (Boolean.TRUE.equals(mapping.getEnabled()) && mapping.getWarehouseCode() != null) {
                warehouses.add(mapping.getWarehouseCode());
            }
        }
        return warehouses;
    }

    private Set<String> knownMaterialCodes(List<String> codes) {
        List<String> distinct = codes.stream().filter(code -> code != null && !code.isBlank()).distinct().toList();
        if (distinct.isEmpty()) return Set.of();
        Set<String> known = new HashSet<>();
        for (int start = 0; start < distinct.size(); start += 1000) {
        for (Material material : materials.selectByCodes(distinct.subList(start, Math.min(start + 1000, distinct.size())))) {
            if (Boolean.TRUE.equals(material.isActive()) && Boolean.FALSE.equals(material.isDeleted())) {
                known.add(material.materialCode());
            }
        }
        }
        return known;
    }

    private void requireKnownWarehouse(String warehouseCode) {
        if (!enabledWarehouses().contains(warehouseCode)) {
            throw new IllegalStateException("仓库不在工厂仓库映射中或未启用");
        }
    }

    private void requireKnownMaterial(String materialCode) {
        if (!knownMaterialCodes(List.of(materialCode)).contains(materialCode)) {
            throw new IllegalStateException("物料料号不存在");
        }
    }

    private void normalize(StockPolicy row) {
        row.setWarehouseCode(requireCode(row.getWarehouseCode(), "仓库编码"));
        row.setMaterialCode(requireCode(row.getMaterialCode(), "物料编码"));
        BigDecimal safety = row.getSafetyStockQty() == null ? BigDecimal.ZERO : row.getSafetyStockQty();
        BigDecimal warning = row.getWarningStockQty() == null ? BigDecimal.ZERO : row.getWarningStockQty();
        if (safety.signum() < 0 || warning.signum() < 0 || safety.compareTo(warning) > 0) {
            throw new IllegalStateException("库存策略必须满足 0 ≤ 安全库存 ≤ 预警库存");
        }
        row.setSafetyStockQty(safety);
        row.setWarningStockQty(warning);
    }

    public void validateMaterialPolicyDetails(List<MaterialPolicyDetail> rows) {
        if (rows == null) throw new IllegalStateException("物料策略明细不能为空");
        Set<String> codes = new HashSet<>();
        for (MaterialPolicyDetail row : rows) {
            row.setMaterialCode(requireCode(row.getMaterialCode(), "物料编码"));
            MaterialPolicyRules.validate(row.getSupplyMode(), row.getExplosionPolicy());
            if (!codes.add(row.getMaterialCode())) throw new IllegalStateException("物料策略重复: " + row.getMaterialCode());
        }
        Set<String> known = knownMaterialCodes(List.copyOf(codes));
        for (String code : codes) if (!known.contains(code)) throw new IllegalStateException("物料不存在、停用或已删除: " + code);
    }

    public ParseResult<MaterialPolicy> validateMaterialPolicyImport(ParseResult<MaterialPolicy> parsed) {
        List<ParseError> errors = new ArrayList<>(parsed.errors());
        List<String> codes = parsed.rawRows().stream().filter(row -> !row.getFirst().startsWith("示例"))
                .map(List::getFirst).toList();
        Set<String> known = knownMaterialCodes(codes);
        for (int i = 0; i < parsed.rawRows().size(); i++) {
            String code = parsed.rawRows().get(i).getFirst();
            if (!code.isBlank() && !code.startsWith("示例") && !known.contains(code)) {
                errors.add(ParseError.of(i + 3, "物料料号", code, "REFERENCE", "物料不存在、停用或已删除: " + code));
            }
        }
        int errorRows = (int) errors.stream().map(ParseError::rowNumber).distinct().count();
        return new ParseResult<>(errors.isEmpty() ? parsed.rows() : List.of(), errors, parsed.recognizedMonths(),
                parsed.totalRows(), errors.isEmpty() ? parsed.successRows() : 0, errorRows, parsed.rawRows(), parsed.headers());
    }

    private void normalize(MaterialPolicy row) {
        row.setMaterialCode(requireCode(row.getMaterialCode(), "物料编码"));
        MaterialPolicyRules.validate(row.getSupplyMode(), row.getExplosionPolicy());
    }

    private void normalize(ProductionCalendarEntry row) {
        row.setFactoryCode(requireCode(row.getFactoryCode(), "工厂编码"));
        if (row.getCalendarDate() == null) throw new IllegalStateException("日期不能为空");
        validateFactory(row.getFactoryCode());
        if (row.getProductionDay() == null) throw new IllegalStateException("请选择生产日或休息日");
        row.setAllocationWeight(BigDecimal.ONE);
        BigDecimal capacity = row.getDailyCapacity();
        if (Boolean.TRUE.equals(row.getProductionDay())) {
            if (capacity == null || capacity.signum() <= 0) throw new IllegalStateException("生产日必须配置正日产能");
            if (!"台".equals(row.getCapacityUnit() == null ? null : row.getCapacityUnit().trim())) throw new IllegalStateException("整机日产能单位必须为台；旧配置请重新确认整机产能后保存");
        } else {
            if (capacity != null && capacity.signum() != 0) throw new IllegalStateException("休息日的日产能必须为0");
            row.setDailyCapacity(BigDecimal.ZERO);
        }
        if (capacity != null && (capacity.stripTrailingZeros().scale() > 0 || capacity.precision() - capacity.scale() > 12)) throw new IllegalStateException("整机日产能须为最多12位的整数台");
        row.setCapacityUnit(blankToNull(row.getCapacityUnit() == null ? null : row.getCapacityUnit().trim()));
        if (row.getCapacityUnit() != null && !"台".equals(row.getCapacityUnit())) throw new IllegalStateException("整机日产能单位必须为台");
        row.setCapacityUnit("台");
        if (row.getReason() != null && row.getReason().length() > 256) throw new IllegalStateException("备注不能超过256个字符");
    }

    private void stampNew(com.mrp.mrp.domain.ConfigAudit row) {
        Long userId = CurrentUser.getUserId();
        row.setCreatedBy(userId);
        row.setUpdatedBy(userId);
    }

    private void insertUnique(Runnable insert, String message) {
        try { insert.run(); } catch (DuplicateKeyException e) { throw new IllegalStateException(message); }
    }

    private void insertCalendarUnique(java.util.function.IntSupplier insert, String message) {
        try {
            if (insert.getAsInt() != 1) throw new ConcurrentChangeException(message);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentChangeException(message);
        }
    }

    private void updateUnique(Runnable update, String message) {
        try { update.run(); } catch (DuplicateKeyException e) { throw new IllegalStateException(message); }
    }

    private void logImport(String resourceType, String resourceId, Object before, Object after, String fileName) {
        log("IMPORT", resourceType, resourceId, before, Map.of("fileName", fileName, "data", after));
    }

    private void logStockPolicy(String action, StockPolicy identity, Object before, Object after) {
        // Business keys are display identifiers; warehouse codes may themselves contain '|'.
        audit.log(CurrentUser.getUserId(), null, stockFactory(identity.getWarehouseCode()), action,
                STOCK_POLICY, key(identity), before, after, null, null);
    }

    private void log(String action, String resourceType, String resourceId, Object before, Object after) {
        String scope = FACTORY_WAREHOUSE.equals(resourceType) ? resourceId.substring(0, resourceId.indexOf('|'))
                : CALENDAR.equals(resourceType) ? resourceId.substring(0, resourceId.indexOf('|')) : null;
        audit.log(CurrentUser.getUserId(), null, scope, action, resourceType, resourceId, before, after, null, null);
    }

    private void logImportSummary(String resourceType, List<String> factories, String fileName, int inserted, int updated) {
        String scope = factories.size() == 1 ? factories.getFirst() : "ALL";
        audit.log(CurrentUser.getUserId(), null, scope, "IMPORT", resourceType, "IMPORT", null,
                Map.of("fileName", fileName, "inserted", inserted, "updated", updated), null, null);
    }

    private String stockFactory(String warehouse) {
        FactoryWarehouseMapping mapping = mapper.selectFactoryWarehouseMappingByWarehouse(warehouse);
        return mapping == null || mapping.getFactoryCode() == null || mapping.getFactoryCode().isBlank()
                ? "ALL" : mapping.getFactoryCode();
    }

    private static String key(StockPolicy row) { return row.getWarehouseCode() + "|" + row.getMaterialCode(); }
    private static String calendarKey(ProductionCalendarEntry row) { return row.getFactoryCode() + "|" + row.getCalendarDate(); }

    private static <T> T require(T value, String message) {
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static String requireCode(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + "不能为空");
        return value.trim();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
