package com.mrp.mrp.service;

import com.mrp.audit.service.AuditService;
import com.mrp.common.exception.ConcurrentChangeException;
import com.mrp.common.response.PageResult;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Two role acknowledgements follow the effective facts, never an editable batch status. */
@Service
public class MrpWorkbenchReviewService {
    private final MrpCalcBatchMapper batches;
    private final MrpWorkbenchReviewMapper reviews;
    private final MrpMaterialFeedbackMapper feedback;
    private final MrpDailyPlanDetailMapper daily;
    private final MrpTerminalDemandMapper demands;
    private final MrpRequirementDetailMapper requirements;
    private final MrpDailyPlanOverrideMapper overrides;
    private final MrpInventoryAllocationMapper allocations;
    private final MrpDecisionIssueMapper issues;
    private final MrpUnallocatedQuantityMapper unallocated;
    private final MrpMaterialPolicyNoticeMapper policies;
    private final MrpStockRiskMapper risks;
    private final PlanningFactoryAccessService access;
    private final DailyPlanningService planning;
    private final AuditService audit;

    public MrpWorkbenchReviewService(MrpCalcBatchMapper batches, MrpWorkbenchReviewMapper reviews,
            MrpMaterialFeedbackMapper feedback, MrpDailyPlanDetailMapper daily, MrpTerminalDemandMapper demands,
            MrpRequirementDetailMapper requirements, MrpDailyPlanOverrideMapper overrides,
            MrpInventoryAllocationMapper allocations, MrpDecisionIssueMapper issues,
            MrpUnallocatedQuantityMapper unallocated, MrpMaterialPolicyNoticeMapper policies,
            MrpStockRiskMapper risks, PlanningFactoryAccessService access, DailyPlanningService planning,
            AuditService audit) {
        this.batches=batches; this.reviews=reviews; this.feedback=feedback; this.daily=daily;
        this.demands=demands; this.requirements=requirements; this.overrides=overrides;
        this.allocations=allocations; this.issues=issues; this.unallocated=unallocated;
        this.policies=policies; this.risks=risks; this.access=access; this.planning=planning; this.audit=audit;
    }

    public record Status(String schedule, String material, Long activeRunId,
                         String scheduleFingerprint, String materialFingerprint, int openFeedback) {}

    @Transactional(readOnly=true)
    public Status status(Long batchId) {
        access.requireBatch(batchId);
        return statusForBatch(batches.selectById(batchId));
    }

