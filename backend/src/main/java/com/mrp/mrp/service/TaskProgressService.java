package com.mrp.mrp.service;

import com.mrp.mrp.engine.CalculationProgressListener;
import com.mrp.task.repository.CalcTaskMapper;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Progress commits independently of candidate-result transactions so polling can see it. */
@Service
public class TaskProgressService {
    private static final Logger log = LoggerFactory.getLogger(TaskProgressService.class);
    private final CalcTaskMapper tasks;
    private final TransactionTemplate transaction;

    public TaskProgressService(CalcTaskMapper tasks,
            @Qualifier("transactionManager") PlatformTransactionManager manager) {
        this.tasks = tasks;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CalculationProgressListener listener(Long taskId, String workerId, int claimVersion) {
        return new ThrottledProgress((percent, stage, completed, total) -> {
            String phase = total > 0 ? stage + "（" + completed + "/" + total + "）" : stage;
            transaction.executeWithoutResult(status -> tasks.updateOwnedProgress(
                    taskId, workerId, claimVersion, percent, phase));
        }, System::nanoTime);
    }

    static final class ThrottledProgress implements CalculationProgressListener {
        private final CalculationProgressListener sink;
        private final LongSupplier clock;
        private String lastStage;
        private int lastPercent = -1;
        private long lastUpdate;

        ThrottledProgress(CalculationProgressListener sink, LongSupplier clock) {
            this.sink = sink;
            this.clock = clock;
        }

        @Override
        public void report(int percent, String stage, int completed, int total) {
            int bounded = Math.max(lastPercent, Math.min(99, Math.max(0, percent)));
            long now = clock.getAsLong();
            // Stage boundaries are immediate; within a stage write at most once per second.
            if (stage.equals(lastStage) && (now - lastUpdate < 1_000_000_000L || bounded == lastPercent)) return;
            lastStage = stage;
            lastPercent = bounded;
            lastUpdate = now;
            try {
                sink.report(bounded, stage, completed, total);
            } catch (RuntimeException error) {
                // A monitoring failure must not roll back valid business results.
                log.warn("Unable to persist calculation progress at {}% ({})", bounded, stage, error);
            }
        }
    }
}
