package com.mrp.planning.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.NotFoundException;
import com.mrp.common.exception.ValidationException;
import com.mrp.planning.domain.*;
import com.mrp.planning.repository.PlanMapper;
import com.mrp.planning.repository.PlanOverrideMapper;
import com.mrp.task.repository.ImportTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PlanningDomainService {

    private static final Logger log = LoggerFactory.getLogger(PlanningDomainService.class);

    private final PlanMapper planMapper;
    private final PlanOverrideMapper overrideMapper;
    private final ImportTaskMapper importTaskMapper;

    public PlanningDomainService(PlanMapper planMapper, PlanOverrideMapper overrideMapper,
                                 ImportTaskMapper importTaskMapper) {
        this.planMapper = planMapper;
        this.overrideMapper = overrideMapper;
        this.importTaskMapper = importTaskMapper;
    }

    // === 人工调整 ===

    @Transactional
    public PlanOverride saveOverride(Long planVersionId, String materialId, LocalDate weekStartDate,
                                     Long manualQuantity, String reason, Long userId) {
        PlanVersion version = planMapper.selectVersionById(planVersionId);
        if (version == null) throw new NotFoundException("PlanVersion", planVersionId);
        if (PlanVersion.STATUS_CALCULATING.equals(version.status())) {
            throw new ValidationException("Cannot override while calculating");
        }

        // Get system quantity for audit
        List<PlanDetail> details = planMapper.selectDetailsByVersionId(planVersionId);
        PlanDetail detail = details.stream()
                .filter(d -> materialId.equals(d.materialId()) && weekStartDate.equals(d.weekStartDate()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("PlanDetail", materialId + "@" + weekStartDate));

        if (manualQuantity < 0) {
            throw new ValidationException("人工调整数量不能为负");
        }

        PlanOverride override = new PlanOverride(
                null, planVersionId, materialId, weekStartDate,
                detail.systemQuantity(), manualQuantity, reason, userId, null
        );
        overrideMapper.upsert(override);

        log.info("Override saved: version={}, material={}, week={}, manual={}",
                planVersionId, materialId, weekStartDate, manualQuantity);

        return overrideMapper.selectByUnique(planVersionId, materialId, weekStartDate);
    }

    @Transactional
    public void restoreAutoValue(Long planVersionId, String materialId, LocalDate weekStartDate, Long userId) {
        PlanOverride existing = overrideMapper.selectByUnique(planVersionId, materialId, weekStartDate);
        if (existing == null) {
            throw new NotFoundException("PlanOverride", materialId + "@" + weekStartDate);
        }
        overrideMapper.deleteByUnique(planVersionId, materialId, weekStartDate);
        log.info("Override restored to auto: version={}, material={}, week={}", planVersionId, materialId, weekStartDate);
    }

    public List<PlanOverride> listOverrides(Long planVersionId) {
        return overrideMapper.selectByVersionId(planVersionId);
    }

    // === 发布 ===

    @Transactional
    public PlanVersion publish(Long planVersionId, Long userId) {
        PlanVersion version = planMapper.selectVersionById(planVersionId);
        if (version == null) throw new NotFoundException("PlanVersion", planVersionId);

        if (!PlanVersion.STATUS_READY.equals(version.status())) {
            throw new ValidationException("Only READY plans can be published, current: " + version.status());
        }

        // Check capacity exceeded (C007: hard block)
        List<PlanDetail> details = planMapper.selectDetailsByVersionId(planVersionId);
        boolean hasExceeded = details.stream().anyMatch(PlanDetail::capacityExceeded);
        if (hasExceeded) {
            long totalExcess = details.stream().mapToLong(PlanDetail::capacityExcessQty).sum();
            throw new BusinessException("MRP_CAPACITY_EXCEEDED",
                    "存在超产能排产，差额 " + totalExcess + "，请先调整再发布");
        }

        planMapper.updateVersionStatus(planVersionId, PlanVersion.STATUS_PUBLISHED, version.resultChecksum(), 0);
        log.info("Plan published: versionId={}", planVersionId);

        return planMapper.selectVersionById(planVersionId);
    }

    // === 版本对比 ===

    public Map<String, Object> compareVersions(Long baseId, Long currentId) {
        List<PlanDetail> baseDetails = planMapper.selectDetailsByVersionId(baseId);
        List<PlanDetail> currentDetails = planMapper.selectDetailsByVersionId(currentId);

        Map<String, PlanDetail> baseMap = baseDetails.stream()
                .collect(Collectors.toMap(
                        d -> d.materialId() + ":" + d.weekStartDate(),
                        d -> d, (a, b) -> a));

        List<Map<String, Object>> diffs = currentDetails.stream()
                .map(current -> {
                    String key = current.materialId() + ":" + current.weekStartDate();
                    PlanDetail base = baseMap.get(key);
                    long baseQty = base != null ? base.effectiveQuantity() : 0;
                    long diff = current.effectiveQuantity() - baseQty;
                    if (diff == 0) return null;
                    return Map.<String, Object>of(
                            "materialId", current.materialId(),
                            "weekStartDate", current.weekStartDate().toString(),
                            "baseQty", baseQty,
                            "currentQty", current.effectiveQuantity(),
                            "diff", diff
                    );
                })
                .filter(d -> d != null)
                .toList();

        return Map.of(
                "baseVersionId", baseId,
                "currentVersionId", currentId,
                "differences", diffs
        );
    }

    // === 版本列表 ===

    public List<PlanVersion> listVersions(String factoryCode) {
        return planMapper.selectVersionsByFactory(factoryCode);
    }

    // === 带 override 的网格查询 ===

    public Map<String, Object> getPlanGridWithOverrides(Long planVersionId) {
        PlanVersion version = planMapper.selectVersionById(planVersionId);
        if (version == null) throw new NotFoundException("PlanVersion", planVersionId);

        List<PlanDetail> details = planMapper.selectDetailsByVersionId(planVersionId);
        List<PlanOverride> overrides = overrideMapper.selectByVersionId(planVersionId);

        Map<String, PlanOverride> overrideMap = overrides.stream()
                .collect(Collectors.toMap(
                        o -> o.materialId() + ":" + o.weekStartDate(),
                        o -> o, (a, b) -> b));

        // Apply overrides to details
        List<Map<String, Object>> grid = details.stream()
                .map(d -> {
                    String key = d.materialId() + ":" + d.weekStartDate();
                    PlanOverride ov = overrideMap.get(key);
                    Long manualQty = ov != null ? ov.manualQuantity() : d.manualQuantity();
                    long effective = manualQty != null ? manualQty : d.systemQuantity();
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("materialId", d.materialId());
                    m.put("materialName", d.materialName() != null ? d.materialName() : "");
                    m.put("factoryCode", d.factoryCode());
                    m.put("weekStartDate", d.weekStartDate().toString());
                    m.put("physicalMonth", d.physicalMonth().toString());
                    m.put("sourceMonth", d.sourceMonth().toString());
                    m.put("slot", d.slot());
                    m.put("isCarry", d.isCarry());
                    m.put("isLocked", d.isLocked());
                    m.put("systemQuantity", d.systemQuantity());
                    m.put("manualQuantity", manualQty != null ? manualQty : "");
                    m.put("effectiveQuantity", effective);
                    m.put("capacityExceeded", d.capacityExceeded());
                    m.put("capacityExcessQty", d.capacityExcessQty());
                    return m;
                })
                .toList();

        return Map.of(
                "version", version,
                "grid", grid
        );
    }

    // === 恢复人工覆盖的计划详情（用于重算时保留覆盖） ===

    public Map<String, Long> getOverridesMap(Long planVersionId) {
        return overrideMapper.selectByVersionId(planVersionId).stream()
                .collect(Collectors.toMap(
                        o -> o.materialId() + ":" + o.weekStartDate(),
                        PlanOverride::manualQuantity,
                        (a, b) -> b));
    }
}
