package com.mrp.mrp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.audit.service.AuditService;
import com.mrp.common.response.PageResult;
import com.mrp.common.security.CurrentUser;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpMaterialPolicyNoticeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** 与计算 run 一起冻结的提示；不写入决策 issue，不阻断计算；默认下钻须经本次确认后发布。 */
@Service
public class MaterialPolicyNoticeService {
    private final MrpMaterialPolicyNoticeMapper mapper;
    private final MaterialMapper materials;
    private final MrpCalcBatchMapper batches;
    private final ObjectMapper json;
    private final AuditService audit;
    public MaterialPolicyNoticeService(MrpMaterialPolicyNoticeMapper mapper, MaterialMapper materials,
                                      MrpCalcBatchMapper batches, ObjectMapper json, AuditService audit) {
        this.mapper = mapper; this.materials = materials; this.batches = batches; this.json = json; this.audit = audit;
    }

    @Transactional
    public void record(MrpCalcBatch batch, Long runId, List<MrpRequirementDetail> details,
                       Map<String, MaterialPolicyDetail> policies) {
        mapper.deleteByRunId(runId);
        List<MrpMaterialPolicyNotice> notices = evaluate(batch, runId, details, policies);
        for (int i=0; i<notices.size(); i+=500) mapper.insertBatch(notices.subList(i, Math.min(i+500, notices.size())));
    }

    List<MrpMaterialPolicyNotice> evaluate(MrpCalcBatch batch, Long runId, List<MrpRequirementDetail> details,
                                         Map<String, MaterialPolicyDetail> policies) {
        Map<String, MrpMaterialPolicyNotice> notices = new TreeMap<>();
        Map<String, Set<Map<String, String>>> sources = new LinkedHashMap<>();
        for (MrpRequirementDetail detail : details) {
            String code = detail.getCurrentMaterialCode();
            MaterialPolicyDetail policy = policies.get(code);
            boolean supplyMissing = policy == null || !MaterialPolicyRules.validSupply(policy.getSupplyMode());
            boolean explosionMissing = policy == null || !MaterialPolicyRules.validExplosion(policy.getExplosionPolicy());
            if (!supplyMissing && !explosionMissing) continue;
            String key = detail.getFactoryCode() + "|" + code;
            notices.computeIfAbsent(key, ignored -> {
                MrpMaterialPolicyNotice notice = new MrpMaterialPolicyNotice();
                notice.setBatchId(batch.getId()); notice.setRunId(runId); notice.setFactoryCode(detail.getFactoryCode());
                notice.setMaterialCode(code);
                notice.setMissingType(supplyMissing && explosionMissing ? "BOTH" : supplyMissing ? "SUPPLY" : "EXPLOSION");
                notice.setEffectiveExplosionPolicy(MaterialPolicyRules.effectiveExplosion(policy == null ? null : policy.getExplosionPolicy()));
                return notice;
            });
            sources.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(Map.of(
                    "rootMaterialCode", detail.getRootMaterialCode(), "sourceMonth", detail.getSourceMonth().toString(),
                    "pathText", detail.getPathText() == null ? code : detail.getPathText()));
        }
        List<String> codes = notices.values().stream().map(MrpMaterialPolicyNotice::getMaterialCode).distinct().toList();
        Map<String, String> names = new HashMap<>();
        for (int i=0; i<codes.size(); i+=1000) {
            for (var material : materials.selectByCodes(codes.subList(i, Math.min(i+1000, codes.size())))) {
                names.put(material.materialCode(), material.materialName());
            }
        }
        for (var entry : notices.entrySet()) {
            entry.getValue().setMaterialName(names.get(entry.getValue().getMaterialCode()));
            try { entry.getValue().setSourcesJson(json.writeValueAsString(sources.get(entry.getKey()))); }
            catch (JsonProcessingException error) { throw new IllegalStateException("无法保存物料策略缺失来源", error); }
        }
        return new ArrayList<>(notices.values());
    }

    public PageResult<MrpMaterialPolicyNotice> page(Long batchId, String keyword, String missingType, int page, int size) {
        MrpCalcBatch batch = accessibleBatch(batchId);
        page = Math.max(1, page); size = Math.max(1, Math.min(100, size));
        if (batch.getActiveRunId() == null) return new PageResult<>(List.of(), 0, page, size);
        return new PageResult<>(mapper.selectPage(batch.getActiveRunId(), keyword, missingType, (page-1)*size, size),
                mapper.countPage(batch.getActiveRunId(), keyword, missingType), page, size);
    }

    public int pendingDefaultExplosionCount(Long batchId) {
        MrpCalcBatch batch = accessibleBatch(batchId);
        return batch.getActiveRunId() == null ? 0 : mapper.countPendingDefaultExplosion(batch.getActiveRunId());
    }

    @Transactional
    public int confirmDefaultExplosion(Long batchId) {
        MrpCalcBatch batch = accessibleBatch(batchId);
        if (batch.getActiveRunId() == null) throw new IllegalStateException("当前排产尚无计算结果");
        Long operatorId = CurrentUser.getUserId();
        if (operatorId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
        int changed = mapper.confirmDefaultExplosion(batch.getActiveRunId(), operatorId);
        if (changed > 0) audit.log(operatorId, null, batch.getFactoryCode(), "CONFIRM_DEFAULT_EXPLOSION",
                "mrp_material_policy_notice", batchId + ":" + batch.getActiveRunId(), null,
                Map.of("confirmedCount", changed, "rule", "DEFAULT_EXPLODE_TO_LEAF", "runId", batch.getActiveRunId()), null, null);
        return changed;
    }

    private MrpCalcBatch accessibleBatch(Long batchId) {
        MrpCalcBatch batch = batches.selectById(batchId);
        if (batch == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排产版本不存在");
        List<String> frozenFactories = batches.selectFactoryCodesByBatchId(batch.getId());
        for (String factory : frozenFactories.isEmpty() ? ProductionCapacityRules.factories(batch) : frozenFactories) {
            if (mapper.canReadFactory(CurrentUser.getUserId(), factory) == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该工厂的排产结果");
            }
        }
        return batch;
    }
    public void copyRun(Long sourceRunId, Long batchId, Long runId) { mapper.copyRun(sourceRunId, batchId, runId); }
    public void deleteRun(Long runId) { mapper.deleteByRunId(runId); }
}
