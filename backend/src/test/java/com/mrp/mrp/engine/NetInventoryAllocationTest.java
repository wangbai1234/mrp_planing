package com.mrp.mrp.engine;

import com.mrp.mrp.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NetInventoryAllocationTest {
    private static final LocalDate MONTH = LocalDate.of(2026, 10, 1);

    @Test void layerNettingMatchesConfirmed1000UnitGoldenExample() {
        var engine = engine(List.of(edge("A","B","2"),edge("B","C","3"),edge("C","D","1")),
                Map.of("A|GOOD",bd("500"),"B|GOOD",bd("300"),"C|GOOD",bd("100"),"D|GOOD",bd("1000")), List.of());
        var result=engine.explode(List.of(root("A","1000")));
        assertTrue(result.issues.isEmpty());
        assertEquals(1,result.terminalDemands.size());
        decimal("1000",result.terminalDemands.getFirst().getQuantity());
        assertEquals(List.of("500","700","2000","1000"), result.details.stream().map(d->d.getNetQty().stripTrailingZeros().toPlainString()).toList());
    }

    @Test void manualAllocationIsConsumedExactlyOnceAndTracedAsInventoryUsed() {
        var result=engine(List.of(),Map.of("A|GOOD",bd("100")),List.of(allocation("A","A","30")))
                .explode(List.of(root("A","80")));
        decimal("30",result.details.getFirst().getInventoryUsedQty());
        decimal("70",result.details.getFirst().getInventoryAfterQty());
        decimal("50",result.terminalDemands.getFirst().getQuantity());
        decimal("30",result.usedInventory.get("A|GOOD"));
    }

    @Test void laterManualReservationCannotBeConsumedByEarlierAutomaticDemand() {
        var result=engine(List.of(edge("A","D","1"),edge("B","D","1")),Map.of("D|GOOD",bd("100")),
                List.of(allocation("B","D","80"))).explode(List.of(root("A","100"),root("B","100")));
        var d=result.details.stream().filter(row->"D".equals(row.getCurrentMaterialCode())).toList();
        decimal("20",d.get(0).getInventoryUsedQty()); decimal("80",d.get(0).getNetQty());
        decimal("80",d.get(1).getInventoryUsedQty()); decimal("20",d.get(1).getNetQty());
        decimal("100",result.usedInventory.get("D|GOOD"));
    }

    @Test void sharedMaterialAllocationBudgetIsNotRepeatedAcrossBomPaths() {
        var result=engine(List.of(edge("A","B","1"),edge("A","C","1"),edge("B","D","1"),edge("C","D","1")),
                Map.of("D|GOOD",bd("100")),List.of(allocation("A","D","30")))
                .explode(List.of(root("A","50")));
        decimal("30",result.usedInventory.get("D|GOOD"));
        decimal("70",result.terminalDemands.stream().map(MrpTerminalDemand::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    @Test void allocationAboveSafetyAdjustedInventoryIsRejected() {
        assertThrows(IllegalStateException.class,()->engine(List.of(),Map.of("A|GOOD",bd("10")),
                List.of(allocation("A","A","11"))).explode(List.of(root("A","100"))));
    }

    private NetRequirementEngine engine(List<BomSnapshotDetail> edges,Map<String,BigDecimal> inventory,List<MrpInventoryAllocation> allocations) {
        Map<String,CategoryRoleDetail> roles=new HashMap<>();
        for(String material:List.of("A","B","C","D")){var role=new CategoryRoleDetail();role.setRole("ASSEMBLY");role.setEnabled(true);roles.put(material,role);}
        return new NetRequirementEngine(edges,inventory,roles,Map.of(),allocations,roles.keySet(),"爱培科",null,1L,1L);
    }
    private MrpInventoryAllocation allocation(String root,String material,String quantity){var a=new MrpInventoryAllocation();a.setDemandKey(root+"|"+material+"|"+MONTH);a.setAllocatedQty(bd(quantity));return a;}
    private NetRequirementEngine.RootDemand root(String material,String qty){return new NetRequirementEngine.RootDemand(material,bd(qty),MONTH);}
    private BomSnapshotDetail edge(String parent,String child,String qty){var e=new BomSnapshotDetail();e.setParentCode(parent);e.setChildCode(child);e.setParentQty(BigDecimal.ONE);e.setChildQty(bd(qty));return e;}
    private static BigDecimal bd(String v){return new BigDecimal(v);}
    private void decimal(String expected,BigDecimal actual){assertEquals(0,bd(expected).compareTo(actual));}
}
