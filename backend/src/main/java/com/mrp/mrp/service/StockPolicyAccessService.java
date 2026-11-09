package com.mrp.mrp.service;

import com.mrp.common.response.PageResult;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.StockPolicy;
import com.mrp.mrp.repository.ConfigMasterMapper;
import com.mrp.mrp.repository.StockPolicyAccessMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/** HTTP scope checks and operations share one snapshot; background snapshot reads remain unchanged. */
@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class StockPolicyAccessService {
    private final StockPolicyAccessMapper access;
    private final ConfigMasterMapper configs;
    private final ConfigMasterService service;

    public StockPolicyAccessService(StockPolicyAccessMapper access, ConfigMasterMapper configs, ConfigMasterService service) {
        this.access=access; this.configs=configs; this.service=service;
    }

    public List<StockPolicy> list() { return access.visible(CurrentUser.getUserId(),null,null,null,null); }

    public PageResult<StockPolicy> page(String keyword, String warehouse, int page, int size) {
        page=Math.max(1,page); size=Math.max(1,Math.min(100,size));
        int offset;
        try { offset=Math.multiplyExact(page-1,size); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("页码超出范围"); }
        Long userId=CurrentUser.getUserId();
        return new PageResult<>(access.visible(userId,keyword,warehouse,offset,size),
                access.countVisible(userId,keyword,warehouse),page,size);
    }

    public List<Map<String,String>> warehouses() {
        return service.listStockPolicyWarehouses().stream()
                .filter(row -> access.canUseWarehouse(CurrentUser.getUserId(),row.get("code")) != 0).toList();
    }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public StockPolicy create(StockPolicy row) { requireWarehouse(row.getWarehouseCode()); return service.createStockPolicy(row); }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public StockPolicy update(Long id, StockPolicy row) { requireRecord(id); return service.updateStockPolicy(id,row); }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public void delete(Long id) { requireRecord(id); service.deleteStockPolicy(id); }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Map<String,Integer> importRows(List<StockPolicy> rows, String filename) {
        if (rows != null) rows.stream().map(StockPolicy::getWarehouseCode).distinct().forEach(this::requireWarehouse);
        return service.importStockPolicies(rows,filename);
    }

    private void requireRecord(Long id) {
        StockPolicy original=configs.selectStockPolicyById(id);
        if (original==null) throw new IllegalStateException("库存策略不存在");
        requireWarehouse(original.getWarehouseCode());
    }

    private void requireWarehouse(String warehouse) {
        if (access.canUseWarehouse(CurrentUser.getUserId(),warehouse==null?null:warehouse.trim())==0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权访问该仓库的库存策略");
    }
}
