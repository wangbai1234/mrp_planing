package com.mrp.inventory.service;

import com.mrp.common.response.PageResult;
import com.mrp.common.security.CurrentUser;
import com.mrp.inventory.domain.InventoryItem;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.repository.InventoryItemMapper;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.inventory.repository.InventorySnapshotAccessMapper;
import com.mrp.mrp.domain.MrpInventoryBalance;
import com.mrp.mrp.service.InventoryNormalizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** HTTP-facing snapshot operations; background normalization remains independent of the current user. */
@Service
public class InventorySnapshotAccessService {
    private final InventorySnapshotAccessMapper access;
    private final InventoryMapper snapshots;
    private final InventoryItemMapper items;
    private final InventoryNormalizationService normalization;

    public InventorySnapshotAccessService(InventorySnapshotAccessMapper access, InventoryMapper snapshots,
            InventoryItemMapper items, InventoryNormalizationService normalization) {
        this.access=access; this.snapshots=snapshots; this.items=items; this.normalization=normalization;
    }

    private void require(Long id) {
        Long userId=CurrentUser.getUserId();
        if (userId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问库存快照");
        if (snapshots.selectSnapshotById(id) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "库存快照不存在");
        if (access.canRead(userId,id) == 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该库存快照包含的全部工厂");
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public List<InventorySnapshot> list(String warehouseCode) {
        return access.visible(CurrentUser.getUserId(),null,warehouseCode,null,null);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<InventorySnapshot> page(String keyword, int page, int pageSize) {
        int offset=offset(page,pageSize);
        return new PageResult<>(access.visible(CurrentUser.getUserId(),keyword,null,offset,pageSize),
                Math.toIntExact(access.countVisible(CurrentUser.getUserId(),keyword)),page,pageSize);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<InventoryItem> itemsPage(Long id, int page, int pageSize) {
        int offset=offset(page,pageSize); require(id);
        return new PageResult<>(items.selectPageBySnapshotId(id,offset,pageSize),items.countBySnapshotId(id),page,pageSize);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<InventoryItem> itemsPage(Long id, String keyword, int page, int pageSize) {
        int offset=offset(page,pageSize); require(id);
        String query=keyword == null ? "" : keyword.trim();
        if (query.isEmpty()) return itemsPage(id,page,pageSize);
        return new PageResult<>(items.selectFilteredPageBySnapshotId(id,query,offset,pageSize),
                items.countFilteredBySnapshotId(id,query),page,pageSize);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public List<InventoryItem> items(Long id) { require(id); return items.selectBySnapshotId(id); }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public int countItems(Long id) { require(id); return items.countBySnapshotId(id); }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public List<MrpInventoryBalance> balances(Long id) { require(id); return normalization.getNormalizedBalances(id); }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public List<MrpInventoryBalance> normalize(Long id, String factoryCode, String inventoryOrg) {
        require(id);
        return normalization.normalizeSnapshot(id,factoryCode,inventoryOrg);
    }

    private int offset(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new IllegalArgumentException("页码须大于0，每页条数须为1至100");
        try { return Math.multiplyExact(page-1,pageSize); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("页码超出范围"); }
    }
}
