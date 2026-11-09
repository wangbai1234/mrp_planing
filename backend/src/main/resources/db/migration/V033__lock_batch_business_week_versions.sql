-- V033: A calculation may span several source months. Lock the published business-week
-- version for every source month instead of reading mutable "latest" configuration later.

CREATE TABLE mrp_batch_business_week_scope (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    business_month DATE NOT NULL,
    business_week_version_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mbbws_batch_month (batch_id, business_month),
    KEY idx_mbbws_week_version (business_week_version_id),
    CONSTRAINT fk_mbbws_batch FOREIGN KEY (batch_id)
        REFERENCES mrp_calc_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_mbbws_week_version FOREIGN KEY (business_week_version_id)
        REFERENCES business_week_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
