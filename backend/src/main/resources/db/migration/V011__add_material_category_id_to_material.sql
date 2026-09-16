-- V011: 物料表增加分类ID字段

ALTER TABLE `material`
    ADD COLUMN `material_category_id` BIGINT DEFAULT NULL COMMENT '物料分类ID（关联material_category表）' AFTER `category`;

CREATE INDEX `idx_material_category_id` ON `material` (`material_category_id`);
