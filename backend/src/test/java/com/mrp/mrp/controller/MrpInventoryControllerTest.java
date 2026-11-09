package com.mrp.mrp.controller;

import com.mrp.mrp.repository.MrpInventoryAllocationMapper;
import com.mrp.mrp.repository.MrpInventoryPoolMapper;
import com.mrp.mrp.service.InventoryNormalizationService;
import com.mrp.mrp.service.PlanningFactoryAccessService;
import com.mrp.mrp.service.StockRiskService;
import com.mrp.inventory.service.InventorySnapshotAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.math.BigDecimal;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MrpInventoryControllerTest {

    @Mock private InventoryNormalizationService normalizationService;
    @Mock private InventorySnapshotAccessService snapshotAccess;
    @Mock private StockRiskService stockRiskService;
    @Mock private PlanningFactoryAccessService factoryAccess;
    @Mock private MrpInventoryPoolMapper poolMapper;
    @Mock private MrpInventoryAllocationMapper allocationMapper;

    private MrpInventoryController controller;

    @BeforeEach
    void setUp() {
        controller = new MrpInventoryController(normalizationService, snapshotAccess, stockRiskService,
                factoryAccess, poolMapper, allocationMapper);
    }

    @Test
    void poolAllocationDetailsRequireAccessToTheirOwningPool() {
        controller.getAllocationsByPool(41L);

        verify(factoryAccess).requireResource("pool", 41L);
        verify(normalizationService).getAllocationsByPool(41L);
    }

    @Test
    void batchAllocationDetailsRequireAccessToTheBatch() {
        controller.getAllocationsByBatch(12L);

        verify(factoryAccess).requireBatch(12L);
        verify(normalizationService).getAllocationsByBatch(12L);
    }

    @Test
    void poolDetailRequiresAccessToItsOwningPool() {
        controller.getPool(41L);

        verify(factoryAccess).requireResource("pool", 41L);
        verify(normalizationService).getPoolById(41L);
    }

    @Test
    void updatingAllocationRequiresAccessBeforeMutation() {
        controller.updateAllocation(73L, new BigDecimal("12.5"), 2, "test adjustment");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(factoryAccess, normalizationService);
        order.verify(factoryAccess).requireResource("allocation", 73L);
        order.verify(normalizationService).updateAllocation(73L, new BigDecimal("12.5"), 2,
                "test adjustment");
    }

    @Test
    void resettingAllocationRequiresAccessBeforeMutation() {
        controller.resetAllocation(73L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(factoryAccess, normalizationService);
        order.verify(factoryAccess).requireResource("allocation", 73L);
        order.verify(normalizationService).resetAllocation(73L);
    }
}
