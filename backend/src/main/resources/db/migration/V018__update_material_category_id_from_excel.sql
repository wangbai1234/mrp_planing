-- V015: 根据Excel数据更新物料二级分类ID
-- 数据来源：副本70m物料权限.xlsx（料号→CODE映射）
-- 幂等：只更新未设置分类的物料，或根据最新数据更新

-- 创建临时表存储Excel映射数据
CREATE TEMPORARY TABLE IF NOT EXISTS `temp_material_category_mapping` (
    `material_code` VARCHAR(64) NOT NULL,
    `category_code` VARCHAR(32) NOT NULL,
    INDEX `idx_temp_material_code` (`material_code`),
    INDEX `idx_temp_category_code` (`category_code`)
) ENGINE=Memory;

-- 注意：此脚本需要通过应用程序执行，因为需要解析Excel文件
-- 实际更新逻辑在MaterialService.updateCategoryFromExcel()方法中实现

-- 更新物料二级分类ID
-- 通过material_code关联临时表，再通过category_code关联material_category表
UPDATE material m
    INNER JOIN temp_material_category_mapping tm ON tm.material_code = m.material_code
    INNER JOIN material_category mc ON mc.code = tm.category_code AND mc.is_deleted = 0 AND mc.level = 2
SET m.material_category_id = mc.id,
    m.updated_at = CURRENT_TIMESTAMP(3)
WHERE m.is_deleted = 0
    AND (m.material_category_id IS NULL OR m.material_category_id != mc.id);

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS `temp_material_category_mapping`;
