package com.mrp.bom.service;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.domain.BomMaterial;
import com.mrp.bom.domain.BomTreeNodeV2;
import com.mrp.bom.local.repository.BomLocalMapper;
import com.mrp.bom.repository.BomMapper;
import com.mrp.common.response.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BomService {

    private static final Logger log = LoggerFactory.getLogger(BomService.class);
    private static final int BATCH_SIZE = 1000;

    private final BomMapper bomOracleMapper;
    private final BomLocalMapper bomLocalMapper;

    public BomService(BomMapper bomOracleMapper, BomLocalMapper bomLocalMapper) {
        this.bomOracleMapper = bomOracleMapper;
        this.bomLocalMapper = bomLocalMapper;
    }

    public PageResult<BomMaterial> getMaterialPage(String keyword, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        int offset = (page - 1) * pageSize;

        List<BomMaterial> items = bomLocalMapper.selectMaterialPage(keyword, offset, pageSize);
        int total = bomLocalMapper.countMaterials(keyword);

        return new PageResult<>(items, total, page, pageSize);
    }

    public BomMaterial getMaterialByCode(String invCode) {
        return bomLocalMapper.selectMaterialByCode(invCode);
    }

    public List<BomDetail> getDetailByParentCode(String parentCode) {
        return bomLocalMapper.selectDetailByParentCode(parentCode);
    }

    public List<BomTreeNodeV2> getTreeChildren(String parentCode) {
        List<BomDetail> details = bomLocalMapper.selectDetailByParentCode(parentCode);
        List<BomTreeNodeV2> nodes = new ArrayList<>();
        for (BomDetail d : details) {
            BomTreeNodeV2 node = new BomTreeNodeV2();
            node.setCode(d.getChildCode());
            node.setName(d.getChildName());
            node.setSpec(d.getMaterialSpec());
            node.setQty(d.getChildQty());
            node.setVersion(d.getVersion());
            boolean hasChildren = bomLocalMapper.isParent(d.getChildCode());
            node.setHasChildren(hasChildren);
            nodes.add(node);
        }
        return nodes;
    }

    @Transactional
    public Map<String, Object> syncFromOracle() {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始从Oracle同步BOM数据...");

            List<BomDetail> oracleDetails = bomOracleMapper.selectAllLatestBom();
            log.info("从Oracle读取到 {} 条BOM明细", oracleDetails.size());

            // 收集所有父项（parentCode）
            Map<String, BomMaterial> parentMap = new HashMap<>();
            for (BomDetail d : oracleDetails) {
                parentMap.computeIfAbsent(d.getParentCode(), code -> {
                    BomMaterial m = new BomMaterial();
                    m.setInvCode(code);
                    m.setInvName(d.getParentName());  // 使用父项名称
                    m.setVersion(d.getVersion());
                    m.setParentQty(d.getParentQty());
                    m.setOrgName(d.getOrgName());
                    m.setBodyCode(d.getBodyCode());
                    return m;
                });
            }

            // 收集所有子项（childCode）中既是子项又是父项的物料
            Map<String, BomMaterial> childParentMap = new HashMap<>();
            for (BomDetail d : oracleDetails) {
                String childCode = d.getChildCode();
                // 如果这个子项在 bom_parent 表中存在（即它也是父项），则添加到 childParentMap
                if (parentMap.containsKey(childCode)) {
                    childParentMap.putIfAbsent(childCode, parentMap.get(childCode));
                }
            }

            bomLocalMapper.deleteAllDetails();
            bomLocalMapper.deleteAllParents();
            log.info("清空本地BOM表完成");

            // 插入所有父项
            List<BomMaterial> parents = new ArrayList<>(parentMap.values());
            for (int i = 0; i < parents.size(); i += BATCH_SIZE) {
                List<BomMaterial> batch = parents.subList(i, Math.min(i + BATCH_SIZE, parents.size()));
                bomLocalMapper.insertParentBatch(batch);
            }
            log.info("同步父项完成，共 {} 条", parents.size());

            // 插入所有子项
            for (int i = 0; i < oracleDetails.size(); i += BATCH_SIZE) {
                List<BomDetail> batch = oracleDetails.subList(i, Math.min(i + BATCH_SIZE, oracleDetails.size()));
                bomLocalMapper.insertDetailBatch(batch);
            }
            log.info("同步子项完成，共 {} 条", oracleDetails.size());

            long elapsed = System.currentTimeMillis() - startTime;
            result.put("success", true);
            result.put("parentCount", parents.size());
            result.put("detailCount", oracleDetails.size());
            result.put("elapsed", elapsed + "ms");

            log.info("BOM数据同步完成，父项: {}, 子项: {}, 耗时: {}ms", parents.size(), oracleDetails.size(), elapsed);

        } catch (Exception e) {
            log.error("BOM数据同步失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public int getLocalCount() {
        return bomLocalMapper.countMaterials(null);
    }
}
