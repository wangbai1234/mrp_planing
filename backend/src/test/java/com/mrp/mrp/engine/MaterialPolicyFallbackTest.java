package com.mrp.mrp.engine;

import com.mrp.mrp.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialPolicyFallbackTest {
    private NetRequirementEngine engine(Map<String, MaterialPolicyDetail> policies) {
        Map<String, CategoryRoleDetail> roles = new HashMap<>();
        for (String code : List.of("A", "B", "C")) {
            CategoryRoleDetail role = new CategoryRoleDetail(); role.setRole("ASSEMBLY"); role.setEnabled(true); roles.put(code, role);
        }
        return new NetRequirementEngine(List.of(edge("A", "B", "2"), edge("B", "C", "3")), Map.of(), roles,
                policies, List.of(), Set.of("A", "B", "C"), "F1", null, 10L, 1L);
    }
    private BomSnapshotDetail edge(String parent, String child, String quantity) {
        BomSnapshotDetail edge = new BomSnapshotDetail(); edge.setParentCode(parent); edge.setChildCode(child);
        edge.setParentQty(BigDecimal.ONE); edge.setChildQty(new BigDecimal(quantity)); return edge;
    }
    private List<NetRequirementEngine.RootDemand> roots() {
        return List.of(new NetRequirementEngine.RootDemand("A", BigDecimal.TEN, LocalDate.of(2027, 2, 1)));
    }
    @Test void absentPolicyContinuesToLeafWithoutDecisionIssueOrNullSupply() {
        var result = engine(Map.of()).explode(roots());
        assertTrue(result.issues.isEmpty());
        assertEquals(List.of("A", "B", "C"), result.details.stream().map(MrpRequirementDetail::getCurrentMaterialCode).toList());
        assertEquals("C", result.terminalDemands.getFirst().getTerminalMaterialCode());
        assertEquals(0, new BigDecimal("60").compareTo(result.terminalDemands.getFirst().getQuantity()));
        assertEquals("UNMAINTAINED", result.terminalDemands.getFirst().getSupplyMode());
        assertTrue(result.details.stream().allMatch(d -> "EXPLODE".equals(d.getEffectiveExplosionPolicy()) && "DEFAULT".equals(d.getPolicySource())));
    }
    @Test void partialLegacyStopStillStopsEvenWhenSupplyIsMissing() {
        MaterialPolicyDetail stop = new MaterialPolicyDetail(); stop.setExplosionPolicy("STOP"); stop.setSupplyMode("UNMAINTAINED");
        var result = engine(Map.of("B", stop)).explode(roots());
        assertTrue(result.issues.isEmpty());
        assertEquals(List.of("A", "B"), result.details.stream().map(MrpRequirementDetail::getCurrentMaterialCode).toList());
        assertEquals("B", result.terminalDemands.getFirst().getTerminalMaterialCode());
        assertEquals("POLICY_STOP", result.details.getLast().getTerminalReason());
        assertEquals("CONFIGURED", result.details.getLast().getPolicySource());
        assertEquals("STOP", result.details.getLast().getEffectiveExplosionPolicy());
        assertEquals("UNMAINTAINED", result.terminalDemands.getFirst().getSupplyMode());
    }
    @Test void legacyUnmaintainedExplosionUsesDefaultEvenWithValidSupply() {
        MaterialPolicyDetail legacy = new MaterialPolicyDetail(); legacy.setSupplyMode("PURCHASED"); legacy.setExplosionPolicy("UNMAINTAINED");
        var result = engine(Map.of("B", legacy)).explode(roots());
        assertEquals("C", result.terminalDemands.getFirst().getTerminalMaterialCode());
        assertEquals("PURCHASED", result.details.get(1).getSupplyMode());
        assertEquals("DEFAULT", result.details.get(1).getPolicySource());
    }
}
