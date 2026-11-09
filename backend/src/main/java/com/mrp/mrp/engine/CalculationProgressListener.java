package com.mrp.mrp.engine;

/** Observes completed work without changing calculation inputs or results. */
@FunctionalInterface
public interface CalculationProgressListener {
    CalculationProgressListener NONE = (percent, stage, completed, total) -> {};

    void report(int percent, String stage, int completed, int total);

    default void report(int percent, String stage) {
        report(percent, stage, 0, 0);
    }

    static int within(int start, int end, int completed, int total) {
        return total <= 0 ? end : start + (int) ((long) (end - start) * completed / total);
    }
}
