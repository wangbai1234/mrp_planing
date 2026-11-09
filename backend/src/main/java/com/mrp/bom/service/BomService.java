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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class BomService {

    private static final Logger log = LoggerFactory.getLogger(BomService.class);
    private static final int BATCH_SIZE = 1000;
    public static final String PLANNING_ORGANIZATION = "上海七十迈数字科技有限公司";

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
            node.setIsDefault(d.getIsDefault());
            boolean hasChildren = bomLocalMapper.isParent(d.getChildCode());
            node.setHasChildren(hasChildren);
            nodes.add(node);
        }
        return nodes;
    }

    @Transactional
    public Map<String, Object> syncFromOracle() {
        return replaceLocalData(fetchFromOracle((current, total) -> {}));
    }

    public List<BomDetail> fetchFromOracle(BiConsumer<Integer, Integer> progress) {
        int total = bomOracleMapper.countLatestBom(PLANNING_ORGANIZATION);
        if (total <= 0) {
            throw new IllegalStateException("NC未返回上海组织的BOM数据，已停止同步并保留现有本地BOM");
        }
        List<BomDetail> details = new ArrayList<>(total);
        progress.accept(0, total);
        bomOracleMapper.streamLatestBom(PLANNING_ORGANIZATION, context -> {
            details.add(context.getResultObject());
            if (details.size() % BATCH_SIZE == 0) progress.accept(details.size(), total);
        });
        if (details.size() % BATCH_SIZE != 0) progress.accept(details.size(), total);
        if (details.size() != total) {
            throw new IllegalStateException("NC数据在拉取期间发生变化，已停止同步并保留现有本地BOM");
        }
        List<BomDetail> shanghai = details.stream()
                .filter(d -> PLANNING_ORGANIZATION.equals(d.getOrgName()))
                .collect(Collectors.toList());
        if (shanghai.isEmpty()) {
            throw new IllegalStateException("NC返回的BOM数据不包含上海组织，已停止同步并保留现有本地BOM");
        }
        return shanghai;
    }

    @Transactional
    public Map<String, Object> replaceLocalData(List<BomDetail> oracleDetails) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始从NC同步BOM数据...");

            if (oracleDetails == null || oracleDetails.isEmpty()) {
                throw new IllegalStateException("NC未返回上海组织的BOM数据，已停止同步并保留现有本地BOM");
            }
            if (oracleDetails.stream().anyMatch(d -> !PLANNING_ORGANIZATION.equals(d.getOrgName()))) {
                throw new IllegalStateException("NC返回了非上海组织的BOM数据，已停止同步并保留现有本地BOM");
            }
            log.info("从NC读取到 {} 条上海组织BOM明细", oracleDetails.size());

            // 收集所有父项（parentCode）
            Map<String, BomMaterial> parentMap = new HashMap<>();
            for (BomDetail d : oracleDetails) {
                parentMap.computeIfAbsent(d.getParentCode(), code -> {
                    BomMaterial m = new BomMaterial();
                    m.setInvCode(code);
                    m.setInvName(d.getParentName());
                    m.setVersion(d.getVersion());
                    m.setParentQty(d.getParentQty());
                    m.setOrgName(d.getOrgName());
                    m.setBodyCode(d.getBodyCode());
                    return m;
                });
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

            // 数据验证
            Map<String, Object> validation = validateBomData();
            log.info("数据验证完成: {}", validation);

            long elapsed = System.currentTimeMillis() - startTime;
            result.put("success", true);
            result.put("parentCount", parents.size());
            result.put("detailCount", oracleDetails.size());
            result.put("elapsed", elapsed + "ms");
            result.put("validation", validation);

            log.info("BOM数据同步完成，父项: {}, 子项: {}, 耗时: {}ms", parents.size(), oracleDetails.size(), elapsed);

        } catch (Exception e) {
            log.error("BOM数据同步失败，将回滚本次本地写入", e);
            throw new IllegalStateException("BOM同步失败，现有本地数据已保留：" + e.getMessage(), e);
        }

        return result;
    }

    private Map<String, Object> validateBomData() {
        Map<String, Object> validation = new HashMap<>();
        int totalDetails = bomLocalMapper.countAllDetails();
        int emptyParentCode = bomLocalMapper.countEmptyParentCode();
        int emptyChildCode = bomLocalMapper.countEmptyChildCode();
        int zeroQty = bomLocalMapper.countZeroQty();
        int selfRef = bomLocalMapper.countSelfReference();

        validation.put("totalDetails", totalDetails);
        validation.put("emptyParentCode", emptyParentCode);
        validation.put("emptyChildCode", emptyChildCode);
        validation.put("zeroQty", zeroQty);
        validation.put("selfReference", selfRef);
        validation.put("isValid", emptyParentCode == 0 && emptyChildCode == 0 && selfRef == 0);

        return validation;
    }

    public int getLocalCount() {
        return bomLocalMapper.countMaterials(null);
    }
}
