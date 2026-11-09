package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpDailyPlanDetail;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.lang.management.ManagementFactory;
import static org.junit.jupiter.api.Assertions.*;

class EffectivePlanChecksumTest {
    private MrpDailyPlanDetail fact(String material, String qty) {
        var p = new MrpDailyPlanDetail();
        p.setFactoryCode("爱培科"); p.setRootMaterialCode("ROOT");
        p.setTerminalMaterialCode(material); p.setTerminalType("RAW_MATERIAL");
        p.setSupplyMode("SELF_MADE"); p.setPathGroupKey(material);
        p.setSourceMonth(LocalDate.of(2026, 10, 1));
        p.setSourceDate(LocalDate.of(2026, 10, 21));
        p.setScheduleDate(LocalDate.of(2026, 10, 14)); p.setIsProductionDay(true);
        p.setSystemQuantity(new BigDecimal("1200"));
        p.setEffectiveQuantity(new BigDecimal(qty));
        return p;
    }

    @Test void unchangedFactsIgnoreOrderAndDecimalScale() {
        assertEquals(BatchOrchestratorService.effectiveChecksum(List.of(fact("A", "1000"), fact("B", "900"))),
                BatchOrchestratorService.effectiveChecksum(List.of(fact("B", "900.000000"), fact("A", "1000.0"))));
    }

    @Test void sameDaySameTotalTransferStillInvalidatesAcknowledgment() {
        assertNotEquals(BatchOrchestratorService.effectiveChecksum(List.of(fact("A", "1000"), fact("B", "900"))),
                BatchOrchestratorService.effectiveChecksum(List.of(fact("A", "900"), fact("B", "1000"))));
    }

    @Test void effectiveChangeAndSourceAttributionChangeAreDetected() {
        var p = fact("A", "1000");
        String before = BatchOrchestratorService.effectiveChecksum(List.of(p));
        p.setEffectiveQuantity(new BigDecimal("999.999999"));
        assertNotEquals(before, BatchOrchestratorService.effectiveChecksum(List.of(p)));
        p.setEffectiveQuantity(new BigDecimal("1000"));
        p.setSourceMonth(LocalDate.of(2026, 11, 1));
        assertNotEquals(before, BatchOrchestratorService.effectiveChecksum(List.of(p)));
    }

    @Test void matchesLegacyBytesForEmptyAndUnicodeFacts() throws Exception {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", BatchOrchestratorService.effectiveChecksum(List.of()));
        var a = fact("料:号\n𠮷", "0.000001");
        a.setSupplyMode(null); a.setPathGroupKey(""); a.setSourceDate(null);
        var b = fact("\uD800", "1.000000");
        b.setFactoryCode("永惠"); b.setIsProductionDay(false);
        var c = fact("A", "-0.000001");
        List<MrpDailyPlanDetail> plans = new ArrayList<>(List.of(a, b, c, a));
        String expected = legacyChecksum(plans);
        Collections.reverse(plans);
        assertEquals(expected, BatchOrchestratorService.effectiveChecksum(plans));
        assertEquals(legacyChecksum(List.of(a)), BatchOrchestratorService.effectiveChecksum(List.of(a)));
    }

    @Test void largeFactSetRetainsLegacyFingerprint() throws Exception {
        List<MrpDailyPlanDetail> plans = new ArrayList<>(224100);
        for (int i = 0; i < 224100; i++) {
            var p = fact("M" + i, i % 2 == 0 ? "123.000001" : "900.000000");
            p.setFactoryCode(i % 3 == 0 ? "永惠" : "爱培科");
            p.setScheduleDate(LocalDate.of(2026, 9, 25).plusDays(i % 184));
            plans.add(p);
        }
        Collections.shuffle(plans, new Random(20261016));
        // Warm both methods before observing allocations; elapsed time is diagnostic, not a gate.
        legacyChecksum(plans.subList(0, 100));
        BatchOrchestratorService.effectiveChecksum(plans.subList(0, 100));
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        boolean measurable = bean.isThreadAllocatedMemorySupported();
        if (measurable && !bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long before = measurable ? bean.getThreadAllocatedBytes(thread) : 0;
        String expected = legacyChecksum(plans);
        long middle = measurable ? bean.getThreadAllocatedBytes(thread) : 0;
        String actual = BatchOrchestratorService.effectiveChecksum(plans);
        long after = measurable ? bean.getThreadAllocatedBytes(thread) : 0;
        assertEquals(expected, actual);
        System.out.printf("checksum allocation diagnostic: facts=%d supported=%s legacyBytes=%d streamBytes=%d%n", plans.size(), measurable, middle-before, after-middle);
    }

    /** Frozen pre-fix algorithm: verifies byte-for-byte compatibility with published fingerprints. */
    private String legacyChecksum(List<MrpDailyPlanDetail> plans) throws Exception {
        List<String> facts = new ArrayList<>();
        for (var p : plans) {
            StringBuilder fact = new StringBuilder();
            for (Object value : Arrays.asList(p.getFactoryCode(), p.getRootMaterialCode(), p.getTerminalMaterialCode(),
                    p.getTerminalType(), p.getSupplyMode(), p.getPathGroupKey(), p.getSourceMonth(), p.getSourceDate(),
                    p.getScheduleDate(), p.getIsProductionDay(), p.getEffectiveQuantity().stripTrailingZeros().toPlainString())) {
                String text = Objects.toString(value, "");
                fact.append(text.length()).append(':').append(text);
            }
            facts.add(fact.toString());
        }
        Collections.sort(facts);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", facts).getBytes(StandardCharsets.UTF_8)));
    }
}
