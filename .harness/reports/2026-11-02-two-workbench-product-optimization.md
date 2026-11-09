# 双工作台产品优化任务报告

## 状态

REVIEW。A/B/C 已实现、自测并通过本轮独立修复复核；D 已完成真实引擎测试与页面只读联调，仍需专用业务批次的浏览器写入演练、外部同步核验和整体阶段复核。

## 本次完成

- 历史缺料默认按工厂、末级物料、终点类型、供应方式汇总；`detail=true` 保留来源整机与月份。历史库存风险显示未评估。
- 物控页紧凑呈现开放异常、默认下钻、待分配、库存风险、反馈的数量与入口；计算任务展示阶段、进度及失败原因；物料确认前显示与后端一致的阻断项。
- 增加物料汇总导出，沿用缺料服务端分页及筛选口径；日备料导出另行命名。导出分页统一使用 MySQL REPEATABLE_READ 只读快照；超过 Excel 15 位有效数字范围的数量按精确文本写入。
- 从物料来源与反馈回到整机时传递工厂、整机、来源月及可用的建议日期；排产日表接受定位条件，日格明细按来源月过滤。
- 独立入口、空/加载/错误状态及生产日历检查位置保留现有双工作台布局。
- 缺料页主加载、需求、待办、反馈及库存池/来源下钻增加切批代次保护；异步确认、反馈和分析结果不会回写到已切换的新批次。无库存池时优先展示已经识别出的库存风险。

## 修改文件

- `backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java`、`MrpShortageExportService.java`、`controller/MrpShortageExportController.java`
- `backend/src/test/java/com/mrp/mrp/ShortageSummaryIntegrationTest.java`
- `frontend/src/api/mrp.ts`、`frontend/src/views/mrp/ShortageWorkspace.vue`、`MrpWorkspace.vue`、`components/RollingPlanningGrid.vue`、`frontend/src/views/mrp/__tests__/ShortageWorkspace.test.ts`、`frontend/e2e/two-workbench-compact.spec.ts`
- `.harness/decision-log.md`、本报告、产品优化开发方案、问题台账

## 业务不变量

- v5 业务周与逐层扣库公式、排产和物料的同 batch/active run、双岗确认指纹、联合发布门禁不变。
- 历史批次只读，不更改计算事实；库存池和库存风险以整批为口径，来源月筛选仅影响需求量。
- “缺料”仅指末级净需求与库存风险；采购在途、承诺到货和交期覆盖未纳入。

## 验证命令与结果

```text
backend/./mvnw -q compile => 0
backend/./mvnw -q test => 0；93 suites、649 tests、0 failure/error
ShortageSummaryIntegrationTest => 6/6（含无池已知风险和大数导出）
backend/./mvnw -q -Dtest=TwoWorkbenchRealEngineFlowIntegrationTest,MaterialAnalysisRecalculationIntegrationTest test => 3/3
frontend/npm run build => 0
frontend/npm test -- --run => 107/107（含切批主加载、需求和异步确认交错）
frontend/npx playwright test e2e/phase2-mrp.spec.ts e2e/two-workbench-compact.spec.ts --project=chromium --workers=1 --reporter=line => 9/9
8180 重启后 /actuator/health => 200；5180 /shortage/121 => 200；git diff --check => 0
```

8180 重启后，历史 #75/run24 的缺料列表为 1 条，库存池/风险分别显示“历史未评估/未评估”。当前 v5 #121/run73 的缺料列表为 23 条；浏览器待办显示默认下钻 24、库存风险 25。物料汇总下载 HTTP 200，24 个工作表行（表头+23 条数据），来源月筛选后的列表/导出仍各为 23 条。浏览器从来源明细回跳到了 `/planning/121`，URL 携工厂、整机、来源月。整机负荷日格在 1440/1920 视口无文字溢出。

本轮修复后再次请求真实 API：列表 200、23 条，导出 200、4832 字节、XLSX ZIP 签名有效；第一页 16 条无库存池且有已知风险，浏览器展示“项库存风险”，没有误标为“未评估”。独立 Reviewer 对四项原阻断及切批确认竞态均给出 PASS，整体阶段保持 REVIEW。

### 本轮修复前后

- 修复前导出每页独立只读事务，同一 run 内重算可混入新旧分页；修复后导出方法用 REPEATABLE_READ 只读事务覆盖所有分页。
- 修复前无库存池优先写“未评估”，掩盖 `STOCK_POLICY_MISSING`；修复后已知风险优先，集成样例与浏览器真实批次均核对。
- 修复前大数经 `doubleValue()` 可能丢失六位小数；修复后超过 15 位有效数字写精确文本，集成样例 `123456789012.123456` 原样读回。
- 修复前切批旧请求可覆盖新批次列表、待办和复核状态；修复后请求代次与批次 ID 校验，单测验证 A 确认晚于 B 操作返回时 B 的复核与加载状态保持正确。

## 未完成与风险

- 隔离业务库的 #121 虽有 v5 run，但确认状态未完成且有待处理项；未替业务人员处理异常、改动排产或发布。真实引擎双岗闭环由专用 Testcontainers MySQL 用例验证，不能冒充浏览器写入 UAT。
- 当前反馈处理只记录排产员结论；是否需要结构化“接受建议/调整排产/拒绝建议”仍属产品后续项。本次不改反馈持久模型。
- Oracle/NC 同步源此前报 `ORA-17800`；本次 8180 健康 200 不证明外部同步已恢复。阶段 16-19 继续 REVIEW。
- 同一批次在异步确认/反馈/分析操作进行中触发普通刷新时，当前 `load()` 会提前清除 `saving`；可能短暂重新启用按钮。该边界未包含在本轮切批 PASS，已登记后续修复项。

## 下一步

- 在可写的专用 v5 UAT 批次完成物控反馈、排产调日、物料确认失效、重算与重审、联合发布的浏览器演练；补外部同步可用性证据。整体独立 Reviewer PASS 后再考虑 DONE。
