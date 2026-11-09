-- New batches read the current synced BOM; historical batches keep their snapshot id.
ALTER TABLE mrp_calc_batch
    ADD COLUMN bom_source_checksum VARCHAR(64) DEFAULT NULL COMMENT 'Current synced BOM checksum for optimistic input consistency';
