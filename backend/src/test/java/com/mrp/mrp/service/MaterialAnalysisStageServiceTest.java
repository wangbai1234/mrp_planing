package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpInventoryAllocation;
import com.mrp.mrp.domain.MrpInventoryPool;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialAnalysisStageServiceTest {
    @Test void rootAndDescendantWithSameCodeHaveSeparateAllocationScopes() {
        var pool = new MrpInventoryPool();
        pool.setId(10L);
        pool.setMaterialCode("ROOT");
        var root = allocation("ROOT", "ROOT", "8");
        var descendant = allocation("ROOT", "ROOT:COMP:ROOT", "3");
        assertFalse(MaterialAnalysisStageService.isMaterialAllocation(root, pool));
        assertTrue(MaterialAnalysisStageService.isMaterialAllocation(descendant, pool));
        assertEquals(new BigDecimal("8"), MaterialAnalysisStageService.machineAllocatedByPool(
                List.of(root, descendant), Map.of(10L, pool)).get(10L));
    }

    @Test void missingPoolFailsInsteadOfSilentlyDroppingReservedMachineStock() {
        var root = allocation("ROOT", "ROOT", "8");
        assertThrows(IllegalStateException.class, () ->
                MaterialAnalysisStageService.machineAllocatedByPool(List.of(root), Map.of()));
    }

    private MrpInventoryAllocation allocation(String root, String path, String quantity) {
        var row = new MrpInventoryAllocation();
        row.setPoolId(10L);
        row.setRootMaterialCode(root);
        row.setDemandKey(root + "|ROOT|" + LocalDate.of(2026, 10, 1));
        row.setSourceMonth(LocalDate.of(2026, 10, 1));
        row.setPathHash(path);
        row.setAllocatedQty(new BigDecimal(quantity));
        return row;
    }
}
