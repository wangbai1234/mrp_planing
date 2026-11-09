package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.repository.MrpDailyPlanDetailMapper;
import com.mrp.mrp.repository.RollingPlanningMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact read projection only; the caller supplies a consistent transaction snapshot. */
@Service
public class AutomaticCapacityReviewService {
    private final MrpDailyPlanDetailMapper facts;
    private final RollingPlanningMapper overrides;

    public AutomaticCapacityReviewService(MrpDailyPlanDetailMapper facts, RollingPlanningMapper overrides) {
        this.facts = facts;
        this.overrides = overrides;
    }

    /** Null retains the established effective-plan path for overrides and historical terminal rules. */
    public Projection read(MrpCalcBatch batch) {
        if (!ProductionCapacityRules.wholeMachine(batch) || batch.getActiveRunId() == null
                || overrides.hasOverrides(batch.getId())) return null;
        var collector = new Collector();
        facts.streamCapacityFacts(batch.getActiveRunId(), context -> collector.accept(context.getResultObject()));
        return collector.finish();
    }

    private record FactoryDay(String factory, LocalDate date) {}
    private static final class Collector {
        private final List<String> identities = new ArrayList<>();
        private final Map<FactoryDay, BigDecimal> loads = new LinkedHashMap<>();
        private LocalDate start;
        private LocalDate end;

        void accept(MrpDailyPlanDetail plan) {
            identities.add(BatchOrchestratorService.checksumFact(plan));
            LocalDate date = plan.getScheduleDate();
            if (start == null || date.isBefore(start)) start = date;
            if (end == null || date.isAfter(end)) end = date;
            if (ProductionCapacityRules.MACHINE_OUTPUT.equals(plan.getTerminalType()))
                loads.merge(new FactoryDay(plan.getFactoryCode(), date), plan.getEffectiveQuantity(), BigDecimal::add);
        }

        Projection finish() {
            List<MrpDailyPlanDetail> machineLoads = new ArrayList<>();
            loads.forEach((key, quantity) -> {
                var plan = new MrpDailyPlanDetail();
                plan.setFactoryCode(key.factory());
                plan.setScheduleDate(key.date());
                plan.setTerminalType(ProductionCapacityRules.MACHINE_OUTPUT);
                plan.setEffectiveQuantity(quantity);
                machineLoads.add(plan);
            });
            return new Projection(BatchOrchestratorService.checksumFacts(identities), machineLoads, start, end);
        }
    }

    public record Projection(String checksum, List<MrpDailyPlanDetail> machineLoads, LocalDate start, LocalDate end) {}
}
