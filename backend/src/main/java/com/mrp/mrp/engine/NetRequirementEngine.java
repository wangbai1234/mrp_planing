package com.mrp.mrp.engine;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.service.MaterialPolicyRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

public class NetRequirementEngine {

    private static final Logger log = LoggerFactory.getLogger(NetRequirementEngine.class);

    private final Map<String, List<BomSnapshotDetail>> adjacencyMap;
    private final Map<String, BigDecimal> inventoryMap;
    private final Map<String, CategoryRoleDetail> categoryRoleMap;
    private final Map<String, MaterialPolicyDetail> materialPolicyMap;
    private final Map<String, MrpInventoryAllocation> lockedAllocationMap;
    private final Set<String> materialsWithInventory;
    private final Set<String> existingMaterials;
    private final String factoryCode;
    private final Long runId;
    private final Long batchId;
    private final boolean factoryScopedIssues;

    public NetRequirementEngine(
            List<BomSnapshotDetail> edges,
            Map<String, BigDecimal> inventoryMap,
            Map<String, CategoryRoleDetail> categoryRoleMap,
            Map<String, MaterialPolicyDetail> materialPolicyMap,
            List<MrpInventoryAllocation> lockedAllocations,
            String factoryCode,
            Long runId,
            Long batchId) {
        this(edges, inventoryMap, categoryRoleMap, materialPolicyMap, lockedAllocations, null, factoryCode, null, runId, batchId, null);
    }

    public NetRequirementEngine(
            List<BomSnapshotDetail> edges,
            Map<String, BigDecimal> inventoryMap,
            Map<String, CategoryRoleDetail> categoryRoleMap,
            Map<String, MaterialPolicyDetail> materialPolicyMap,
            List<MrpInventoryAllocation> lockedAllocations,
            Set<String> materialsWithInventory,
            String factoryCode,
            String inventoryOrg,
            Long runId,
            Long batchId) {
        this(edges, inventoryMap, categoryRoleMap, materialPolicyMap, lockedAllocations,
                materialsWithInventory, factoryCode, inventoryOrg, runId, batchId, null);
    }

    public NetRequirementEngine(
            List<BomSnapshotDetail> edges, Map<String, BigDecimal> inventoryMap,
            Map<String, CategoryRoleDetail> categoryRoleMap, Map<String, MaterialPolicyDetail> materialPolicyMap,
            List<MrpInventoryAllocation> lockedAllocations, Set<String> materialsWithInventory,
            String factoryCode, String inventoryOrg, Long runId, Long batchId,
            Set<String> existingMaterials) {
        this(edges, inventoryMap, categoryRoleMap, materialPolicyMap, lockedAllocations, materialsWithInventory,
                factoryCode, inventoryOrg, runId, batchId, existingMaterials, false);
    }

