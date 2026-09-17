-- V015: plan_version 增加拆分维度字段，plan_detail 增加来源整机字段
-- 幂等：使用 IF NOT EXISTS 语义（ALTER TABLE ADD COLUMN 在列已存在时会报错，需确认列不存在）

-- plan_version: 拆分维度
ALTER TABLE plan_version
  ADD COLUMN split_categories JSON COMMENT '拆分维度分类编码数组，如["02","02001"]',
  ADD COLUMN split_category_names VARCHAR(500) COMMENT '拆分维度显示名称';

-- plan_detail: 来源整机
ALTER TABLE plan_detail
  ADD COLUMN root_material_code VARCHAR(64) COMMENT '来源整机料号',
  ADD COLUMN root_material_name VARCHAR(255) COMMENT '来源整机名称';

-- 索引：按版本+整机查询
ALTER TABLE plan_detail
  ADD KEY idx_pd_root_material (plan_version_id, root_material_code);
