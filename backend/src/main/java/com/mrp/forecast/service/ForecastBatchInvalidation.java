package com.mrp.forecast.service;

import org.springframework.jdbc.core.JdbcTemplate;

final class ForecastBatchInvalidation {
    private ForecastBatchInvalidation() {}

    static int staleBatches(JdbcTemplate jdbc, Long effectiveVersionId) {
        return jdbc.update("UPDATE mrp_calc_batch SET status='STALE', version=version+1 "
                + "WHERE (forecast_version_id IS NULL OR forecast_version_id<>?) AND status NOT IN "
                + "('PUBLISHED','SUPERSEDED','CANCELLED','FAILED','STALE')", effectiveVersionId);
    }
}
