# Phase 1 规则追踪矩阵

本矩阵是设计和验收索引。Mimo 在 Prompt 00 中补充实际表名、类名、API 和测试名；后续阶段不得删除映射，只能将 `TBD` 替换为真实证据。

| 规则 | 领域模块 | 数据证据 | API/UI 证据 | 自动化测试 | 状态 |
|---|---|---|---|---|---|
| R001 月可排量 MAX(0) | planning | plan_detail.system_quantity | GET /plans/{id}/grid | PlanningCalculatorTest(27) + TC001/TC002 PASS | VERIFIED |
| R002 月度拆周、余数前置 | planning | plan_detail.slot, is_carry | 12周网格 carry 标签 | PlanningCalculatorTest(余数/整除) + TC003/TC004 PASS | VERIFIED |
| R003 下月第一份提前到上月W4 | planning | plan_detail.physical_month, source_month | 提前量标签与来源合计 | PlanningCalculatorTest(跨月) + TC005 PASS | VERIFIED |
| R004 人工调整、留痕、恢复 | planning/audit | plan_override + audit_log | POST/DELETE overrides + 抽屉 | PlanningServiceIntegrationTest(override) PASS, TC006 待E2E | PARTIAL |
| R005 当前周起固定12周 | planning | plan_version.current_week_start | grid + 定位当前周 | PlanningApiTest(9) + WeekPlanTest(11) PASS | VERIFIED |
| R006 经营计划半月更新及开关 | forecast/planning | forecast_version + calc_task | POST /forecast-imports + 开关 | ForecastExcelParserTest(9) + TC008 PASS | VERIFIED |
| R007 库存每日更新及开关 | inventory/planning | inventory_snapshot + calc_task | POST /inventory-imports + 开关 | InventoryExcelParserTest(9) + TC009 PASS | VERIFIED |
| R008 CRM按料号+月份累计 | shipment | shipment_batch/detail | POST /shipment-sync-tasks | TC010 FAIL (API未实现) | BLOCKED |
| R009 按产线周产能 | capacity/planning | capacity_version/line | GET/POST/PUT capacity-lines | CapacityServiceIntegrationTest(8) PASS | VERIFIED |
| R010 草稿→已发布、发布后可改 | planning/audit | plan_version.status + audit_log | POST /plans/{id}/publish | PlanningApiTest(publish) PASS, TC012 E2E FAIL(按钮disabled) | PARTIAL |
| R011 经营计划版本 | forecast | forecast_version | GET /forecast-versions | ForecastExcelParserTest + TC008 PASS | VERIFIED |
| R012 重算生成版本、人工修改不生成、不可回滚 | planning/versioning | plan_version + plan_override | POST /recalculations + 对比 | PlanningCalculatorTest + PlanningApiTest(recalc) PASS | VERIFIED |
| R013 负值归零 | planning | plan_detail.system_quantity=0 | grid 计算详情 | PlanningCalculatorTest(MAX(0边界)) + TC002 PASS | VERIFIED |
| R014 超产能人工调整 | capacity/planning | exception fields | 红色异常 + 调整 | CapacityServiceIntegrationTest + TC013 PASS | VERIFIED |
| R015 特殊月份人工处理 | planning | plan_override | 人工调整入口 | TC014 功能未实现 | BLOCKED |
| R016 工厂分开、多仓汇总、排除不良品 | inventory/planning | inventory_detail | 库存口径 | InventoryExcelParserTest + TC009 PASS | VERIFIED |
| R017 自定义导出字段 | importexport | export_task | POST /plans/{id}/exports + 字段选择器 | ImportApiTest(3) PASS, TC011 待E2E | PARTIAL |

完成定义：每行必须同时存在实现证据和至少一个测试证据。页面截图不能替代规则测试。
