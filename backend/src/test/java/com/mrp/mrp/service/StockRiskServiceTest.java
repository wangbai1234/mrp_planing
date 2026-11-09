package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpInventoryPool;
import com.mrp.mrp.domain.MrpRequirementDetail;
import com.mrp.mrp.domain.MrpStockRisk;
import com.mrp.mrp.domain.StockPolicyDetail;
import com.mrp.mrp.repository.MrpStockRiskMapper;
import com.mrp.mrp.repository.Phase2ScopeConfigMapper;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockRiskServiceTest {

    @Mock private MrpStockRiskMapper riskMapper;
    @Mock private Phase2ScopeConfigMapper scopeConfigMapper;
    @Mock private InventoryNormalizationService inventoryService;
    @Mock private MrpCalcBatchMapper batchMapper;
    @Mock private AuditService auditService;

    @Test
    void classifiesSafetyWarningAndMissingPolicyWithoutBlocking() {
        MrpCalcBatch batch = new MrpCalcBatch();
        batch.setId(21L);
        batch.setFactoryCode("F001");
        batch.setStockPolicyVersionId(7L);

        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(
                policy("YH", "SAFETY", "30", "80"),
                policy("YH", "WARNING", "10", "40"),
                policy("YH", "SAFE", "5", "20"),
                policy("YH", "ZERO-LINE", "0", "0")));
        when(inventoryService.getPoolsByBatch(21L)).thenReturn(List.of(
                pool("YH", "SAFETY", "100"),
                pool("YH", "WARNING", "100"),
                pool("YH", "SAFE", "100"),
                pool("YH", "ZERO-LINE", "0")));

        List<MrpStockRisk> risks = service().evaluate(batch, 9L, List.of(
                detail("ROOT-A", "SAFETY", "80"),
                detail("ROOT-A", "WARNING", "70"),
                detail("ROOT-B", "WARNING", "0"),
                detail("ROOT-A", "SAFE", "10"),
                detail("ROOT-A", "UNMAINTAINED", "0"),
                detail("ROOT-B", "UNMAINTAINED", "0")));

        assertEquals(List.of("SAFETY_STOCK_BREACH", "STOCK_POLICY_MISSING", "WARNING_STOCK_BREACH"),
                risks.stream().map(MrpStockRisk::getRiskType).sorted().toList());

        MrpStockRisk safety = risk(risks, "SAFETY");
        assertEquals(new BigDecimal("20"), safety.getRemainingQty());
        assertEquals("ROOT-A", safety.getRootMaterialCodes());

        MrpStockRisk warning = risk(risks, "WARNING");
        assertEquals(new BigDecimal("30"), warning.getRemainingQty());
        assertEquals("ROOT-A,ROOT-B", warning.getRootMaterialCodes());

        MrpStockRisk missing = risk(risks, "UNMAINTAINED");
        assertEquals("STOCK_POLICY_MISSING", missing.getRiskType());
        assertEquals("ROOT-A,ROOT-B", missing.getRootMaterialCodes());
    }

    @Test
    void delimiterInWarehouseAndMaterialCannotBorrowAnotherPolicy() {
        var batch = new MrpCalcBatch();batch.setId(21L);batch.setStockPolicyVersionId(7L);
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(
                policy("WH|A", "B", "5", "10")));
        when(inventoryService.getPoolsByBatch(21L)).thenReturn(List.of(
                pool("WH|A", "B", "8"), pool("WH", "A|B", "8")));
        var risks=service().evaluate(batch,9L,List.of(detail("ROOT-1","B","0"),detail("ROOT-2","A|B","0")));
        assertEquals(2,risks.size());
        assertEquals("WARNING_STOCK_BREACH",risk(risks,"B").getRiskType());
        assertEquals("WH|A",risk(risks,"B").getWarehouseCode());
        assertEquals("STOCK_POLICY_MISSING",risk(risks,"A|B").getRiskType());
        assertEquals("ROOT-2",risk(risks,"A|B").getRootMaterialCodes());
    }

    @Test
    void factoryAndMaterialIdentifiersCannotMergeUsageOrSources() {
        var batch=new MrpCalcBatch();batch.setId(21L);batch.setStockPolicyVersionId(7L);
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(
                policy("WH1","B","1","3"),policy("WH2","A|B","1","3")));
        var first=pool("WH1","B","10");first.setFactoryCode("F|A");
        var second=pool("WH2","A|B","10");second.setFactoryCode("F");
        when(inventoryService.getPoolsByBatch(21L)).thenReturn(List.of(first,second));
        var a=detail("ROOT-1","B","8");a.setFactoryCode("F|A");
        var b=detail("ROOT-2","A|B","2");b.setFactoryCode("F");
        var risks=service().evaluate(batch,9L,List.of(a,b));
        assertEquals(1,risks.size());
        assertEquals("B",risks.getFirst().getMaterialCode());
        assertEquals(new BigDecimal("2"),risks.getFirst().getRemainingQty());
        assertEquals("ROOT-1",risks.getFirst().getRootMaterialCodes());
    }

    @Test
    void zeroThresholdPolicyCountsAsMaintainedOnlyInItsOwnFactory() {
        var batch=new MrpCalcBatch();batch.setId(21L);batch.setStockPolicyVersionId(7L);
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(policy("WH1","M","0","0")));
        var other=pool("WH2","M","0");other.setFactoryCode("F002");
        when(inventoryService.getPoolsByBatch(21L)).thenReturn(List.of(pool("WH1","M","0"),other));
        var detailOther=detail("ROOT-OTHER","M","0");detailOther.setFactoryCode("F002");
        var risks=service().evaluate(batch,9L,List.of(detail("ROOT-OWN","M","0"),detailOther));
        assertEquals(1,risks.size());assertEquals("STOCK_POLICY_MISSING",risks.getFirst().getRiskType());
        assertEquals("ROOT-OTHER",risks.getFirst().getRootMaterialCodes());
    }

    @Test
    void largeMaintainedDatasetDoesNotRepeatedlyTraverseEveryInventoryPool() {
        var batch=new MrpCalcBatch();batch.setId(21L);batch.setStockPolicyVersionId(7L);
        var policies=new ArrayList<StockPolicyDetail>();var pools=new ArrayList<MrpInventoryPool>();
        var details=new ArrayList<MrpRequirementDetail>();var reads=new java.util.concurrent.atomic.AtomicInteger();
        for(int i=0;i<2000;i++) {
            String code="SCALE-"+i;policies.add(policy("WH",code,"0","0"));
            var pool=new MrpInventoryPool(){@Override public String getFactoryCode(){reads.incrementAndGet();return "F001";}};
            pool.setWarehouseCode("WH");pool.setMaterialCode(code);pool.setAvailableQty(BigDecimal.ZERO);pools.add(pool);
            details.add(detail("ROOT",code,"0"));
        }
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(policies);
        when(inventoryService.getPoolsByBatch(21L)).thenReturn(pools);
        assertEquals(List.of(),service().evaluate(batch,9L,details));
        org.junit.jupiter.api.Assertions.assertTrue(reads.get()<=6000,
                "Pool accesses must scale with pools, not materials times pools: "+reads.get());
    }

    @Test
    void splitStageUsesWarehouseAllocationInsteadOfRepeatingFactoryUsage() {
        var batch = new MrpCalcBatch();
        batch.setId(21L);
        batch.setRuleVersion(ProductionCapacityRules.VERSION);
        batch.setStockPolicyVersionId(7L);
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(
                policy("WH1", "COMP", "20", "30"),
                policy("WH2", "COMP", "20", "30")));
        var first = pool("WH1", "COMP", "100");
        first.setAllocatedQty(new BigDecimal("85"));
        var second = pool("WH2", "COMP", "100");
        second.setAllocatedQty(new BigDecimal("5"));
        when(inventoryService.getPoolsByBatch(21L)).thenReturn(List.of(first, second));

        var risks = service().evaluate(batch, 9L, List.of(detail("ROOT", "COMP", "90")));
        assertEquals(1, risks.size());
        assertEquals("WH1", risks.getFirst().getWarehouseCode());
        assertEquals(new BigDecimal("85"), risks.getFirst().getUsedQty());
        assertEquals(new BigDecimal("15"), risks.getFirst().getRemainingQty());
    }

    @Test
    void missingPolicyInOneWarehouseRemainsVisibleWhenAnotherWarehouseIsMaintained() {
        var batch=new MrpCalcBatch(); batch.setId(21L); batch.setStockPolicyVersionId(7L);
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(policy("WH1","COMP","0","0")));
        when(inventoryService.getPoolsByBatch(21L)).thenReturn(List.of(
                pool("WH1","COMP","5"),pool("WH2","COMP","5")));
        var risks=service().evaluate(batch,9L,List.of(detail("ROOT","COMP","0")));
        assertEquals(1,risks.size());
        assertEquals("STOCK_POLICY_MISSING",risks.getFirst().getRiskType());
        assertEquals("WH2",risks.getFirst().getWarehouseCode());
        assertEquals("F001",risks.getFirst().getFactoryCode());
    }

    private StockRiskService service() {
        return new StockRiskService(riskMapper, scopeConfigMapper, inventoryService, batchMapper, auditService);
    }

    private MrpStockRisk risk(List<MrpStockRisk> risks, String material) {
        return risks.stream().filter(item -> material.equals(item.getMaterialCode())).findFirst().orElseThrow();
    }

    private StockPolicyDetail policy(String warehouse, String material, String safety, String warning) {
        StockPolicyDetail detail = new StockPolicyDetail();
        detail.setWarehouseCode(warehouse);
        detail.setMaterialCode(material);
        detail.setSafetyStockQty(new BigDecimal(safety));
        detail.setWarningStockQty(new BigDecimal(warning));
        return detail;
    }

    private MrpInventoryPool pool(String warehouse, String material, String available) {
        MrpInventoryPool pool = new MrpInventoryPool();
        pool.setFactoryCode("F001");
        pool.setWarehouseCode(warehouse);
        pool.setMaterialCode(material);
        pool.setAvailableQty(new BigDecimal(available));
        return pool;
    }

    private MrpRequirementDetail detail(String root, String material, String used) {
        MrpRequirementDetail detail = new MrpRequirementDetail();
        detail.setRootMaterialCode(root);
        detail.setFactoryCode("F001");
        detail.setCurrentMaterialCode(material);
        detail.setInventoryUsedQty(new BigDecimal(used));
        return detail;
    }
}
