package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.*;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Uses the same pending criteria as the workspace badges; run again under the publishing lock. */
@Service
public class PlanningReleaseValidationService {
    private final MrpDecisionIssueMapper issues;
    private final MrpUnallocatedQuantityMapper unallocated;
    private final MrpMaterialPolicyNoticeMapper policies;
    private final MrpStockRiskMapper risks;
    private final PlanningCalendarValidationService calendars;
    public PlanningReleaseValidationService(MrpDecisionIssueMapper issues, MrpUnallocatedQuantityMapper unallocated,
            MrpMaterialPolicyNoticeMapper policies, MrpStockRiskMapper risks, PlanningCalendarValidationService calendars) {
        this.issues = issues; this.unallocated = unallocated; this.policies = policies; this.risks = risks; this.calendars = calendars;
    }
    public void requirePublishable(MrpCalcBatch batch) {
        if (batch.getActiveRunId() == null) throw new IllegalStateException("无法发布：尚无计算结果");
        List<String> pending = new ArrayList<>();
        add(pending, "待处理异常", issues.countByBatchId(batch.getId(), "OPEN", null));
        add(pending, "待分配需求", unallocated.countByRunIdAndStatus(batch.getActiveRunId(), "PENDING", null));
        add(pending, "默认下钻待确认", policies.countPendingDefaultExplosion(batch.getActiveRunId()));
        add(pending, "库存风险待处理", risks.countByBatchId(batch.getId(), batch.getActiveRunId()));
        var calendar = calendars.check(batch, false);
        if (calendar.total() > 0) {
            String example = calendar.missingDays().isEmpty() ? String.join("、", calendar.configurationIssues())
                    : calendar.missingDays().getFirst().factoryCode() + " " + calendar.missingDays().getFirst().date();
            pending.add("生产日历或业务周配置缺失 " + calendar.total() + " 项（" + example + "）");
        }
        if (!pending.isEmpty()) throw new IllegalStateException("无法发布：" + String.join("；", pending) + "。请先处理异常与明细中的红点");
    }
    private void add(List<String> pending, String label, int count) { if (count > 0) pending.add(label + " " + count + " 项"); }
}
