package com.mrp.mrp.engine;

import com.mrp.mrp.domain.BomSnapshotDetail;
import com.mrp.mrp.domain.CategoryRoleDetail;
import com.mrp.mrp.domain.MaterialPolicyDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingInputIssuesTest {
    @Test
    void missingMachineInventoryWaitsForMaterialReviewWithoutBlockingCandidateSchedule() {
        var role = new CategoryRoleDetail();
        role.setRole("FINISHED_GOOD"); role.setEnabled(true);
        var missing = new NetRequirementEngine(List.of(), Map.of(), Map.of("ROOT", role), Map.of(),
                List.of(), Set.of(), "F1", null, null, 1L, Set.of("ROOT"));
        assertTrue(missing.explodeRoots(roots()).issues.isEmpty());
        var issues = missing.explodeNetRoots(roots()).issues;
        assertEquals(1, issues.size());
        assertEquals("INVENTORY_MISSING", issues.getFirst().getIssueType());
        assertEquals("ROOT", issues.getFirst().getScopeKey());

        var explicitZero = new NetRequirementEngine(List.of(), Map.of("ROOT|GOOD", BigDecimal.ZERO),
                Map.of("ROOT", role), Map.of(), List.of(), Set.of("ROOT"), "F1", null, null, 1L, Set.of("ROOT"));
        assertTrue(explicitZero.explodeNetRoots(roots()).issues.isEmpty());
    }

    @Test
    void multiFactoryIssueKeysAreDistinctAndStableWithoutChangingHistoricalKeys() {
        var first = new NetRequirementEngine(List.of(), Map.of(), Map.of(), Map.of(),
                List.of(), Set.of(), "F1", null, null, 1L, Set.of("ROOT"), true)
                .explodeRoots(roots()).issues.getFirst();
        var second = new NetRequirementEngine(List.of(), Map.of(), Map.of(), Map.of(),
                List.of(), Set.of(), "F2", null, null, 1L, Set.of("ROOT"), true)
                .explodeRoots(roots()).issues.getFirst();
        var repeat = new NetRequirementEngine(List.of(), Map.of(), Map.of(), Map.of(),
                List.of(), Set.of(), "F1", null, null, 1L, Set.of("ROOT"), true)
                .explodeRoots(roots()).issues.getFirst();
        var legacy = new NetRequirementEngine(List.of(), Map.of(), Map.of(), Map.of(),
                List.of(), Set.of(), "F1", null, null, 1L, Set.of("ROOT"))
                .explodeRoots(roots()).issues.getFirst();
        assertEquals("F1",first.getFactoryCode());
        assertTrue(!first.getIssueKey().equals(second.getIssueKey()));
        assertEquals(first.getIssueKey(),repeat.getIssueKey());
        assertEquals("ROOT|ROOT|ROLE_MISSING",legacy.getIssueKey());
    }
    @Test
    void missingInventoryAtIntermediateNodeStillChecksItsChild() {
        var parent = new BomSnapshotDetail();
        parent.setParentCode("ROOT"); parent.setChildCode("MIDDLE");
        parent.setParentQty(BigDecimal.ONE); parent.setChildQty(BigDecimal.ONE);
        var child = new BomSnapshotDetail();
        child.setParentCode("MIDDLE"); child.setChildCode("LEAF");
        child.setParentQty(BigDecimal.ONE); child.setChildQty(BigDecimal.ONE);
        var role = new CategoryRoleDetail();
        role.setRole("RAW_MATERIAL"); role.setEnabled(true);
        var result = new NetRequirementEngine(List.of(parent, child), Map.of(),
                Map.of("ROOT", role, "MIDDLE", role, "LEAF", role), Map.of(),
                List.of(), Set.of("ROOT"), "F1", null, null, 1L, Set.of("ROOT", "MIDDLE", "LEAF"))
                .explode(roots());
        assertEquals(List.of("MIDDLE", "LEAF"), result.issues.stream()
                .filter(issue -> "INVENTORY_MISSING".equals(issue.getIssueType()))
                .map(issue -> issue.getScopeKey()).toList());
        assertTrue(result.details.stream().anyMatch(detail -> "LEAF".equals(detail.getCurrentMaterialCode())));
    }

    @Test
    void missingMaterialMasterAndInventoryAreBothReportedAtSameBomNode() {
        var result = engine(Set.of("ROOT")).explode(roots());

        assertEquals(List.of("MATERIAL_MISSING", "INVENTORY_MISSING"), result.issues.stream()
                .map(issue -> issue.getIssueType()).toList());
        assertEquals("MATERIAL_MISSING", result.details.getLast().getTerminalReason());
    }

    @Test
    void presentMasterWithoutRoleAndInventoryReportsBothSpecificIssues() {
        var result = engine(Set.of("ROOT", "BOM-ONLY")).explode(roots());

        assertEquals(List.of("ROLE_MISSING", "INVENTORY_MISSING"), result.issues.stream()
                .map(issue -> issue.getIssueType()).toList());
    }

    @Test
    void missingRoleDoesNotHideLowerBomInventoryIssues() {
        var first = new BomSnapshotDetail(); first.setParentCode("ROOT"); first.setChildCode("BOM-ONLY");
        first.setParentQty(BigDecimal.ONE); first.setChildQty(BigDecimal.ONE);
        var second = new BomSnapshotDetail(); second.setParentCode("BOM-ONLY"); second.setChildCode("DEEP");
        second.setParentQty(BigDecimal.ONE); second.setChildQty(BigDecimal.ONE);
        var rootRole = new CategoryRoleDetail(); rootRole.setRole("FINISHED_GOOD"); rootRole.setEnabled(true);
        var result = new NetRequirementEngine(List.of(first, second), Map.of(), Map.of("ROOT", rootRole), Map.of(),
                List.of(), Set.of("ROOT"), "F1", null, null, 1L, Set.of("ROOT"))
                .explode(roots());
        assertEquals(List.of("BOM-ONLY", "DEEP"), result.issues.stream()
                .filter(issue -> "INVENTORY_MISSING".equals(issue.getIssueType()))
                .map(issue -> issue.getScopeKey()).toList());
    }

    @Test
    void stopPolicyPreventsMissingRoleNodeFromReportingItsDescendants() {
        var first = new BomSnapshotDetail(); first.setParentCode("ROOT"); first.setChildCode("STOP-NODE");
        first.setParentQty(BigDecimal.ONE); first.setChildQty(BigDecimal.ONE);
        var second = new BomSnapshotDetail(); second.setParentCode("STOP-NODE"); second.setChildCode("DEEP");
        second.setParentQty(BigDecimal.ONE); second.setChildQty(BigDecimal.ONE);
        var rootRole = new CategoryRoleDetail(); rootRole.setRole("FINISHED_GOOD"); rootRole.setEnabled(true);
        var stop = new MaterialPolicyDetail(); stop.setExplosionPolicy("STOP");
        var result = new NetRequirementEngine(List.of(first, second), Map.of(),
                Map.of("ROOT", rootRole), Map.of("STOP-NODE", stop),
                List.of(), Set.of("ROOT"), "F1", null, null, 1L, Set.of("ROOT"))
                .explode(roots());
        assertEquals(List.of("STOP-NODE"), result.issues.stream()
                .filter(issue -> "INVENTORY_MISSING".equals(issue.getIssueType()))
                .map(issue -> issue.getScopeKey()).toList());
        assertTrue(result.details.stream().noneMatch(detail -> "DEEP".equals(detail.getCurrentMaterialCode())));
    }

    private NetRequirementEngine engine(Set<String> existingMaterials) {
        var edge = new BomSnapshotDetail();
        edge.setParentCode("ROOT");
        edge.setChildCode("BOM-ONLY");
        edge.setParentQty(BigDecimal.ONE);
        edge.setChildQty(BigDecimal.ONE);
        var role = new CategoryRoleDetail();
        role.setRole("FINISHED_GOOD");
        role.setEnabled(true);
        return new NetRequirementEngine(List.of(edge), Map.of(), Map.of("ROOT", role), Map.of(),
                List.of(), Set.of("ROOT"), "F1", null, null, 1L, existingMaterials);
    }

    private List<NetRequirementEngine.RootDemand> roots() {
        return List.of(new NetRequirementEngine.RootDemand("ROOT", BigDecimal.TEN, LocalDate.of(2026, 10, 1)));
    }
}
