# Phase 1 规则追踪矩阵

本矩阵是设计和验收索引。Mimo 在 Prompt 00 中补充实际表名、类名、API 和测试名；后续阶段不得删除映射，只能将 `TBD` 替换为真实证据。

| 规则 | 领域模块 | 数据证据 | API/UI 证据 | 自动化测试 | 状态 |
|---|---|---|---|---|---|
| R001 月可排量 MAX(0) | planning | plan_detail.system_quantity | GET /plans/{id}/grid | 规则单测 + 黄金数据(TC001/TC002) | PLANNED@04 |
| R002 月度拆周、余数前置 | planning | plan_detail.slot, is_carry | 12周网格 carry 标签 | 规则单测余数/整除(TC003/TC004) | PLANNED@04 |
| R003 下月第一份提前到上月W4 | planning | plan_detail.physical_month, source_month | 提前量标签与来源合计 | 规则单测跨月(TC005) | PLANNED@04 |
| R004 人工调整、留痕、恢复 | planning/audit | plan_override + audit_log | POST/DELETE overrides + 抽屉 | 服务集成测试(TC006) | PLANNED@05 |
| R005 当前周起固定12周 | planning | plan_version.current_week_start | grid + 定位当前周 | API 测试 + 前端验收 | PLANNED@04,06 |
| R006 经营计划半月更新及开关 | forecast/planning | forecast_version + calc_task | POST /forecast-imports + 开关 | Excel 测试 + API(TC008) | PLANNED@03,05 |
| R007 库存每日更新及开关 | inventory/planning | inventory_snapshot + calc_task | POST /inventory-imports + 开关 | Excel 测试 + API(TC009) | PLANNED@03,05 |
| R008 CRM按料号+月份累计 | shipment | shipment_batch/detail | POST /shipment-sync-tasks | 服务集成测试(TC010) | PLANNED@03 |
| R009 按产线周产能 | capacity/planning | capacity_version/line | GET/POST/PUT capacity-lines | API 测试 | PLANNED@03,05 |
| R010 草稿→已发布、发布后可改 | planning/audit | plan_version.status + audit_log | POST /plans/{id}/publish | API 测试(TC012) | PLANNED@05 |
| R011 经营计划版本 | forecast | forecast_version | GET /forecast-versions | 服务集成测试(TC008) | PLANNED@03 |
| R012 重算生成版本、人工修改不生成、不可回滚 | planning/versioning | plan_version + plan_override | POST /recalculations + 对比 | 规则单测 + API(TC007) | PLANNED@04,05 |
| R013 负值归零 | planning | plan_detail.system_quantity=0 | grid 计算详情 | 规则单测边界(TC002) | PLANNED@04 |
| R014 超产能人工调整 | capacity/planning | exception fields | 红色异常 + 调整 | API 测试 + 前端验收(TC013) | PLANNED@05,06 |
| R015 特殊月份人工处理 | planning | plan_override | 人工调整入口 | 前端验收(TC014) | PLANNED@06 |
| R016 工厂分开、多仓汇总、排除不良品 | inventory/planning | inventory_detail | 库存口径 | 服务集成测试(TC009) | PLANNED@03 |
| R017 自定义导出字段 | importexport | export_task | POST /plans/{id}/exports + 字段选择器 | API 测试(TC011) | PLANNED@05 |

完成定义：每行必须同时存在实现证据和至少一个测试证据。页面截图不能替代规则测试。
