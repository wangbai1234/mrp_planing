package com.mrp.bom.service;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.domain.BomExplosionResult;
import com.mrp.bom.domain.BomExplosionResult.ErrorNode;
import com.mrp.bom.domain.BomExplosionResult.SchedulingNode;
import com.mrp.bom.domain.BomExplosionResult.UnmatchedLeaf;
import com.mrp.bom.local.repository.BomLocalMapper;
import com.mrp.common.exception.ValidationException;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.domain.MaterialCategory;
import com.mrp.masterdata.repository.MaterialCategoryMapper;
import com.mrp.masterdata.repository.MaterialMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BomExplosionService {

    private static final Logger log = LoggerFactory.getLogger(BomExplosionService.class);
    private static final int MAX_DEPTH = 50;

    private final BomLocalMapper bomLocalMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;

    public BomExplosionService(BomLocalMapper bomLocalMapper, MaterialMapper materialMapper,
                               MaterialCategoryMapper categoryMapper) {
        this.bomLocalMapper = bomLocalMapper;
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 预加载 BOM 图和物料分类到内存，避免 N+1 查询。
     * 返回 BomGraph 对象，供后续 explode 调用复用。
     */
    public BomGraph loadBomGraph() {
        long t0 = System.currentTimeMillis();

        // 1. 一次性加载所有 BOM 子项（当前版本，~143k 行）
        List<BomDetail> allDetails = bomLocalMapper.selectAllDetails();
        long t1 = System.currentTimeMillis();
        log.info("BOM graph: loaded {} detail rows in {}ms", allDetails.size(), t1 - t0);

        return buildBomGraph(allDetails, t0);
    }

    /**
     * 只加载指定根物料可达的 BOM 子图。
     * 通过 BFS 找到所有可达的 parentCode，然后只加载这些节点。
     * 适用于整机数量较少的场景（如 10-100 个整机）。
     */
    public BomGraph loadBomSubgraph(Set<String> rootMaterialCodes) {
        long t0 = System.currentTimeMillis();

        // 1. BFS 找到所有可达的 parentCode
        Set<String> reachableParents = new HashSet<>(rootMaterialCodes);
        Queue<String> queue = new LinkedList<>(rootMaterialCodes);
        int maxIterations = 10000; // 安全限制
        int iterations = 0;

        while (!queue.isEmpty() && iterations < maxIterations) {
            String currentCode = queue.poll();
            iterations++;

            // 查询当前节点的子项（只查 parent_code）
            List<BomDetail> children = bomLocalMapper.selectDetailByParentCode(currentCode);
            for (BomDetail child : children) {
                String childCode = child.getChildCode();
                if (!reachableParents.contains(childCode)) {
                    reachableParents.add(childCode);
                    queue.add(childCode);
                }
            }
        }

        long t1 = System.currentTimeMillis();
        log.info("BOM subgraph: BFS found {} reachable nodes from {} roots in {}ms (iterations={})",
                reachableParents.size(), rootMaterialCodes.size(), t1 - t0, iterations);

        // 2. 批量加载可达节点的 BOM 详情
        List<String> parentCodesList = new ArrayList<>(reachableParents);
        List<BomDetail> allDetails = new ArrayList<>();
        // 分批加载，避免 IN 子句过大
        int batchSize = 1000;
        for (int i = 0; i < parentCodesList.size(); i += batchSize) {
            List<String> batch = parentCodesList.subList(i, Math.min(i + batchSize, parentCodesList.size()));
            allDetails.addAll(bomLocalMapper.selectDetailsByParentCodes(batch));
        }

        long t2 = System.currentTimeMillis();
        log.info("BOM subgraph: loaded {} detail rows for {} parents in {}ms",
                allDetails.size(), reachableParents.size(), t2 - t1);

        return buildBomGraph(allDetails, t0);
    }

    /**
     * 从 BOM 详情列表构建 BomGraph。
     */
    private BomGraph buildBomGraph(List<BomDetail> allDetails, long startTime) {
        long t1 = System.currentTimeMillis();

        // 2. 构建 parentCode -> children Map
        Map<String, List<BomDetail>> childrenMap = new HashMap<>();
        for (BomDetail d : allDetails) {
            childrenMap.computeIfAbsent(d.getParentCode(), k -> new ArrayList<>()).add(d);
        }

        // 3. 加载所有物料的分类（包括根物料，它们不在 bom_detail 中）
        List<Material> allMaterials = materialMapper.selectAll(null, null, null, null, null);
        Map<String, Material> materialByCode = new HashMap<>();
        for (Material m : allMaterials) {
            materialByCode.put(m.materialCode(), m);
        }

        // 4. 加载所有分类
        List<MaterialCategory> allCategories = categoryMapper.selectAll();
        Map<Long, MaterialCategory> categoryById = new HashMap<>();
        Map<String, MaterialCategory> categoryByCode = new HashMap<>();
        for (MaterialCategory c : allCategories) {
            categoryById.put(c.getId(), c);
            categoryByCode.put(c.getCode(), c);
        }

        // 5. 构建 materialCode -> categoryCode 映射
        Map<String, String> materialToCategoryCode = new HashMap<>();
        for (Material m : allMaterials) {
            if (m.materialCategoryId() != null) {
                MaterialCategory cat = categoryById.get(m.materialCategoryId());
                if (cat != null && Boolean.TRUE.equals(cat.getEnabled())) {
                    materialToCategoryCode.put(m.materialCode(), cat.getCode());
                }
            }
        }

        long t2 = System.currentTimeMillis();
        log.info("BOM graph: built maps in {}ms (materials={}, categories={}, bomNodes={})",
                t2 - t1, allMaterials.size(), allCategories.size(), childrenMap.size());

        return new BomGraph(childrenMap, materialByCode, materialToCategoryCode, categoryByCode);
    }

    /**
     * 使用预加载的 BomGraph 执行 BOM 展开。
     * 同一 BomGraph 可被多次 explode 调用复用。
     */
    public BomExplosionResult explode(Long rootMaterialId, BigDecimal demandQuantity,
                                      List<String> targetCategories, BomGraph graph) {
        if (rootMaterialId == null) {
            throw new ValidationException("根物料ID不能为空");
        }
        if (demandQuantity == null || demandQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("需求数量必须大于0");
        }
        if (targetCategories == null || targetCategories.isEmpty()) {
            throw new ValidationException("目标分类不能为空");
        }

        Set<String> targetCategorySet = new HashSet<>(targetCategories);

        // 从 graph 中查找根物料
        Material rootMaterial = null;
        for (Material m : graph.materialByCode().values()) {
            if (m.id().equals(rootMaterialId)) {
                rootMaterial = m;
                break;
            }
        }
        if (rootMaterial == null) {
            throw new ValidationException("根物料不存在: " + rootMaterialId);
        }

        BomExplosionResult result = new BomExplosionResult();
        Set<String> visited = new HashSet<>();

        List<BomDetail> rootChildren = graph.childrenMap().getOrDefault(rootMaterial.materialCode(), List.of());

        if (!rootChildren.isEmpty()) {
            explodeRecursive(rootMaterial.materialCode(), rootMaterial.materialName(),
                    demandQuantity, BigDecimal.ONE, rootMaterial.materialCode(),
                    "1", targetCategorySet, graph, result, visited, 0,
                    rootMaterial.materialCode(), rootMaterial.materialName());
        } else {
            String categoryCode = graph.materialToCategoryCode().get(rootMaterial.materialCode());
            if (categoryCode != null && targetCategorySet.contains(categoryCode)) {
                MaterialCategory cat = graph.categoryByCode().get(categoryCode);
                SchedulingNode node = new SchedulingNode();
                node.setRootMaterialCode(rootMaterial.materialCode());
                node.setRootMaterialName(rootMaterial.materialName());
                node.setMaterialCode(rootMaterial.materialCode());
                node.setMaterialName(rootMaterial.materialName());
                node.setCategoryCode(categoryCode);
                node.setCategoryName(cat != null ? cat.getName() : "");
                node.setDemandQuantity(demandQuantity);
                node.setBomPath(rootMaterial.materialCode());
                node.setQuantityChain(demandQuantity.toPlainString());
                node.setQuantityMultiplier(demandQuantity);
                result.getSchedulingNodes().add(node);
            } else {
                UnmatchedLeaf leaf = new UnmatchedLeaf();
                leaf.setRootMaterialCode(rootMaterial.materialCode());
                leaf.setRootMaterialName(rootMaterial.materialName());
                leaf.setMaterialCode(rootMaterial.materialCode());
                leaf.setMaterialName(rootMaterial.materialName());
                leaf.setCategoryCode(categoryCode);
                leaf.setDemandQuantity(demandQuantity);
                leaf.setBomPath(rootMaterial.materialCode());
                leaf.setQuantityChain(demandQuantity.toPlainString());
                leaf.setReason(categoryCode == null ? "物料无分类" : "分类未命中目标");
                result.getUnmatchedLeaves().add(leaf);
            }
        }

        return result;
    }

    /**
     * 向后兼容：无 BomGraph 时自动加载（不推荐用于批量场景）
     */
    public BomExplosionResult explode(Long rootMaterialId, BigDecimal demandQuantity,
                                      List<String> targetCategories) {
        BomGraph graph = loadBomGraph();
        return explode(rootMaterialId, demandQuantity, targetCategories, graph);
    }

    private void explodeRecursive(String materialCode, String materialName,
                                  BigDecimal accumulatedQty, BigDecimal stepMultiplier,
                                  String bomPath, String qtyChain,
                                  Set<String> targetCategorySet,
                                  BomGraph graph,
                                  BomExplosionResult result, Set<String> visited,
                                  int depth, String rootCode, String rootName) {
        if (depth > MAX_DEPTH) {
            result.getErrorNodes().add(new ErrorNode(materialCode, materialName,
                    "MAX_DEPTH_EXCEEDED", "BOM展开超过最大深度(" + MAX_DEPTH + ")", bomPath));
            return;
        }

        if (visited.contains(materialCode)) {
            result.getErrorNodes().add(new ErrorNode(materialCode, materialName,
                    "BOM_CYCLE", "BOM循环: " + materialCode, bomPath));
            return;
        }
        visited.add(materialCode);

        // 从内存 Map 获取分类，无 SQL 查询
        String categoryCode = graph.materialToCategoryCode().get(materialCode);
        String categoryName = null;
        if (categoryCode != null) {
            MaterialCategory cat = graph.categoryByCode().get(categoryCode);
            categoryName = cat != null ? cat.getName() : null;
        }

        if (categoryCode != null && targetCategorySet.contains(categoryCode)) {
            SchedulingNode node = new SchedulingNode();
            node.setRootMaterialCode(rootCode);
            node.setRootMaterialName(rootName);
            node.setMaterialCode(materialCode);
            node.setMaterialName(materialName);
            node.setCategoryCode(categoryCode);
            node.setCategoryName(categoryName != null ? categoryName : "");
            node.setDemandQuantity(accumulatedQty);
            node.setBomPath(bomPath);
            node.setQuantityChain(qtyChain);
            node.setQuantityMultiplier(accumulatedQty);
            result.getSchedulingNodes().add(node);

            visited.remove(materialCode);
            return;
        }

        // 从内存 Map 获取子项，无 SQL 查询
        List<BomDetail> children = graph.childrenMap().getOrDefault(materialCode, List.of());

        if (children.isEmpty()) {
            UnmatchedLeaf leaf = new UnmatchedLeaf();
            leaf.setRootMaterialCode(rootCode);
            leaf.setRootMaterialName(rootName);
            leaf.setMaterialCode(materialCode);
            leaf.setMaterialName(materialName);
            leaf.setCategoryCode(categoryCode);
            leaf.setCategoryName(categoryName);
            leaf.setDemandQuantity(accumulatedQty);
            leaf.setBomPath(bomPath);
            leaf.setQuantityChain(qtyChain);
            leaf.setReason(categoryCode == null ? "物料无分类" : "分类未命中目标且无BOM子项");
            result.getUnmatchedLeaves().add(leaf);

            visited.remove(materialCode);
            return;
        }

        for (BomDetail child : children) {
            String childCode = child.getChildCode();
            String childName = child.getChildName();
            Long childQty = child.getChildQty();

            if (childQty == null || childQty <= 0) {
                result.getErrorNodes().add(new ErrorNode(childCode, childName,
                        "INVALID_BOM_QTY", "BOM数量无效: " + childQty, bomPath + " -> " + childCode));
                continue;
            }

            BigDecimal childQtyDecimal = BigDecimal.valueOf(childQty);
            BigDecimal newAccumulated = accumulatedQty.multiply(childQtyDecimal);
            String newBomPath = bomPath + " -> " + childCode;
            String newQtyChain = qtyChain + " x " + childQty;

            // 从内存获取物料名称
            Material childMaterial = graph.materialByCode().get(childCode);
            String childNameResolved = childMaterial != null ? childMaterial.materialName() : childName;

            if (childMaterial == null) {
                result.getErrorNodes().add(new ErrorNode(childCode, childNameResolved,
                        "MATERIAL_NOT_FOUND", "子物料不存在: " + childCode, newBomPath));
                continue;
            }

            // 从内存检查是否有子项
            List<BomDetail> childChildren = graph.childrenMap().getOrDefault(childCode, List.of());

            if (!childChildren.isEmpty()) {
                explodeRecursive(childCode, childNameResolved, newAccumulated, childQtyDecimal,
                        newBomPath, newQtyChain, targetCategorySet, graph,
                        result, visited, depth + 1, rootCode, rootName);
            } else {
                String childCatCode = graph.materialToCategoryCode().get(childCode);
                if (childCatCode != null && targetCategorySet.contains(childCatCode)) {
                    MaterialCategory cat = graph.categoryByCode().get(childCatCode);
                    SchedulingNode node = new SchedulingNode();
                    node.setRootMaterialCode(rootCode);
                    node.setRootMaterialName(rootName);
                    node.setMaterialCode(childCode);
                    node.setMaterialName(childNameResolved);
                    node.setCategoryCode(childCatCode);
                    node.setCategoryName(cat != null ? cat.getName() : "");
                    node.setDemandQuantity(newAccumulated);
                    node.setBomPath(newBomPath);
                    node.setQuantityChain(newQtyChain);
                    node.setQuantityMultiplier(newAccumulated);
                    result.getSchedulingNodes().add(node);
                } else {
                    UnmatchedLeaf leaf = new UnmatchedLeaf();
                    leaf.setRootMaterialCode(rootCode);
                    leaf.setRootMaterialName(rootName);
                    leaf.setMaterialCode(childCode);
                    leaf.setMaterialName(childNameResolved);
                    leaf.setCategoryCode(childCatCode);
                    leaf.setDemandQuantity(newAccumulated);
                    leaf.setBomPath(newBomPath);
                    leaf.setQuantityChain(newQtyChain);
                    leaf.setReason(childCatCode == null ? "物料无分类" : "分类未命中目标");
                    result.getUnmatchedLeaves().add(leaf);
                }
            }
        }

        visited.remove(materialCode);
    }

    /**
     * BOM 图数据结构，预加载后在内存中使用。
     */
    public record BomGraph(
            Map<String, List<BomDetail>> childrenMap,
            Map<String, Material> materialByCode,
            Map<String, String> materialToCategoryCode,
            Map<String, MaterialCategory> categoryByCode
    ) {}
}
