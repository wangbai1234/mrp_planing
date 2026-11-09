package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpTerminalDemand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MrpCalculationServiceDemandMergeTest {
    @Test
    void mergesRepeatedBomPathsWithoutMixingFactoriesOrMonths() {
        MrpTerminalDemand first = demand("永惠", "A", "X", "2026-10-01", "1.250000");
        MrpTerminalDemand second = demand("永惠", "A", "X", "2026-10-01", "2.500000");
        MrpTerminalDemand otherFactory = demand("爱培科", "A", "X", "2026-10-01", "3.000000");
        MrpTerminalDemand otherMonth = demand("永惠", "A", "X", "2026-11-01", "4.000000");

        List<MrpTerminalDemand> merged = MrpCalculationService.mergeTerminalDemands(
                List.of(first, second, otherFactory, otherMonth));

        assertEquals(3, merged.size());
        assertEquals(new BigDecimal("3.750000"), merged.get(0).getQuantity());
        assertEquals(new BigDecimal("3.000000"), merged.get(1).getQuantity());
        assertEquals(new BigDecimal("4.000000"), merged.get(2).getQuantity());
        assertEquals(new BigDecimal("1.250000"), first.getQuantity());
    }

    private static MrpTerminalDemand demand(String factory, String root, String terminal,
                                             String month, String quantity) {
        MrpTerminalDemand demand = new MrpTerminalDemand();
        demand.setRunId(1L);
        demand.setBatchId(106L);
        demand.setFactoryCode(factory);
        demand.setRootMaterialCode(root);
        demand.setTerminalMaterialCode(terminal);
        demand.setTerminalType("RAW_MATERIAL");
        demand.setSupplyMode("UNMAINTAINED");
        demand.setSourceMonth(LocalDate.parse(month));
        demand.setQuantity(new BigDecimal(quantity));
        return demand;
    }
}
