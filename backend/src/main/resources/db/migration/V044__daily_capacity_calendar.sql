-- Daily capacity uses frozen calendar and material units. Historical units stay unknown.
ALTER TABLE mrp_production_calendar
    ADD COLUMN capacity_unit VARCHAR(32) NULL,
    ADD COLUMN revision INT NOT NULL DEFAULT 0;
ALTER TABLE production_calendar_day ADD COLUMN capacity_unit VARCHAR(32) NULL;
CREATE TABLE mrp_calendar_material_unit (
    calendar_version_id BIGINT NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    unit VARCHAR(32) NULL,
    PRIMARY KEY (calendar_version_id, material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