    public NetRequirementEngine(
            List<BomSnapshotDetail> edges, Map<String, BigDecimal> inventoryMap,
            Map<String, CategoryRoleDetail> categoryRoleMap, Map<String, MaterialPolicyDetail> materialPolicyMap,
            List<MrpInventoryAllocation> lockedAllocations, Set<String> materialsWithInventory,
            String factoryCode, String inventoryOrg, Long runId, Long batchId,
            Set<String> existingMaterials, boolean factoryScopedIssues) {
        this.adjacencyMap = new LinkedHashMap<>();
        for (BomSnapshotDetail edge : edges) {
            adjacencyMap.computeIfAbsent(edge.getParentCode(), k -> new ArrayList<>()).add(edge);
        }
        for (List<BomSnapshotDetail> children : adjacencyMap.values()) {
            children.sort(Comparator.comparing(BomSnapshotDetail::getChildCode)
                    .thenComparing(BomSnapshotDetail::getBomVersion, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        this.inventoryMap = inventoryMap != null ? inventoryMap : Collections.emptyMap();
        this.categoryRoleMap = categoryRoleMap != null ? categoryRoleMap : Collections.emptyMap();
        this.materialPolicyMap = materialPolicyMap != null ? materialPolicyMap : Collections.emptyMap();
        this.lockedAllocationMap = new LinkedHashMap<>();
        if (lockedAllocations != null) {
            for (MrpInventoryAllocation alloc : lockedAllocations) {
                lockedAllocationMap.merge(alloc.getDemandKey(), alloc, (existing, next) -> {
                    existing.setAllocatedQty(existing.getAllocatedQty().add(next.getAllocatedQty()));
                    return existing;
                });
            }
        }
        this.materialsWithInventory = materialsWithInventory != null ? materialsWithInventory : Collections.emptySet();
        this.existingMaterials = existingMaterials;
        this.factoryCode = factoryCode;
        this.runId = runId;
        this.batchId = batchId;
        this.factoryScopedIssues = factoryScopedIssues;
    }

    public ExplosionResult explode(List<RootDemand> rootDemands) {
        return explode(rootDemands, (completed, total) -> {});
    }

    /** Net only finished-machine roots; component issues belong to the later material stage. */
    public ExplosionResult explodeRoots(List<RootDemand> roots) {
        List<MrpRequirementDetail> details = new ArrayList<>();
        List<MrpTerminalDemand> demands = new ArrayList<>();
        List<MrpDecisionIssue> issues = new ArrayList<>();
        Map<String, BigDecimal> used = new LinkedHashMap<>();
        Map<String, BigDecimal> remaining = new LinkedHashMap<>();
        Map<String, BigDecimal> reserved = new LinkedHashMap<>();
        for (var entry : lockedAllocationMap.entrySet()) {
            String[] dimensions = entry.getKey().split("\\|", 3);
            if (dimensions.length != 3) throw new IllegalStateException("库存分配需求标识无效");
            BigDecimal quantity = entry.getValue().getAllocatedQty();
            if (quantity == null || quantity.signum() < 0) throw new IllegalStateException("库存分配数量无效");
            remaining.put(entry.getKey(), quantity);
            reserved.merge(dimensions[1] + "|GOOD", quantity, BigDecimal::add);
        }
        for (var entry : reserved.entrySet()) {
            if (entry.getValue().compareTo(inventoryMap.getOrDefault(entry.getKey(), BigDecimal.ZERO)) > 0)
                throw new IllegalStateException("库存分配超过扣除安全库存后的可用量: " + entry.getKey());
        }
        for (RootDemand root : roots) {
            String key = root.materialCode + "|GOOD";
            String demandKey = root.materialCode + "|" + root.materialCode + "|" + root.sourceMonth;
            BigDecimal before = getAvailableInventory(root.materialCode, used);
            BigDecimal available = remaining.containsKey(demandKey) ? remaining.get(demandKey)
                    : before.subtract(reserved.getOrDefault(key, BigDecimal.ZERO)).max(BigDecimal.ZERO);
            BigDecimal consumed = root.quantity.min(available).min(before);
            used.merge(key, consumed, BigDecimal::add);
            if (remaining.containsKey(demandKey)) {
                remaining.put(demandKey, remaining.get(demandKey).subtract(consumed));
                reserved.put(key, reserved.get(key).subtract(consumed));
            }
            ExplosionNode node = new ExplosionNode(root.materialCode, root.quantity, BigDecimal.ONE,
                    root.materialCode, root.sourceMonth, 0, null, new LinkedHashSet<>());
            CategoryRoleDetail role = categoryRoleMap.get(root.materialCode);
            MaterialPolicyDetail policy = materialPolicyMap.get(root.materialCode);
            MrpRequirementDetail detail = addDetail(details, node, root.quantity, before, consumed,
                    before.subtract(consumed), root.quantity.subtract(consumed), role, policy, false, null);
            if (role == null || !Boolean.TRUE.equals(role.getEnabled()) || "EXCLUDED".equals(role.getRole())) {
                String type = role == null ? (existingMaterials != null && !existingMaterials.contains(root.materialCode)
                        ? "MATERIAL_MISSING" : "ROLE_MISSING") : "CATEGORY_ROLE_DISABLED";
                addIssue(issues, node, type, "BLOCKING", "Finished-machine role is missing or unavailable");
            }
            // A missing root inventory record is an explicit material-control decision. It
            // cannot suppress a candidate machine schedule.
        }
        return new ExplosionResult(details, demands, Map.of(), used, issues);
    }

    public ExplosionResult explode(List<RootDemand> rootDemands, java.util.function.BiConsumer<Integer, Integer> progress) {
        List<MrpRequirementDetail> allDetails = new ArrayList<>();
        List<MrpTerminalDemand> terminalDemands = new ArrayList<>();
        List<MrpDecisionIssue> issues = new ArrayList<>();
        Map<String, BigDecimal> usedInventory = new LinkedHashMap<>();
        Map<String, BigDecimal> remainingAllocations = new LinkedHashMap<>();
        Map<String, BigDecimal> reservedByMaterial = new LinkedHashMap<>();
        for (var entry : lockedAllocationMap.entrySet()) {
            BigDecimal quantity = entry.getValue().getAllocatedQty();
            if (quantity == null || quantity.signum() < 0) throw new IllegalStateException("库存分配数量无效");
            String[] dimensions = entry.getKey().split("\\|", 3);
            if (dimensions.length != 3) throw new IllegalStateException("库存分配需求标识无效");
            remainingAllocations.put(entry.getKey(), quantity);
            reservedByMaterial.merge(dimensions[1] + "|GOOD", quantity, BigDecimal::add);
        }
        for (var reservation : reservedByMaterial.entrySet()) {
            if (reservation.getValue().compareTo(inventoryMap.getOrDefault(reservation.getKey(), BigDecimal.ZERO)) > 0)
                throw new IllegalStateException("库存分配超过扣除安全库存后的可用量: " + reservation.getKey());
        }
        int completedRoots = 0;
        for (RootDemand root : rootDemands) {
            explodeRoot(root, allDetails, terminalDemands, issues, usedInventory, remainingAllocations, reservedByMaterial);
            progress.accept(++completedRoots, rootDemands.size());
        }

        Map<String, BigDecimal> terminalAggregation = new LinkedHashMap<>();
        for (MrpTerminalDemand td : terminalDemands) {
            String key = td.getTerminalMaterialCode() + "|" + td.getSourceMonth();
            terminalAggregation.merge(key, td.getQuantity(), BigDecimal::add);
        }

        return new ExplosionResult(allDetails, terminalDemands, terminalAggregation, usedInventory, issues);
    }

    /** Material stage consumes already-netted machine output, without another root deduction. */
    public ExplosionResult explodeNetRoots(List<RootDemand> roots) {
        List<MrpRequirementDetail> details = new ArrayList<>();
        List<MrpTerminalDemand> demands = new ArrayList<>();
        List<MrpDecisionIssue> issues = new ArrayList<>();
        Map<String, BigDecimal> used = new LinkedHashMap<>();
        Map<String, BigDecimal> remaining = new LinkedHashMap<>();
        Map<String, BigDecimal> reserved = new LinkedHashMap<>();
        for (var entry : lockedAllocationMap.entrySet()) {
            String[] dimensions = entry.getKey().split("\\|", 3);
            if (dimensions.length != 3) throw new IllegalStateException("库存分配需求标识无效");
            BigDecimal qty = entry.getValue().getAllocatedQty();
            if (qty == null || qty.signum() < 0) throw new IllegalStateException("库存分配数量无效");
            remaining.put(entry.getKey(), qty);
            reserved.merge(dimensions[1] + "|GOOD", qty, BigDecimal::add);
        }
        for (var entry : reserved.entrySet())
            if (entry.getValue().compareTo(inventoryMap.getOrDefault(entry.getKey(), BigDecimal.ZERO)) > 0)
                throw new IllegalStateException("库存分配超过可用量: " + entry.getKey());
        for (RootDemand root : roots) {
            if (root.quantity.signum() > 0) {
                if (!materialsWithInventory.contains(root.materialCode)) {
                    ExplosionNode node = new ExplosionNode(root.materialCode, root.quantity, BigDecimal.ONE,
                            root.materialCode, root.sourceMonth, 0, null, new LinkedHashSet<>());
                    addIssue(issues, node, "INVENTORY_MISSING", "BLOCKING",
                            "Finished machine " + root.materialCode + " has no inventory balance record in the snapshot");
                }
                explodeRoot(root, details, demands, issues, used, remaining, reserved, true);
            }
        }
        details.removeIf(detail -> Integer.valueOf(0).equals(detail.getLevelNo()));
        return new ExplosionResult(details, demands, Map.of(), used, issues);
    }

    private void explodeRoot(RootDemand root, List<MrpRequirementDetail> details,
                             List<MrpTerminalDemand> terminalDemands, List<MrpDecisionIssue> issues,
                             Map<String, BigDecimal> usedInventory, Map<String, BigDecimal> remainingAllocations,
                             Map<String, BigDecimal> reservedByMaterial) {
        explodeRoot(root, details, terminalDemands, issues, usedInventory, remainingAllocations, reservedByMaterial, false);
    }

    private void explodeRoot(RootDemand root, List<MrpRequirementDetail> details,
                             List<MrpTerminalDemand> terminalDemands, List<MrpDecisionIssue> issues,
                             Map<String, BigDecimal> usedInventory, Map<String, BigDecimal> remainingAllocations,
                             Map<String, BigDecimal> reservedByMaterial, boolean rootAlreadyNet) {
        Queue<ExplosionNode> queue = new LinkedList<>();
        queue.add(new ExplosionNode(root.materialCode, root.quantity, BigDecimal.ONE,
                root.materialCode, root.sourceMonth, 0, null, new LinkedHashSet<>()));

        while (!queue.isEmpty()) {
            ExplosionNode node = queue.poll();
            MaterialPolicyDetail policy = materialPolicyMap.get(node.materialCode);
            boolean stopExplosion = policy != null && "STOP".equals(policy.getExplosionPolicy());

            CategoryRoleDetail role = categoryRoleMap.get(node.materialCode);
            if (role == null) {
                boolean materialMissing = existingMaterials != null && !existingMaterials.contains(node.materialCode);
                String issueType = materialMissing ? "MATERIAL_MISSING" : "ROLE_MISSING";
                addIssue(issues, node, issueType, "BLOCKING",
                        "Material " + node.materialCode + (materialMissing ? " is absent from the local material master" : " has no category role"));
                if (!(rootAlreadyNet && node.depth == 0) && !materialsWithInventory.contains(node.materialCode)) {
                    addIssue(issues, node, "INVENTORY_MISSING", "BLOCKING",
                            "Material " + node.materialCode + " has no inventory balance record in the snapshot");
                }
                addDetail(details, node, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        true, issueType);
                // A missing role blocks approval, but must not hide further BOM nodes from precheck.
                if (!stopExplosion) enqueueChildrenForValidation(queue, node);
                continue;
            }

            if (!Boolean.TRUE.equals(role.getEnabled())) {
                addIssue(issues, node, "CATEGORY_ROLE_DISABLED", "BLOCKING",
                        "Category role for " + node.materialCode + " is disabled");
                if (!(rootAlreadyNet && node.depth == 0) && !materialsWithInventory.contains(node.materialCode)) {
                    addIssue(issues, node, "INVENTORY_MISSING", "BLOCKING",
                            "Material " + node.materialCode + " has no inventory balance record in the snapshot");
                }
                addDetail(details, node, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        true, "CATEGORY_ROLE_DISABLED");
                if (!stopExplosion) enqueueChildrenForValidation(queue, node);
                continue;
            }

            if ("EXCLUDED".equals(role.getRole()) && node.depth == 0) {
                continue;
            }
            if ("EXCLUDED".equals(role.getRole())) {
                addIssue(issues, node, "EXCLUDED_INTERMEDIATE", "BLOCKING",
                        "Excluded material " + node.materialCode + " appears as intermediate node");
                addDetail(details, node, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        true, "EXCLUDED_INTERMEDIATE");
                continue;
            }

            // The caller supplies only this factory's frozen mapped-warehouse snapshot.
            // DEFECTIVE balances are traceable but never available for consumption.
            if (!(rootAlreadyNet && node.depth == 0) && !materialsWithInventory.contains(node.materialCode)) {
                addIssue(issues, node, "INVENTORY_MISSING", "BLOCKING",
                        "Material " + node.materialCode + " has no inventory balance record in the snapshot");
                // A missing balance is interpreted as zero only after business confirmation.
                // It must not stop the BOM path: downstream requirements still need validation.
            }

            BigDecimal gross = node.gross;
            BigDecimal inventoryBefore = getAvailableInventory(node.materialCode, usedInventory);
            String usedInventoryKey = node.materialCode + "|GOOD";
            String demandKey = node.rootMaterialCode + "|" + node.materialCode + "|" + node.sourceMonth;
            BigDecimal inventoryUsed;
            if (rootAlreadyNet && node.depth == 0) {
                inventoryUsed = BigDecimal.ZERO;
            } else if (remainingAllocations.containsKey(demandKey)) {
                // Explicit allocation is the whole inventory deduction for this demand;
                // consume its budget once even when the same material has several paths.
                inventoryUsed = gross.min(remainingAllocations.get(demandKey)).min(inventoryBefore);
                remainingAllocations.compute(demandKey, (key, value) -> value.subtract(inventoryUsed));
                reservedByMaterial.compute(usedInventoryKey, (key, value) -> value.subtract(inventoryUsed));
            } else {
                BigDecimal automaticAvailable = inventoryBefore.subtract(
                        reservedByMaterial.getOrDefault(usedInventoryKey, BigDecimal.ZERO)).max(BigDecimal.ZERO);
                inventoryUsed = gross.min(automaticAvailable);
            }
            BigDecimal inventoryAfter = inventoryBefore.subtract(inventoryUsed);
            BigDecimal netAfterInventory = gross.subtract(inventoryUsed).max(BigDecimal.ZERO);
            usedInventory.merge(usedInventoryKey, inventoryUsed, BigDecimal::add);

            MrpRequirementDetail detail = addDetail(details, node, gross, inventoryBefore, inventoryUsed,
                    inventoryAfter, netAfterInventory, role, policy, false, null);

            if (stopExplosion) {
                detail.setTerminalFlag(true);
                detail.setTerminalReason("POLICY_STOP");
                addTerminalDemand(terminalDemands, node, netAfterInventory,
                        role.getRole(), policy != null ? policy.getSupplyMode() : null, "POLICY_STOP");
                continue;
            }

            List<BomSnapshotDetail> children = adjacencyMap.get(node.materialCode);
            if (children == null || children.isEmpty()) {
                detail.setTerminalFlag(true);
                detail.setTerminalReason("LEAF");
                addTerminalDemand(terminalDemands, node, netAfterInventory,
                        role.getRole(), policy != null ? policy.getSupplyMode() : null, "LEAF");
                continue;
            }

            if (node.currentPath.contains(node.materialCode)) {
                detail.setTerminalFlag(true);
                detail.setTerminalReason("BOM_CYCLE");
                addIssue(issues, node, "BOM_CYCLE", "BLOCKING",
                        "Cycle detected at material " + node.materialCode + " in path " + node.currentPath);
                addTerminalDemand(terminalDemands, node, netAfterInventory,
                        role.getRole(), policy != null ? policy.getSupplyMode() : null, "BOM_CYCLE");
                continue;
            }

            Set<String> newPath = new LinkedHashSet<>(node.currentPath);
            newPath.add(node.materialCode);

            for (BomSnapshotDetail child : children) {
                // 子项用量为 0 表示该整机不使用此物料，跳过且不产生需求。
                if (child.getChildQty() == null || child.getChildQty().compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                BigDecimal ratio = child.getChildQty().divide(child.getParentQty(), 18, RoundingMode.HALF_UP);
                BigDecimal childGross = netAfterInventory.multiply(ratio);
                queue.add(new ExplosionNode(child.getChildCode(), childGross, child.getParentQty(),
                        node.rootMaterialCode, node.sourceMonth, node.depth + 1,
                        node.materialCode, newPath));
            }
        }
    }

    private void enqueueChildrenForValidation(Queue<ExplosionNode> queue, ExplosionNode node) {
        List<BomSnapshotDetail> children = adjacencyMap.get(node.materialCode);
        if (children == null || children.isEmpty() || node.currentPath.contains(node.materialCode)) return;
        Set<String> path = new LinkedHashSet<>(node.currentPath);
        path.add(node.materialCode);
        for (BomSnapshotDetail child : children) {
            if (child.getChildQty() == null || child.getChildQty().signum() <= 0) continue;
            BigDecimal gross = node.gross.multiply(child.getChildQty())
                    .divide(child.getParentQty(), 18, RoundingMode.HALF_UP);
            queue.add(new ExplosionNode(child.getChildCode(), gross, child.getParentQty(),
                    node.rootMaterialCode, node.sourceMonth, node.depth + 1, node.materialCode, path));
        }
    }

    private BigDecimal getAvailableInventory(String materialCode, Map<String, BigDecimal> usedInventory) {
        String key = materialCode + "|GOOD";
        BigDecimal total = inventoryMap.getOrDefault(key, BigDecimal.ZERO);
        BigDecimal used = usedInventory.getOrDefault(key, BigDecimal.ZERO);
        return total.subtract(used).max(BigDecimal.ZERO);
    }

    private void addDetail(List<MrpRequirementDetail> details, ExplosionNode node,
                           BigDecimal gross, BigDecimal inventoryBefore, BigDecimal inventoryUsed,
                           BigDecimal inventoryAfter, boolean terminalFlag, String terminalReason) {
        addDetail(details, node, gross, inventoryBefore, inventoryUsed, inventoryAfter,
                gross.subtract(inventoryUsed).max(BigDecimal.ZERO), null, null, terminalFlag, terminalReason);
    }

    private MrpRequirementDetail addDetail(List<MrpRequirementDetail> details, ExplosionNode node,
                           BigDecimal gross, BigDecimal inventoryBefore, BigDecimal inventoryUsed,
                           BigDecimal inventoryAfter, BigDecimal netQty, CategoryRoleDetail role,
                           MaterialPolicyDetail policy, boolean terminalFlag, String terminalReason) {
        MrpRequirementDetail detail = new MrpRequirementDetail();
        detail.setRunId(runId);
        detail.setBatchId(batchId);
        detail.setFactoryCode(factoryCode);
        detail.setRootMaterialCode(node.rootMaterialCode);
        detail.setSourceMonth(node.sourceMonth);
        detail.setParentMaterialCode(node.parentMaterialCode);
        detail.setCurrentMaterialCode(node.materialCode);
        detail.setLevelNo(node.depth);
        detail.setGrossQty(gross);
        detail.setInventoryBeforeQty(inventoryBefore);
        detail.setInventoryUsedQty(inventoryUsed);
        detail.setInventoryAfterQty(inventoryAfter);
        detail.setNetQty(netQty);
        detail.setPathText(buildPathText(node));
        detail.setPathHash(computePathHash(node));
        detail.setCategoryRole(role != null ? role.getRole() : null);
        policy = materialPolicyMap.get(node.materialCode);
        detail.setSupplyMode(MaterialPolicyRules.effectiveSupply(policy != null ? policy.getSupplyMode() : null));
        detail.setEffectiveExplosionPolicy(MaterialPolicyRules.effectiveExplosion(policy != null ? policy.getExplosionPolicy() : null));
        detail.setPolicySource(policy != null && MaterialPolicyRules.validExplosion(policy.getExplosionPolicy()) ? "CONFIGURED" : "DEFAULT");
        detail.setExplosionPolicy(policy != null ? policy.getExplosionPolicy() : null);
        detail.setTerminalFlag(terminalFlag);
        detail.setTerminalReason(terminalReason);
        details.add(detail);
        return detail;
    }

    private void addTerminalDemand(List<MrpTerminalDemand> terminalDemands, ExplosionNode node,
                                   BigDecimal quantity, String terminalType, String supplyMode,
                                   String reason) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        MrpTerminalDemand terminal = new MrpTerminalDemand();
        terminal.setRunId(runId);
        terminal.setBatchId(batchId);
        terminal.setFactoryCode(factoryCode);
        terminal.setRootMaterialCode(node.rootMaterialCode);
        terminal.setTerminalMaterialCode(node.materialCode);
        terminal.setTerminalType(terminalType);
        terminal.setSupplyMode(MaterialPolicyRules.effectiveSupply(supplyMode));
        terminal.setSourceMonth(node.sourceMonth);
        terminal.setQuantity(quantity);
        terminalDemands.add(terminal);
    }

    private void addIssue(List<MrpDecisionIssue> issues, ExplosionNode node,
                          String issueType, String severity, String message) {
        MrpDecisionIssue issue = new MrpDecisionIssue();
        issue.setBatchId(batchId);
        issue.setFactoryCode(factoryCode);
        String legacyKey = node.rootMaterialCode + "|" + node.materialCode + "|" + issueType;
        if (factoryScopedIssues) {
            // A bounded stable key avoids collisions across factories in one v5 batch.
            try {
                byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(
                        (factoryCode.length() + ":" + factoryCode + legacyKey.length() + ":" + legacyKey)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                issue.setIssueKey("F:" + java.util.HexFormat.of().formatHex(bytes));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        } else {
            issue.setIssueKey(legacyKey);
        }
        issue.setIssueType(issueType);
        issue.setSeverity(severity);
        issue.setScopeType("MATERIAL");
        issue.setScopeKey(node.materialCode);
        issue.setStatus("OPEN");
        issue.setRevision(0);
        issues.add(issue);
        log.warn("Decision issue: {} - {}", issueType, message);
    }

    private String buildPathText(ExplosionNode node) {
        StringBuilder sb = new StringBuilder();
        for (String material : node.currentPath) {
            if (sb.length() > 0) {
                sb.append("→");
            }
            sb.append(material);
        }
        if (sb.length() > 0) {
            sb.append("→");
        }
        sb.append(node.materialCode);
        return sb.toString();
    }

    private String computePathHash(ExplosionNode node) {
        StringBuilder sb = new StringBuilder();
        for (String material : node.currentPath) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            sb.append(material);
        }
        if (sb.length() > 0) {
            sb.append(":");
        }
        sb.append(node.materialCode);
        return sb.toString();
    }

    public static class RootDemand {
        public String materialCode;
        public BigDecimal quantity;
        public LocalDate sourceMonth;
        public Integer prioritySeq;

        public RootDemand() {}

        public RootDemand(
                @com.fasterxml.jackson.annotation.JsonProperty("materialCode") String materialCode,
                @com.fasterxml.jackson.annotation.JsonProperty("quantity") BigDecimal quantity,
                @com.fasterxml.jackson.annotation.JsonProperty("sourceMonth") LocalDate sourceMonth) {
            this(materialCode, quantity, sourceMonth, 0);
        }

        @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.PROPERTIES)
        public RootDemand(
                @com.fasterxml.jackson.annotation.JsonProperty("materialCode") String materialCode,
                @com.fasterxml.jackson.annotation.JsonProperty("quantity") BigDecimal quantity,
                @com.fasterxml.jackson.annotation.JsonProperty("sourceMonth") LocalDate sourceMonth,
                @com.fasterxml.jackson.annotation.JsonProperty("prioritySeq") Integer prioritySeq) {
            this.materialCode = materialCode;
            this.quantity = quantity;
            this.sourceMonth = sourceMonth;
            this.prioritySeq = prioritySeq == null ? 0 : prioritySeq;
        }
    }

    private static class ExplosionNode {
        final String materialCode;
        final BigDecimal gross;
        final BigDecimal parentQty;
        final String rootMaterialCode;
        final LocalDate sourceMonth;
        final int depth;
        final String parentMaterialCode;
        final Set<String> currentPath;

        ExplosionNode(String materialCode, BigDecimal gross, BigDecimal parentQty,
                      String rootMaterialCode, LocalDate sourceMonth, int depth,
                      String parentMaterialCode, Set<String> currentPath) {
            this.materialCode = materialCode;
            this.gross = gross;
            this.parentQty = parentQty;
            this.rootMaterialCode = rootMaterialCode;
            this.sourceMonth = sourceMonth;
            this.depth = depth;
            this.parentMaterialCode = parentMaterialCode;
            this.currentPath = currentPath;
        }
    }

    public static class ExplosionResult {
        public final List<MrpRequirementDetail> details;
        public final List<MrpTerminalDemand> terminalDemands;
        public final Map<String, BigDecimal> terminalAggregation;
        public final Map<String, BigDecimal> usedInventory;
        public final List<MrpDecisionIssue> issues;

        public ExplosionResult(List<MrpRequirementDetail> details, List<MrpTerminalDemand> terminalDemands,
                               Map<String, BigDecimal> terminalAggregation, Map<String, BigDecimal> usedInventory,
                               List<MrpDecisionIssue> issues) {
            this.details = details;
            this.terminalDemands = terminalDemands;
            this.terminalAggregation = terminalAggregation;
            this.usedInventory = usedInventory;
            this.issues = issues;
        }
    }
}
