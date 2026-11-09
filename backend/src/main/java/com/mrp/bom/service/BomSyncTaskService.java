package com.mrp.bom.service;

import com.mrp.bom.domain.BomSyncTask;
import com.mrp.bom.local.repository.BomSyncTaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class BomSyncTaskService {
    private final BomSyncTaskMapper tasks;

    public BomSyncTaskService(BomSyncTaskMapper tasks) {
        this.tasks = tasks;
    }

    public BomSyncTask start() {
        BomSyncTask active = tasks.selectActive();
        if (active != null) return active;
        BomSyncTask created = new BomSyncTask();
        try {
            tasks.insertPending(created);
            return tasks.selectById(created.getId());
        } catch (DuplicateKeyException concurrentRequest) {
            return tasks.selectActive();
        }
    }

    public BomSyncTask latest() { return tasks.selectLatest(); }

    public BomSyncTask get(long id) { return tasks.selectById(id); }
}