    /** Internal publish worker has no HTTP principal; submission already checked factory access. */
    private Status statusForBatch(MrpCalcBatch batch) {
        Long batchId = batch.getId();
        if (batch.getActiveRunId()==null) return new Status("NOT_GENERATED","NOT_ANALYZED",null,null,null,feedback.countOpen(batchId));
        String schedule = scheduleFingerprint(batch);
        boolean analyzed = "MATERIAL_REVIEW".equals(batch.getStatus()) || "READY".equals(batch.getStatus()) || "PUBLISHED".equals(batch.getStatus())
                || "SUPERSEDED".equals(batch.getStatus())
                || (!ProductionCapacityRules.VERSION.equals(batch.getRuleVersion()) && "WAITING_DECISION".equals(batch.getStatus()));
        String material = analyzed ? materialFingerprint(batch, schedule) : null;
        MrpWorkbenchReviewMapper.Review scheduleReview = reviews.latest(batchId,"SCHEDULE");
        MrpWorkbenchReviewMapper.Review materialReview = reviews.latest(batchId,"MATERIAL");
        if ("PUBLISHED".equals(batch.getStatus()) && ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())) {
            // Published facts have a new run ID. The publication copies only verified
            // confirmations, preserving their original operator, time and fingerprints.
            return new Status(scheduleReview==null?"HISTORICAL_UNVERIFIED":"CONFIRMED",
                    materialReview==null?"HISTORICAL_UNVERIFIED":"CONFIRMED",
                    batch.getActiveRunId(),scheduleReview==null?null:scheduleReview.scheduleFingerprint(),
                    materialReview==null?null:materialReview.materialFingerprint(),feedback.countOpen(batchId));
        }
        boolean scheduleCurrent = current(scheduleReview,batch,schedule,null);
        boolean materialCurrent = analyzed && materialReview != null && materialReview.materialFingerprint() != null
                && !"INVALIDATED_BY_ALLOCATION".equals(materialReview.scheduleFingerprint())
                && current(materialReview,batch,schedule,material);
        return new Status(scheduleCurrent?"CONFIRMED":scheduleReview==null?"PENDING":"EXPIRED",
                !analyzed?"NOT_ANALYZED":materialCurrent?"CONFIRMED":materialReview==null?"PENDING":"EXPIRED",
                batch.getActiveRunId(),schedule,material,feedback.countOpen(batchId));
    }

    @Transactional
    public Status confirm(Long batchId, String type, String expectedFingerprint) {
        MrpCalcBatch batch = batches.selectForUpdate(batchId);
        access.require(batch);
        if (!Set.of("SCHEDULE","MATERIAL").contains(type)) throw new IllegalArgumentException("确认类型无效");
        if (batch.getActiveRunId()==null || !("READY".equals(batch.getStatus())
                || "MATERIAL".equals(type) && "MATERIAL_REVIEW".equals(batch.getStatus())
                || "SCHEDULE".equals(type) && "SCHEDULE_READY".equals(batch.getStatus())))
            throw new IllegalStateException("当前阶段尚不能确认");
        String schedule=scheduleFingerprint(batch);
        String material="MATERIAL".equals(type)?materialFingerprint(batch,schedule):null;
        String current="MATERIAL".equals(type)?material:schedule;
        if (!Objects.equals(expectedFingerprint,current))
            throw new ConcurrentChangeException("排产或物料结果已变化，请刷新后重新确认");
        Long operator=CurrentUser.getUserId();
        if (operator==null) throw new IllegalStateException("请先登录");
        if (current(reviews.latest(batchId,type),batch,schedule,material)) return statusForBatch(batch);
        if ("MATERIAL".equals(type)) {
            if (!current(reviews.latest(batchId,"SCHEDULE"),batch,schedule,null))
                throw new IllegalStateException("请先确认当前整机排产");
            if (ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())
                    && !Set.of("MATERIAL_REVIEW","READY").contains(batch.getStatus()))
                throw new IllegalStateException("物料分析尚未进入复核阶段");
            if (feedback.countOpen(batchId)>0) throw new IllegalStateException("尚有未处理的物料反馈");
            if (issues.countByBatchId(batchId,"OPEN",null)>0
                    || unallocated.countByRunIdAndStatus(batch.getActiveRunId(),"PENDING",null)>0
                    || policies.countPendingDefaultExplosion(batch.getActiveRunId())>0
                    || risks.countByBatchId(batchId,batch.getActiveRunId())>0)
                throw new IllegalStateException("请先处理物料异常、待分配和库存风险");
            if ("MATERIAL_REVIEW".equals(batch.getStatus())) batches.updateStatusForce(batchId,"READY");
        }
        reviews.insert(batchId,type,batch.getActiveRunId(),schedule,material,operator);
        audit.log(operator,null,batch.getFactoryCode(),"CONFIRM_"+type,"mrp_workbench_review",
                batchId.toString(),null,Map.of("runId",batch.getActiveRunId(),"fingerprint",current),null,null);
        return status(batchId);
    }

    @Transactional(readOnly=true)
    public void requireConfirmed(MrpCalcBatch batch) {
        if (!ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())) return;
        Status status=statusForBatch(batch);
        if (!"CONFIRMED".equals(status.schedule()) || !"CONFIRMED".equals(status.material())
                || status.openFeedback()>0)
            throw new IllegalStateException("无法发布：当前整机排产与物料分析须双岗确认，且物料反馈全部关闭");
    }

    public void copyPublishedConfirmations(Long sourceBatchId, Long publishedBatchId, Long publishedRunId) {
        MrpCalcBatch source = batches.selectById(sourceBatchId);
        requireConfirmed(source);
        if (reviews.copyPublished(sourceBatchId,publishedBatchId,publishedRunId)!=2)
            throw new IllegalStateException("发布确认快照复制不完整");
    }

    private boolean current(MrpWorkbenchReviewMapper.Review review, MrpCalcBatch batch,
                            String schedule, String material) {
        return review!=null && Objects.equals(review.runId(),batch.getActiveRunId())
                && Objects.equals(review.scheduleFingerprint(),schedule)
                && (material==null || Objects.equals(review.materialFingerprint(),material));
    }

    /** Worker has no logged-in HTTP principal; verify the frozen schedule internally. */
    public boolean matchesSchedule(Long batchId, Long runId, String fingerprint) {
        MrpCalcBatch batch = batches.selectById(batchId);
        return batch != null && Objects.equals(batch.getActiveRunId(), runId)
                && Objects.equals(scheduleFingerprint(batch), fingerprint)
                && current(reviews.latest(batchId,"SCHEDULE"),batch,fingerprint,null);
    }

    private String scheduleFingerprint(MrpCalcBatch batch) {
        List<String> facts=new ArrayList<>();
        for (MrpDailyPlanDetail row:planning.getDailyPlans(batch.getId())) {
            if (!ProductionCapacityRules.MACHINE_OUTPUT.equals(row.getTerminalType())) continue;
            facts.add(String.join("|",row.getFactoryCode(),row.getRootMaterialCode(),
                    String.valueOf(row.getSourceMonth()),String.valueOf(row.getScheduleDate()),
                    row.getEffectiveQuantity().stripTrailingZeros().toPlainString()));
        }
        Collections.sort(facts);
        return hash(batch.getActiveRunId()+"|"+batch.getInputChecksum()+"|"+String.join(";",facts));
    }

    private String materialFingerprint(MrpCalcBatch batch,String schedule) {
        List<String> facts=new ArrayList<>();
        Long run=batch.getActiveRunId();
        for (MrpTerminalDemand row:demands.selectByRunId(run))
            if (!ProductionCapacityRules.MACHINE_OUTPUT.equals(row.getTerminalType()))
                facts.add("D|"+row.getFactoryCode()+"|"+row.getRootMaterialCode()+"|"+row.getTerminalMaterialCode()+"|"+row.getSourceMonth()+"|"+row.getQuantity().toPlainString());
        for (MrpRequirementDetail row:requirements.selectByRunId(run))
            facts.add("R|"+row.getFactoryCode()+"|"+row.getRootMaterialCode()+"|"+row.getCurrentMaterialCode()+"|"+row.getSourceMonth()+"|"+row.getPathHash()+"|"+row.getNetQty());
        for (MrpDailyPlanDetail row:planning.getDailyPlans(batch.getId()))
            if (!ProductionCapacityRules.MACHINE_OUTPUT.equals(row.getTerminalType()))
                facts.add("P|"+row.getFactoryCode()+"|"+row.getRootMaterialCode()+"|"+row.getTerminalMaterialCode()+"|"+row.getSourceMonth()+"|"+row.getScheduleDate()+"|"+row.getEffectiveQuantity());
        for (MrpInventoryAllocation row:allocations.selectByBatchId(batch.getId()))
            facts.add("A|"+row.getPoolId()+"|"+row.getDemandKey()+"|"+row.getAllocatedQty()+"|"+row.getRevision());
        for (MrpDailyPlanOverride row:overrides.selectByBatchId(batch.getId()))
            if (!ProductionCapacityRules.MACHINE_OUTPUT.equals(row.getTerminalType()))
                facts.add("O|"+row.getTerminalMaterialCode()+"|"+row.getScheduleDate()+"|"+row.getManualQuantity()+"|"+row.getRevision());
        facts.add("OPEN_ISSUES|"+issues.countByBatchId(batch.getId(),"OPEN",null));
        facts.add("PENDING_ALLOCATION|"+unallocated.countByRunIdAndStatus(run,"PENDING",null));
        facts.add("PENDING_POLICY|"+policies.countPendingDefaultExplosion(run));
        facts.add("OPEN_RISK|"+risks.countByBatchId(batch.getId(),run));
        facts.add("OPEN_FEEDBACK|"+feedback.countOpen(batch.getId()));
        Collections.sort(facts);
        return hash(schedule+"|"+String.join(";",facts));
    }

    private static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch (NoSuchAlgorithmException error) {throw new IllegalStateException(error);}
    }

    @Transactional(readOnly=true)
    public PageResult<MrpMaterialFeedback> feedbackPage(Long batchId,int page,int size) {
        access.requireBatch(batchId);
        RollingPlanningQueryService.validatePage(page,size);
        return new PageResult<>(feedback.page(batchId,(long)(page-1)*size,size),feedback.count(batchId),page,size);
    }

    @Transactional
    public MrpMaterialFeedback createFeedback(Long batchId, MrpMaterialFeedback input) {
        MrpCalcBatch batch=batches.selectForUpdate(batchId); access.require(batch);
        if (!Set.of("MATERIAL_REVIEW","READY").contains(batch.getStatus()) || batch.getActiveRunId()==null)
            throw new IllegalStateException("只有有效待发布结果可提交物料反馈");
        if (input==null || input.getReason()==null || input.getReason().isBlank() || input.getReason().length()>512
                || input.getAffectedQuantity()==null || input.getAffectedQuantity().signum()<=0
                || input.getSourceMonth()==null || input.getRootMaterialCode()==null || input.getTerminalMaterialCode()==null
                || input.getFactoryCode()==null)
            throw new IllegalArgumentException("请填写工厂、整机、末级物料、来源月、影响数量及原因");
        access.requireBatchFactory(batchId,input.getFactoryCode());
        boolean exists=demands.selectByRunId(batch.getActiveRunId()).stream().anyMatch(d ->
                Objects.equals(d.getFactoryCode(),input.getFactoryCode())
                && Objects.equals(d.getRootMaterialCode(),input.getRootMaterialCode())
                && Objects.equals(d.getTerminalMaterialCode(),input.getTerminalMaterialCode())
                && Objects.equals(d.getSourceMonth(),input.getSourceMonth())
                && !ProductionCapacityRules.MACHINE_OUTPUT.equals(d.getTerminalType()));
        if (!exists) throw new IllegalArgumentException("反馈物料不属于当前计算结果");
        input.setBatchId(batchId);input.setRunId(batch.getActiveRunId());input.setCreatedBy(CurrentUser.getUserId());
        feedback.insert(input);
        audit.log(input.getCreatedBy(),null,batch.getFactoryCode(),"CREATE_MATERIAL_FEEDBACK",
                "mrp_material_feedback",input.getId().toString(),null,input,null,null);
        return input;
    }

    @Transactional
    public MrpMaterialFeedback respond(Long batchId,Long id,String response) {
        MrpCalcBatch batch=batches.selectForUpdate(batchId);access.require(batch);
        MrpMaterialFeedback item=feedback.byId(id);
        if (item==null || !batchId.equals(item.getBatchId())) throw new IllegalArgumentException("物料反馈不存在");
        if (!Set.of("MATERIAL_REVIEW","READY").contains(batch.getStatus()) || !Objects.equals(batch.getActiveRunId(),item.getRunId()))
            throw new ConcurrentChangeException("排产结果已变化，请重新提交反馈");
        if (response==null || response.isBlank() || response.length()>512) throw new IllegalArgumentException("请填写处理结果");
        if (feedback.respond(id,response,CurrentUser.getUserId())!=1)
            throw new ConcurrentChangeException("反馈已由其他人处理，请刷新");
        audit.log(CurrentUser.getUserId(),null,batch.getFactoryCode(),"RESPOND_MATERIAL_FEEDBACK",
                "mrp_material_feedback",id.toString(),item,Map.of("response",response),null,null);
        return feedback.byId(id);
    }
}
