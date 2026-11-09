package com.mrp.mrp.service;

import com.mrp.mrp.domain.BusinessWeekDetail;
import com.mrp.mrp.domain.FactoryWarehouseMappingDetail;
import com.mrp.mrp.domain.StockPolicyDetail;
import com.mrp.mrp.repository.Phase2ScopeConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class Phase2ScopeConfigService {
    private final Phase2ScopeConfigMapper mapper;

    public Phase2ScopeConfigService(Phase2ScopeConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> createFactoryWarehouseVersion(LocalDate effectiveDate, Long userId) {
        mapper.insertFactoryWarehouseVersion(mapper.nextFactoryWarehouseVersionNo(), effectiveDate, userId);
        return mapper.selectFactoryWarehouseVersion(mapper.selectLastInsertId());
    }

    public List<Map<String, Object>> listFactoryWarehouseVersions() {
        return mapper.selectFactoryWarehouseVersions();
    }

    public List<FactoryWarehouseMappingDetail> getFactoryWarehouseDetails(Long versionId) {
        return mapper.selectFactoryWarehouseDetails(versionId);
    }

    @Transactional
    public void saveFactoryWarehouseDetails(Long versionId, List<FactoryWarehouseMappingDetail> details) {
        requireDraft(mapper.selectFactoryWarehouseVersionStatus(versionId), "工厂仓库映射");
        if (details == null || details.isEmpty()) {
            throw new IllegalStateException("工厂仓库映射至少需要一条记录");
        }
        Set<String> warehouses = new HashSet<>();
        for (FactoryWarehouseMappingDetail detail : details) {
            detail.setFactoryCode(requireCode(detail.getFactoryCode(), "工厂编码"));
            detail.setWarehouseCode(requireCode(detail.getWarehouseCode(), "仓库编码"));
            if (!warehouses.add(detail.getWarehouseCode())) {
                throw new IllegalStateException("同一版本内仓库只能映射一个工厂: " + detail.getWarehouseCode());
            }
            detail.setVersionId(versionId);
            if (detail.getEnabled() == null) detail.setEnabled(true);
        }
        mapper.deleteFactoryWarehouseDetails(versionId);
        mapper.insertFactoryWarehouseDetails(details);
    }

    @Transactional
    public void publishFactoryWarehouseVersion(Long versionId) {
        List<FactoryWarehouseMappingDetail> details = mapper.selectFactoryWarehouseDetails(versionId);
        if (details.isEmpty()) throw new IllegalStateException("没有可发布的工厂仓库映射");
        int updated = mapper.publishFactoryWarehouseVersion(versionId, checksum(details.stream()
                .sorted(Comparator.comparing(FactoryWarehouseMappingDetail::getFactoryCode))
                .map(d -> d.getFactoryCode() + "|" + d.getWarehouseCode() + "|" + d.getEnabled()).toList()));
        if (updated == 0) throw new IllegalStateException("工厂仓库映射版本不存在或已经发布");
    }

    @Transactional
    public void deleteFactoryWarehouseVersion(Long versionId) {
        deleteDraft(mapper.selectFactoryWarehouseVersionStatus(versionId), "工厂仓库映射",
                () -> mapper.markFactoryWarehouseVersionDeleted(versionId));
    }

    @Transactional
    public Map<String, Object> createStockPolicyVersion(LocalDate effectiveDate, Long userId) {
        mapper.insertStockPolicyVersion(mapper.nextStockPolicyVersionNo(), effectiveDate, userId);
        return mapper.selectStockPolicyVersion(mapper.selectLastInsertId());
    }

    /**
     * 以已发布版本为底复制出新草稿，再按传入行覆盖同键数据。传入行只增不删。
     */
    @Transactional
    public Map<String, Object> createIncrementalStockPolicyVersion(Long baseVersionId, LocalDate effectiveDate,
                                                                    List<StockPolicyDetail> overrides, Long userId) {
        String status = mapper.selectStockPolicyVersionStatus(baseVersionId);
        if (status == null) throw new IllegalStateException("库存控制策略版本不存在");
        if (!"PUBLISHED".equals(status)) throw new IllegalStateException("只能在已发布版本上做增量变更");
        if (overrides == null || overrides.isEmpty()) throw new IllegalStateException("增量变更至少需要一条数据");

        Map<String, StockPolicyDetail> merged = new LinkedHashMap<>();
        for (StockPolicyDetail existing : mapper.selectStockPolicyDetails(baseVersionId)) {
            merged.put(existing.getWarehouseCode() + "|" + existing.getMaterialCode(), copyPolicy(existing));
        }
        for (StockPolicyDetail override : overrides) {
            override.setWarehouseCode(requireCode(override.getWarehouseCode(), "仓库编码"));
            override.setMaterialCode(requireCode(override.getMaterialCode(), "物料编码"));
            merged.put(override.getWarehouseCode() + "|" + override.getMaterialCode(), override);
        }

        Map<String, Object> draft = createStockPolicyVersion(effectiveDate, userId);
        Long draftId = ((Number) draft.get("id")).longValue();
        saveStockPolicyDetails(draftId, new ArrayList<>(merged.values()));
        return mapper.selectStockPolicyVersion(draftId);
    }

    public List<Map<String, Object>> listStockPolicyVersions() { return mapper.selectStockPolicyVersions(); }
    public List<StockPolicyDetail> getStockPolicyDetails(Long versionId) { return mapper.selectStockPolicyDetails(versionId); }

    @Transactional
    public void saveStockPolicyDetails(Long versionId, List<StockPolicyDetail> details) {
        requireDraft(mapper.selectStockPolicyVersionStatus(versionId), "库存控制策略");
        // 空版本是合法配置：表示全部物料都不设安全库存和预警线，库存按良品数量直接扣减。
        if (details == null || details.isEmpty()) {
            mapper.deleteStockPolicyDetails(versionId);
            return;
        }
        Set<String> keys = new HashSet<>();
        for (StockPolicyDetail detail : details) {
            detail.setWarehouseCode(requireCode(detail.getWarehouseCode(), "仓库编码"));
            detail.setMaterialCode(requireCode(detail.getMaterialCode(), "物料编码"));
            BigDecimal safety = defaultZero(detail.getSafetyStockQty());
            BigDecimal warning = defaultZero(detail.getWarningStockQty());
            if (safety.signum() < 0 || warning.signum() < 0 || safety.compareTo(warning) > 0) {
                throw new IllegalStateException("库存策略必须满足 0 ≤ 安全库存 ≤ 预警库存");
            }
            if (!keys.add(detail.getWarehouseCode() + "|" + detail.getMaterialCode())) {
                throw new IllegalStateException("仓库与物料组合重复: " + detail.getWarehouseCode() + "/" + detail.getMaterialCode());
            }
            detail.setSafetyStockQty(safety);
            detail.setWarningStockQty(warning);
            detail.setVersionId(versionId);
        }
        mapper.deleteStockPolicyDetails(versionId);
        mapper.insertStockPolicyDetails(details);
    }

    @Transactional
    public void publishStockPolicyVersion(Long versionId) {
        List<StockPolicyDetail> details = mapper.selectStockPolicyDetails(versionId);
        int updated = mapper.publishStockPolicyVersion(versionId, checksum(details.stream()
                .sorted(Comparator.comparing((StockPolicyDetail d) -> d.getWarehouseCode())
                        .thenComparing(StockPolicyDetail::getMaterialCode))
                .map(d -> d.getWarehouseCode() + "|" + d.getMaterialCode() + "|"
                        + d.getSafetyStockQty().toPlainString() + "|" + d.getWarningStockQty().toPlainString()).toList()));
        if (updated == 0) throw new IllegalStateException("库存控制策略版本不存在或已经发布");
    }

    @Transactional
    public void deleteStockPolicyVersion(Long versionId) {
        deleteDraft(mapper.selectStockPolicyVersionStatus(versionId), "库存控制策略",
                () -> mapper.markStockPolicyVersionDeleted(versionId));
    }

    @Transactional
    public Map<String, Object> createBusinessWeekVersion(LocalDate businessMonth, Long userId) {
        if (businessMonth == null) throw new IllegalStateException("业务月份不能为空");
        LocalDate normalized = businessMonth.withDayOfMonth(1);
        mapper.insertBusinessWeekVersion(mapper.nextBusinessWeekVersionNo(normalized), normalized, userId);
        return mapper.selectBusinessWeekVersion(mapper.selectLastInsertId());
    }

    public List<Map<String, Object>> listBusinessWeekVersions() { return mapper.selectBusinessWeekVersions(); }
    public List<BusinessWeekDetail> getBusinessWeekDetails(Long versionId) { return mapper.selectBusinessWeekDetails(versionId); }

    @Transactional
    public void saveBusinessWeekDetails(Long versionId, List<BusinessWeekDetail> details) {
        requireDraft(mapper.selectBusinessWeekVersionStatus(versionId), "业务周期间");
        if (details == null || details.isEmpty()) throw new IllegalStateException("业务周期间至少需要一周");
        List<BusinessWeekDetail> sorted = details.stream()
                .sorted(Comparator.comparing(BusinessWeekDetail::getStartDate)).toList();
        Set<String> weekCodes = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            BusinessWeekDetail detail = sorted.get(i);
            detail.setWeekCode(requireCode(detail.getWeekCode(), "周编码").toUpperCase());
            if (!weekCodes.add(detail.getWeekCode())) throw new IllegalStateException("周编码重复: " + detail.getWeekCode());
            if (detail.getStartDate() == null || detail.getEndDate() == null || detail.getStartDate().isAfter(detail.getEndDate())) {
                throw new IllegalStateException("业务周开始日期不能晚于结束日期");
            }
            if (i > 0 && !sorted.get(i - 1).getEndDate().plusDays(1).equals(detail.getStartDate())) {
                throw new IllegalStateException("业务周日期必须连续且不能重叠");
            }
            detail.setVersionId(versionId);
            detail.setSortNo(i + 1);
        }
        Map<String, Object> version = mapper.selectBusinessWeekVersion(versionId);
        LocalDate businessMonth = asLocalDate(version.get("businessMonth"), version.get("BUSINESS_MONTH"));
        LocalDate monthEnd = businessMonth.with(TemporalAdjusters.lastDayOfMonth());
        if (sorted.get(0).getStartDate().isAfter(businessMonth) || sorted.get(sorted.size() - 1).getEndDate().isBefore(monthEnd)) {
            throw new IllegalStateException("业务周范围必须完整覆盖业务月份，可跨自然月");
        }
        mapper.deleteBusinessWeekDetails(versionId);
        mapper.insertBusinessWeekDetails(sorted);
    }

    @Transactional
    public void publishBusinessWeekVersion(Long versionId) {
        List<BusinessWeekDetail> details = mapper.selectBusinessWeekDetails(versionId);
        if (details.isEmpty()) throw new IllegalStateException("没有可发布的业务周期间");
        int updated = mapper.publishBusinessWeekVersion(versionId, checksum(details.stream()
                .sorted(Comparator.comparing(BusinessWeekDetail::getSortNo))
                .map(d -> d.getWeekCode() + "|" + d.getStartDate() + "|" + d.getEndDate()).toList()));
        if (updated == 0) throw new IllegalStateException("业务周版本不存在或已经发布");
    }

    @Transactional
    public void deleteBusinessWeekVersion(Long versionId) {
        deleteDraft(mapper.selectBusinessWeekVersionStatus(versionId), "业务周",
                () -> mapper.markBusinessWeekVersionDeleted(versionId));
    }

    private void deleteDraft(String status, String label, java.util.function.IntSupplier markDeleted) {
        if (status == null) throw new IllegalStateException(label + "版本不存在");
        if (!"DRAFT".equals(status)) throw new IllegalStateException("已发布的" + label + "版本不允许删除");
        if (markDeleted.getAsInt() == 0) throw new IllegalStateException("已发布的" + label + "版本不允许删除");
    }

    private void requireDraft(String status, String label) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException(label + "版本不存在或不是草稿状态");
    }

    private String requireCode(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + "不能为空");
        return value.trim();
    }

    private BigDecimal defaultZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private StockPolicyDetail copyPolicy(StockPolicyDetail source) {
        StockPolicyDetail copy = new StockPolicyDetail();
        copy.setWarehouseCode(source.getWarehouseCode());
        copy.setMaterialCode(source.getMaterialCode());
        copy.setSafetyStockQty(source.getSafetyStockQty());
        copy.setWarningStockQty(source.getWarningStockQty());
        return copy;
    }

    private LocalDate asLocalDate(Object lower, Object upper) {
        Object value = lower != null ? lower : upper;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value == null) throw new IllegalStateException("业务周版本不存在");
        return LocalDate.parse(value.toString());
    }

    private String checksum(List<String> canonicalLines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.join("\n", canonicalLines).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
