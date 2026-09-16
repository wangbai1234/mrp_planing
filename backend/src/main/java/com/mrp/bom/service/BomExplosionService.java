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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public BomExplosionResult explode(Long rootMaterialId, BigDecimal demandQuantity,
                                      List<String> targetCategories) {
        // Validation
        if (rootMaterialId == null) {
            throw new ValidationException("根物料ID不能为空");
        }
        if (demandQuantity == null || demandQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("需求数量必须大于0");
        }
        if (targetCategories == null || targetCategories.isEmpty()) {
            throw new ValidationException("目标分类不能为空");
        }

        // Deduplicate target categories
        List<String> uniqueCategories = targetCategories.stream().distinct().collect(Collectors.toList());

        // Load target category set for fast lookup
        Set<String> targetCategorySet = new HashSet<>(uniqueCategories);

        // Validate all target categories exist and are enabled
        List<MaterialCategory> categories = categoryMapper.selectByCodes(uniqueCategories);
        Map<String, MaterialCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(MaterialCategory::getCode, c -> c));

        for (String code : uniqueCategories) {
            if (!categoryMap.containsKey(code)) {
                throw new ValidationException("目标分类不存在或已停用: " + code);
            }
        }

        // Load root material
        Material rootMaterial = materialMapper.selectById(rootMaterialId);
        if (rootMaterial == null) {
            throw new ValidationException("根物料不存在: " + rootMaterialId);
        }

        BomExplosionResult result = new BomExplosionResult();
        Set<String> visited = new HashSet<>();

        // Check if root material has BOM children (by querying bom_detail, not bom_parent)
        List<BomDetail> rootChildren = bomLocalMapper.selectDetailByParentCode(rootMaterial.materialCode());

        if (!rootChildren.isEmpty()) {
            // Start BOM explosion
            explodeRecursive(rootMaterial.materialCode(), rootMaterial.materialName(),
                    demandQuantity, BigDecimal.ONE, rootMaterial.materialCode(),
                    "1", targetCategorySet, categoryMap, result, visited, 0,
                    rootMaterial.materialCode(), rootMaterial.materialName());
        } else {
            // Root material is not a BOM parent, check if it matches a category
            String categoryCode = getCategoryCodeForMaterial(rootMaterial);
            if (categoryCode != null && targetCategorySet.contains(categoryCode)) {
                MaterialCategory cat = categoryMap.get(categoryCode);
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

        log.info("BOM explosion completed: root={}, schedulingNodes={}, unmatchedLeaves={}, errors={}",
                rootMaterial.materialCode(), result.getSchedulingNodeCount(),
                result.getUnmatchedLeafCount(), result.getErrorNodeCount());

        return result;
    }

    private void explodeRecursive(String materialCode, String materialName,
                                  BigDecimal accumulatedQty, BigDecimal stepMultiplier,
                                  String bomPath, String qtyChain,
                                  Set<String> targetCategorySet,
                                  Map<String, MaterialCategory> categoryMap,
                                  BomExplosionResult result, Set<String> visited,
                                  int depth, String rootCode, String rootName) {
        // Depth limit
        if (depth > MAX_DEPTH) {
            result.getErrorNodes().add(new ErrorNode(materialCode, materialName,
                    "MAX_DEPTH_EXCEEDED", "BOM展开超过最大深度(" + MAX_DEPTH + ")", bomPath));
            return;
        }

        // Cycle detection
        if (visited.contains(materialCode)) {
            result.getErrorNodes().add(new ErrorNode(materialCode, materialName,
                    "BOM_CYCLE", "BOM循环: " + materialCode, bomPath));
            return;
        }
        visited.add(materialCode);

        // Load material from DB to get category
        Material material = materialMapper.selectByCode(materialCode);

        String categoryCode = null;
        String categoryName = null;
        if (material != null) {
            categoryCode = getCategoryCodeForMaterial(material);
            if (categoryCode != null) {
                MaterialCategory cat = categoryMap.get(categoryCode);
                categoryName = cat != null ? cat.getName() : null;
            }
        }

        // Check category match
        if (categoryCode != null && targetCategorySet.contains(categoryCode)) {
            // HIT: This is a scheduling node, stop expanding
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

        // NOT HIT: Continue expanding BOM children
        List<BomDetail> children = bomLocalMapper.selectDetailByParentCode(materialCode);

        if (children.isEmpty()) {
            // Leaf node that didn't match - add to unmatched
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

        // Recurse into children
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

            Material childMaterial = materialMapper.selectByCode(childCode);
            String childNameResolved = childMaterial != null ? childMaterial.materialName() : childName;

            if (childMaterial == null) {
                result.getErrorNodes().add(new ErrorNode(childCode, childNameResolved,
                        "MATERIAL_NOT_FOUND", "子物料不存在: " + childCode, newBomPath));
                continue;
            }

            // Check if child has BOM children (by querying bom_detail, not bom_parent)
            List<BomDetail> childChildren = bomLocalMapper.selectDetailByParentCode(childCode);

            if (!childChildren.isEmpty()) {
                // Has children - recurse
                explodeRecursive(childCode, childNameResolved, newAccumulated, childQtyDecimal,
                        newBomPath, newQtyChain, targetCategorySet, categoryMap,
                        result, visited, depth + 1, rootCode, rootName);
            } else {
                // Leaf node - check category
                String childCatCode = getCategoryCodeForMaterial(childMaterial);
                if (childCatCode != null && targetCategorySet.contains(childCatCode)) {
                    MaterialCategory cat = categoryMap.get(childCatCode);
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

    private String getCategoryCodeForMaterial(Material material) {
        if (material.materialCategoryId() != null) {
            MaterialCategory cat = categoryMapper.selectById(material.materialCategoryId());
            if (cat != null && Boolean.TRUE.equals(cat.getEnabled())) {
                return cat.getCode();
            }
        }
        return null;
    }
}
