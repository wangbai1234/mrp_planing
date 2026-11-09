package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import java.util.*;

/** Versioned output and capacity semantics; historical batches retain their original basis. */
public final class ProductionCapacityRules {
    public static final String VERSION = "v5-shifted-business-weeks";
    public static final String ROLLING_V4 = "v4-rolling-business-weeks";
    public static final String WHOLE_MACHINE_V3 = "v3-whole-machine-capacity";
    public static final List<String> FACTORIES = List.of("爱培科", "永惠");
    public static final String MACHINE_OUTPUT = "MACHINE_OUTPUT";
    public static final String TERMINAL = "TERMINAL";
    private ProductionCapacityRules() {}
    public static boolean wholeMachine(MrpCalcBatch batch) {
        return batch != null && (rolling(batch) || WHOLE_MACHINE_V3.equals(batch.getRuleVersion()));
    }
    public static boolean rolling(MrpCalcBatch batch) { return batch != null && (VERSION.equals(batch.getRuleVersion()) || ROLLING_V4.equals(batch.getRuleVersion())); }
    public static boolean shiftedBusinessWeeks(MrpCalcBatch batch) { return batch != null && VERSION.equals(batch.getRuleVersion()); }
    public static List<String> factories(MrpCalcBatch batch) {
        return "ALL".equals(batch.getFactoryCode()) ? FACTORIES : List.of(batch.getFactoryCode());
    }
    public static boolean dailyCapacity(MrpCalcBatch batch) {
        return wholeMachine(batch) || (batch != null && "v2-daily-capacity".equals(batch.getRuleVersion()));
    }
    public static String basis(MrpCalcBatch batch) { return wholeMachine(batch) ? MACHINE_OUTPUT : TERMINAL; }
    public static String view(MrpCalcBatch batch, String requested) {
        String value = requested == null || requested.isBlank() ? basis(batch) : requested;
        if (!MACHINE_OUTPUT.equals(value) && !TERMINAL.equals(value)) throw new IllegalArgumentException("排产视图须为 MACHINE_OUTPUT 或 TERMINAL");
        return value;
    }
    public static boolean matches(String view, MrpDailyPlanDetail plan) {
        return MACHINE_OUTPUT.equals(view) == MACHINE_OUTPUT.equals(plan.getTerminalType());
    }
    /** Root net requirement is already after root inventory; BOM children never contribute output. */
    public static List<MrpTerminalDemand> machineDemands(List<MrpRequirementDetail> details) {
        List<MrpTerminalDemand> result = new ArrayList<>();
        for (var detail : details) {
            if (!Integer.valueOf(0).equals(detail.getLevelNo()) || detail.getNetQty() == null || detail.getNetQty().signum() <= 0) continue;
            if (detail.getNetQty().stripTrailingZeros().scale() > 0)
                throw new IllegalStateException("整机净需求必须为整数台：" + detail.getRootMaterialCode() + " / " + detail.getSourceMonth() + " / " + detail.getNetQty().toPlainString());
            var demand = new MrpTerminalDemand();
            demand.setRunId(detail.getRunId()); demand.setBatchId(detail.getBatchId()); demand.setFactoryCode(detail.getFactoryCode());
            demand.setRootMaterialCode(detail.getRootMaterialCode()); demand.setTerminalMaterialCode(detail.getRootMaterialCode());
            demand.setTerminalType(MACHINE_OUTPUT); demand.setSupplyMode("SELF_MADE"); demand.setSourceMonth(detail.getSourceMonth());
            demand.setQuantity(detail.getNetQty().setScale(0)); result.add(demand);
        }
        return result;
    }
}
