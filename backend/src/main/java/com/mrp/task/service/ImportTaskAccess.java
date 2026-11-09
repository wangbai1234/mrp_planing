package com.mrp.task.service;

import com.mrp.common.security.CurrentUser;
import com.mrp.task.domain.ImportTask;
import com.mrp.task.repository.ImportTaskMapper;
import java.util.Objects;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Staging files belong to their uploader, including previews, errors and confirmation. */
public final class ImportTaskAccess {
    private ImportTaskAccess() {}

    public static void requireActor(Long userId) {
        if (userId == null || !Objects.equals(userId, CurrentUser.getUserId())) {
            throw forbidden();
        }
    }

    public static String requestKey(String prefix, String checksum, Long userId) {
        requireActor(userId);
        return prefix + userId + ":" + checksum;
    }

    public static ImportTask requireOwner(ImportTaskMapper mapper, Long taskId, String type, String prefix) {
        Long userId = CurrentUser.getUserId();
        if (userId == null) throw forbidden();
        ImportTask task = mapper.selectById(taskId);
        return checkOwner(task, userId, type, prefix);
    }

    /** Caller must hold the confirmation transaction until formal data and task commit together. */
    public static ImportTask lockForConfirmation(ImportTaskMapper mapper, Long taskId, String type, String prefix) {
        Long userId = CurrentUser.getUserId();
        if (userId == null) throw forbidden();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Import confirmation requires a transaction");
        }
        ImportTask task = checkOwner(mapper.selectByIdForUpdate(taskId), userId, type, prefix);
        if (!ImportTask.STATUS_PENDING.equals(task.status()) && !ImportTask.STATUS_SUCCEEDED.equals(task.status())) {
            throw conflict();
        }
        return task;
    }

    public static <T> void completeAfterCommit(ImportTaskMapper mapper, ImportTask task, Long resourceId,
                                              Map<Long, T> cache, T staging) {
        if (mapper.completePending(task.id(), resourceId, task.version()) != 1) throw conflict();
        // An enclosing transaction may still roll back after this service method returns.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.remove(task.id(), staging); }
        });
    }

    public static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "导入任务状态或结果已变化，请刷新后重试");
    }

    private static ImportTask checkOwner(ImportTask task, Long userId, String type, String prefix) {
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导入任务不存在");
        if (!Objects.equals(userId, task.createdBy()) || !type.equals(task.taskType())
                || task.requestKey() == null || !task.requestKey().startsWith(prefix)) {
            throw forbidden();
        }
        return task;
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问或确认该导入任务");
    }
}
