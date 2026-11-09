package com.mrp.mrp.engine;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.service.ProductionCapacityRules;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WholeMachineOutputTest {
    @Test void candidateMachineScheduleDoesNotWaitForComponentInventory() {
        var role = new CategoryRoleDetail(); role.setRole("FINISHED_GOOD"); role.setEnabled(true);
        var edge = edge("UNMAINTAINED-COMPONENT", "2");
        var engine = new NetRequirementEngine(List.of(edge), Map.of("ROOT|GOOD", new BigDecimal("30")),
                Map.of("ROOT", role), Map.of(), List.of(), Set.of("ROOT"),
                "爱培科", null, 10L, 1L, Set.of("ROOT"));
        var candidate = engine.explodeRoots(List.of(new NetRequirementEngine.RootDemand("ROOT",
                new BigDecimal("100"), LocalDate.of(2026, 10, 1))));
        assertEquals(1, candidate.details.size());
        assertTrue(candidate.issues.isEmpty());
        decimal("70", ProductionCapacityRules.machineDemands(candidate.details).getFirst().getQuantity());
        assertEquals(0, candidate.details.getFirst().getInventoryUsedQty().compareTo(new BigDecimal("30")));
    }
    @Test void materialStageExplodesConfirmedNetMachinesWithoutDeductingRootStockTwice() {
        Map<String,CategoryRoleDetail> roles = new HashMap<>();
        for (String code : List.of("ROOT", "A", "B")) {
            var role = new CategoryRoleDetail(); role.setRole("ASSEMBLY"); role.setEnabled(true); roles.put(code, role);
        }
        var engine = new NetRequirementEngine(List.of(edge("A","2"), edge("B","3")),
                Map.of("ROOT|GOOD",new BigDecimal("30")), roles, Map.of(), List.of(),
                Set.of("ROOT","A","B"), "爱培科", null, 10L, 1L);
        var result = engine.explodeNetRoots(List.of(new NetRequirementEngine.RootDemand("ROOT",
                new BigDecimal("70"), LocalDate.of(2026,10,1))));
        assertTrue(result.details.stream().noneMatch(item -> "ROOT".equals(item.getCurrentMaterialCode())));
        assertEquals(0, result.usedInventory.getOrDefault("ROOT|GOOD", BigDecimal.ZERO).compareTo(BigDecimal.ZERO));
        var quantities = new HashMap<String,BigDecimal>();
        result.terminalDemands.forEach(item -> quantities.merge(item.getTerminalMaterialCode(), item.getQuantity(), BigDecimal::add));
        decimal("140", quantities.get("A"));
        decimal("210", quantities.get("B"));
    }
    @Test void twoBomChildrenDoNotMultiplyOutputAndRootInventoryReducesMachineDemand() {
        var result = explode(Map.of("ROOT|GOOD",new BigDecimal("30")),Map.of(),"100");
        assertEquals(2,result.terminalDemands.size());
        var output = ProductionCapacityRules.machineDemands(result.details);
        assertEquals(1,output.size()); decimal("70",output.getFirst().getQuantity());
        assertEquals("ROOT",output.getFirst().getTerminalMaterialCode());
        assertEquals("MACHINE_OUTPUT",output.getFirst().getTerminalType());
        assertEquals(LocalDate.of(2026,10,1),output.getFirst().getSourceMonth());
    }
    @Test void childInventoryChangesOnlyPreparationsWhileOutputRemainsTheSame() {
        var zero = explode(Map.of(),Map.of(),"100");
        var stocked = explode(Map.of("A|GOOD",new BigDecimal("1000"),"B|GOOD",new BigDecimal("1000")),Map.of(),"100");
        assertTrue(stocked.terminalDemands.isEmpty());
        decimal("100",ProductionCapacityRules.machineDemands(stocked.details).getFirst().getQuantity());
        assertEquals(ProductionCapacityRules.machineDemands(zero.details).getFirst().getQuantity(),
                ProductionCapacityRules.machineDemands(stocked.details).getFirst().getQuantity());
    }
    @Test void purchasedLeavesAndRootStopStillProduceIndependentOutputDemand() {
        var purchased = explode(Map.of(),Map.of("A",policy("PURCHASED","STOP"),"B",policy("OUTSOURCED","STOP")),"100");
        assertTrue(purchased.terminalDemands.stream().noneMatch(d -> "SELF_MADE".equals(d.getSupplyMode())));
        decimal("100",ProductionCapacityRules.machineDemands(purchased.details).getFirst().getQuantity());
        var stopped = explode(Map.of("ROOT|GOOD",new BigDecimal("30")),Map.of("ROOT",policy("SELF_MADE","STOP")),"100");
        assertEquals(1,stopped.details.size()); decimal("70",ProductionCapacityRules.machineDemands(stopped.details).getFirst().getQuantity());
    }
    @Test void fractionalNetOutputCannotBeRoundedSilentlyAndFullyStockedRootHasNoOutput() {
        var fractional = explode(Map.of("ROOT|GOOD",new BigDecimal("0.5")),Map.of(),"100");
        var error = assertThrows(IllegalStateException.class,() -> ProductionCapacityRules.machineDemands(fractional.details));
        assertTrue(error.getMessage().contains("ROOT")); assertTrue(error.getMessage().contains("整数台"));
        assertTrue(ProductionCapacityRules.machineDemands(explode(Map.of("ROOT|GOOD",new BigDecimal("100")),Map.of(),"100").details).isEmpty());
    }
    private NetRequirementEngine.ExplosionResult explode(Map<String,BigDecimal> inventory,Map<String,MaterialPolicyDetail> policies,String quantity) {
        Map<String,CategoryRoleDetail> roles = new HashMap<>();
        for (String code : List.of("ROOT","A","B")) { var role = new CategoryRoleDetail(); role.setRole("ASSEMBLY"); role.setEnabled(true); roles.put(code,role); }
        var engine = new NetRequirementEngine(List.of(edge("A","2"),edge("B","3")),inventory,roles,policies,List.of(),Set.of("ROOT","A","B"),"爱培科",null,10L,1L);
        return engine.explode(List.of(new NetRequirementEngine.RootDemand("ROOT",new BigDecimal(quantity),LocalDate.of(2026,10,1))));
    }
    private BomSnapshotDetail edge(String child,String quantity) {
        var edge = new BomSnapshotDetail(); edge.setParentCode("ROOT"); edge.setChildCode(child); edge.setParentQty(BigDecimal.ONE); edge.setChildQty(new BigDecimal(quantity)); return edge;
    }
    private MaterialPolicyDetail policy(String supply,String explosion) { var p=new MaterialPolicyDetail(); p.setSupplyMode(supply); p.setExplosionPolicy(explosion); return p; }
    private void decimal(String expected,BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual)); }
}
