package com.mrp.bom.service;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.local.repository.BomSyncTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class BomSyncCommitService {
    private final BomService bomService;
    private final BomSyncTaskMapper tasks;
    public BomSyncCommitService(BomService bomService, BomSyncTaskMapper tasks) {
        this.bomService = bomService;
        this.tasks = tasks;
    }

    @Transactional
    public void replaceAndFinish(long taskId, String workerId, List<BomDetail> details) {
        if (tasks.isOwned(taskId, workerId) != 1) {
            throw new IllegalStateException("BOM同步任务已失去执行权");
        }
        Map<String, Object> result = bomService.replaceLocalData(details);
        @SuppressWarnings("unchecked")
        Map<String, Object> validation = (Map<String, Object>) result.get("validation");
        if (tasks.finish(taskId, workerId, ((Number) result.get("parentCount")).longValue(),
                ((Number) validation.get("zeroQty")).longValue(),
                ((Number) validation.get("selfReference")).longValue(),
                Boolean.TRUE.equals(validation.get("isValid"))) != 1) {
            throw new IllegalStateException("BOM同步任务完成状态更新失败");
        }
    }
}
