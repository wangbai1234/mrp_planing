package com.mrp.task.service;

import com.mrp.task.domain.ImportTask;
import com.mrp.task.repository.ImportTaskMapper;
import org.springframework.dao.DuplicateKeyException;

import java.util.Objects;

/** Resolve the durable request-key identity without relying on a pooled connection's last id. */
public final class ImportTaskCreation {
    private ImportTaskCreation() {}

    public static ImportTask createOrGet(ImportTaskMapper mapper, ImportTask candidate) {
        ImportTaskAccess.requireActor(candidate.createdBy());
        DuplicateKeyException collision = null;
        try {
            if (mapper.insert(candidate) != 1) throw ImportTaskAccess.conflict();
        } catch (DuplicateKeyException ex) {
            // Another first upload may have inserted the same user/flow/checksum key.
            // Do not update that row or infer its id from the connection pool.
            collision = ex;
        }
        ImportTask persisted = mapper.selectByRequestKey(candidate.requestKey());
        if (persisted == null) {
            if (collision != null) throw collision;
            throw ImportTaskAccess.conflict();
        }
        if (!Objects.equals(persisted.requestKey(), candidate.requestKey())
                || !Objects.equals(persisted.createdBy(), candidate.createdBy())
                || !Objects.equals(persisted.taskType(), candidate.taskType())
                || !Objects.equals(persisted.inputChecksum(), candidate.inputChecksum())) {
            throw ImportTaskAccess.conflict();
        }
        return persisted;
    }
}
