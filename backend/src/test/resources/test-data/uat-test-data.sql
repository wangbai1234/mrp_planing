-- UAT Test Data Preparation Script
-- This script prepares test data for all remaining UAT cases

-- ========================================
-- TC005: 提前一周滚动测试数据
-- ========================================

-- 准备8月、9月、10月的forecast数据
-- 需要通过API导入经营计划Excel

-- ========================================
-- TC006: 人工调整测试数据
-- ========================================

-- 确保有排产计划数据
-- 需要通过API生成排产计划

-- ========================================
-- TC007: 人工调整 + Forecast变更测试数据
-- ========================================

-- 准备初始排产计划和forecast数据

-- ========================================
-- TC010: 已出货记录测试数据
-- ========================================

-- 创建已出货记录表（如果不存在）
CREATE TABLE IF NOT EXISTS `shipment_record` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `material_id` VARCHAR(64) NOT NULL COMMENT '物料ID',
  `factory_code` VARCHAR(32) NOT NULL COMMENT '工厂代码',
  `shipment_month` VARCHAR(7) NOT NULL COMMENT '出货月份 YYYY-MM',
  `quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '出货数量',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_material_factory_month` (`material_id`, `factory_code`, `shipment_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已出货记录表';

-- 插入测试出货数据
INSERT INTO `shipment_record` (`material_id`, `factory_code`, `shipment_month`, `quantity`) VALUES
('6830AA800561', 'YH', '2026-08', 100),
('6830AA800561', 'YH', '2026-09', 50),
('6830AA800305', 'YH', '2026-08', 200),
('6830AA800305', 'YH', '2026-09', 150),
('6830AA800306', 'YH', '2026-08', 80),
('6830AA800306', 'YH', '2026-09', 60),
('6830AA800318', 'YH', '2026-08', 30),
('6830AA800318', 'YH', '2026-09', 20),
('6830AA800382', 'YH', '2026-08', 50),
('6830AA800382', 'YH', '2026-09', 40),
('6830AA800384', 'YH', '2026-08', 15),
('6830AA800384', 'YH', '2026-09', 10),
('6830AA800405', 'YH', '2026-08', 100),
('6830AA800405', 'YH', '2026-09', 80),
('6830AA800437', 'YH', '2026-08', 150),
('6830AA800437', 'YH', '2026-09', 120),
('6830AA800438', 'YH', '2026-08', 60),
('6830AA800438', 'YH', '2026-09', 45),
('6830AA800496', 'YH', '2026-08', 180),
('6830AA800496', 'YH', '2026-09', 140),
('6830AA800509', 'YH', '2026-08', 400),
('6830AA800509', 'YH', '2026-09', 300),
('6830AA800530', 'YH', '2026-08', 1500),
('6830AA800530', 'YH', '2026-09', 1200),
('6830AA800551', 'YH', '2026-08', 100),
('6830AA800551', 'YH', '2026-09', 80),
('6830AA800559', 'YH', '2026-08', 2500),
('6830AA800559', 'YH', '2026-09', 2000),
('6830AA800561', 'YH', '2026-08', 1500),
('6830AA800561', 'YH', '2026-09', 1200),
('6830AA800562', 'YH', '2026-08', 1500),
('6830AA800562', 'YH', '2026-09', 1200),
('6830AA800626', 'YH', '2026-08', 700),
('6830AA800626', 'YH', '2026-09', 550);

-- ========================================
-- TC013: 产能超限测试数据
-- ========================================

-- 确保产能配置表存在
CREATE TABLE IF NOT EXISTS `capacity_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `factory_code` VARCHAR(32) NOT NULL COMMENT '工厂代码',
  `line_code` VARCHAR(32) NOT NULL COMMENT '产线代码',
  `line_name` VARCHAR(64) COMMENT '产线名称',
  `weekly_capacity` BIGINT NOT NULL DEFAULT 0 COMMENT '每周产能',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_factory_line` (`factory_code`, `line_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产能配置表';

-- 插入产能配置数据
INSERT INTO `capacity_config` (`factory_code`, `line_code`, `line_name`, `weekly_capacity`, `is_active`) VALUES
('YH', 'LINE01', '永惠产线1', 500, 1),
('YH', 'LINE02', '永惠产线2', 500, 1),
('YH', 'LINE03', '永惠产线3', 300, 1),
('APK', 'LINE01', '爱培科产线1', 400, 1),
('APK', 'LINE02', '爱培科产线2', 400, 1);

-- ========================================
-- 验证测试数据
-- ========================================

-- 验证出货记录
SELECT 'shipment_record' as table_name, COUNT(*) as record_count FROM shipment_record;

-- 验证产能配置
SELECT 'capacity_config' as table_name, COUNT(*) as record_count FROM capacity_config;

-- 验证现有排产数据
SELECT 'plan_version' as table_name, COUNT(*) as record_count FROM plan_version;

-- 验证物料数据
SELECT 'material' as table_name, COUNT(*) as record_count FROM material;

-- 验证经营计划数据
SELECT 'forecast_version' as table_name, COUNT(*) as record_count FROM forecast_version;
