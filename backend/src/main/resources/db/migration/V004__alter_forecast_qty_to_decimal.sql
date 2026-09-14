-- V004: 将forecast_qty从BIGINT改为DECIMAL以支持小数
ALTER TABLE `forecast_detail` MODIFY COLUMN `forecast_qty` DECIMAL(18,6) NOT NULL DEFAULT 0;
