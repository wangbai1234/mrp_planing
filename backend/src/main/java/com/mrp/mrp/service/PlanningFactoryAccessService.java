package com.mrp.mrp.service;

import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpMaterialPolicyNoticeMapper;
import com.mrp.mrp.repository.PlanningAccessMapper;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** A combined plan may only be opened or adjusted by users authorized for every included factory. */
@Service
public class PlanningFactoryAccessService {
    private final MrpMaterialPolicyNoticeMapper scopes;
    private final PlanningAccessMapper queries;
    private final MrpCalcBatchMapper batches;
    private final FactoryMasterService factoryMaster;
    public PlanningFactoryAccessService(MrpMaterialPolicyNoticeMapper scopes, PlanningAccessMapper queries,
                                        MrpCalcBatchMapper batches, FactoryMasterService factoryMaster) {
        this.scopes = scopes; this.queries = queries; this.batches = batches; this.factoryMaster = factoryMaster;
    }

    public void require(MrpCalcBatch batch) {
        if (batch == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排产版本不存在");
        List<String> factories = batches.selectFactoryCodesByBatchId(batch.getId());
        if (factories.isEmpty()) factories = ProductionCapacityRules.factories(batch);
        requireFactories(factories);
    }

    public void requireFactory(String factory) {
        requireFactories("ALL".equals(factory) ? factoryMaster.enabledCodes() : List.of(factory));
    }

    public boolean canReadFactory(String factory) {
        Long userId = CurrentUser.getUserId();
        if (userId == null || factory == null || factory.isBlank()) return false;
        List<String> factories = "ALL".equals(factory) ? factoryMaster.enabledCodes() : List.of(factory);
        return factories.stream().allMatch(code -> scopes.canReadFactory(userId, code) != 0);
    }

    private void requireFactories(List<String> factories) {
        Long userId = CurrentUser.getUserId();
        for (String factory : factories) {
            if (userId == null || scopes.canReadFactory(userId, factory) == 0)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看或调整该版本包含的工厂");
        }
    }

    public void requireBatch(Long id) { require(batches.selectById(id)); }

    public void requireBatchFactory(Long id, String factory) {
        MrpCalcBatch batch = batches.selectById(id);
        require(batch);
        List<String> factories = batches.selectFactoryCodesByBatchId(id);
        if (factories.isEmpty()) factories = ProductionCapacityRules.factories(batch);
        List<String> requested = "ALL".equals(factory) ? factories : List.of(factory);
        if (!factories.containsAll(requested))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "工厂不属于当前排产版本");
    }

    public Long requireResource(String resource, Long id) {
        Long batchId = queries.ownerBatch(resource, id);
        if (batchId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排产资源不存在");
        requireBatch(batchId);
        return batchId;
    }

    public List<MrpCalcBatch> visible(String status, String keyword, boolean current, Long offset, Integer limit) {
        return queries.visibleBatches(CurrentUser.getUserId(), status, keyword, current, offset, limit);
    }

    public long countVisible(String status, String keyword) {
        return queries.countVisibleBatches(CurrentUser.getUserId(), status, keyword);
    }
}
