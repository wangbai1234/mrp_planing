-- Increase request_key column size from VARCHAR(128) to VARCHAR(256)
-- to accommodate longer field lists in export/import/calc task requests.

ALTER TABLE export_task MODIFY COLUMN `request_key` VARCHAR(256) NOT NULL;
ALTER TABLE import_task MODIFY COLUMN `request_key` VARCHAR(256) NOT NULL;
ALTER TABLE calc_task MODIFY COLUMN `request_key` VARCHAR(256) NOT NULL;
