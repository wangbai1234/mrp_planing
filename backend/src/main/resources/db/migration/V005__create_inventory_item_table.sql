-- V005: Create inventory_item table for flat inventory import
-- Each row represents one material with multiple warehouse quantity columns

CREATE TABLE IF NOT EXISTS `inventory_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `snapshot_id` BIGINT NOT NULL,
    `product_line` VARCHAR(128),
    `inventory_category` VARCHAR(64) NOT NULL,
    `material_id` VARCHAR(64) NOT NULL,
    `project_model` VARCHAR(128),
    `material_name` VARCHAR(255),
    `supplier_name` VARCHAR(255) NOT NULL,
    `product_mode` VARCHAR(128),
    `odm_supplier_qty` BIGINT NOT NULL DEFAULT 0,
    `xa400_qty` BIGINT NOT NULL DEFAULT 0,
    `xa378_qty` BIGINT NOT NULL DEFAULT 0,
    `xa226_qty` BIGINT NOT NULL DEFAULT 0,
    `shipping_available_qty` BIGINT NOT NULL DEFAULT 0 COMMENT 'Auto-calculated: odm + xa400 + xa378 + xa226',
    `classification` VARCHAR(64),
    `barcode` VARCHAR(128),
    `remark` VARCHAR(512),
    `order_pending_qty` BIGINT NOT NULL DEFAULT 0,
    `sales_status` VARCHAR(64),
    `parent_record` VARCHAR(255),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_ii_snapshot_material` (`snapshot_id`, `material_id`),
    CONSTRAINT `fk_ii_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `inventory_snapshot`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
