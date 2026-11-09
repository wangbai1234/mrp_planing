# 物料策略默认下钻与两策略物料选择补充任务报告

## 状态

REVIEW。用户已确认方案并授权实施。本次代码实现、自我复核、编译、测试和真实服务/浏览器验收通过；12/14/16/17 阶段保持 REVIEW，未宣称独立阶段 Reviewer PASS 或整项目 DONE。

## 本次完成

- `/mrp/material-policies` 和 `/mrp/stock-policies` 新增弹窗共用 `PolicyMaterialPicker`，查询物料管理的真实 `material` 数据，支持编码、名称、规格模糊搜索、服务端分页、加载/空/错误重试、选中完整标签、防抖与过期响应防护。停用或已删除物料不可新建配置，保存端再次校验。
- 物料策略新增/编辑/筛选/导入模板去掉供应方式和下钻策略的「未维护」选项，新增不预填默认值，必填供应方式与下钻策略。供应只允许自制/外购/委外，下钻只允许继续/停止。
- 后端新增/编辑/导入和旧版本明细保存/发布均执行物料引用与枚举校验；Excel 错误保留原始行号、稀疏空行，错误原因位于最后一列。
- 按经营计划整机和批次冻结 BOM/策略逐层计算：缺下钻策略默认继续直到结构叶子；有效 STOP 即使供应方式缺失仍生效。缺物料策略不形成阻断 issue，空物料策略表可以冻结为输入并创建批次。
- 计算完成后保存缺失清单，按工厂+物料去重，保留缺失字段、实际下钻策略、全部不同整机/月/BOM 路径。工作台自动刷新并提示缺失数量，提供清单分页/搜索/展开来源和维护入口。
- 清单与 run 绑定，只查询 active run；发布复制源 run 清单，失败清理候选 run，旧计算/快照不回填或改写。
- 日排产和需求追溯中的缺供应显示「未配置」；历史内部供应哨兵保留以兼容 NOT NULL 结果字段。
- 修复验收发现的 HTTP 状态异常被泛化为 500 的问题，403/404 保留真实状态和统一 API 错误体，新增 MockMvc 响应测试。
- 同步计算契约、API 补充契约、需求文档、规则库、决策记录、来源裁决和原型，workflow 记录本次补充任务 REVIEW。

## 修改文件（本需求范围）

仓库存在大量此前修改，下列仅列本需求所涉及的实现，不把整仓 diff 归为本任务。

### 后端

- `backend/src/main/java/com/mrp/mrp/service/MaterialPolicyRules.java`
- `backend/src/main/java/com/mrp/mrp/service/ConfigMasterService.java`、`MrpConfigurationService.java`、`ConfigSnapshotService.java`
- `backend/src/main/java/com/mrp/mrp/controller/ConfigMasterController.java`、`MrpBatchController.java`
- `backend/src/main/java/com/mrp/mrp/repository/ConfigMasterMapper.java`、`backend/src/main/resources/mapper/mrp/ConfigMasterMapper.xml`
- `backend/src/main/java/com/mrp/mrp/engine/NetRequirementEngine.java`
- `backend/src/main/java/com/mrp/mrp/domain/MrpRequirementDetail.java`、`MrpMaterialPolicyNotice.java`
- `backend/src/main/java/com/mrp/mrp/repository/MrpMaterialPolicyNoticeMapper.java`、`backend/src/main/resources/mapper/mrp/MrpMaterialPolicyNoticeMapper.xml`、`MrpRequirementDetailMapper.xml`
- `backend/src/main/java/com/mrp/mrp/service/MaterialPolicyNoticeService.java`、`MrpCalculationService.java`、`BatchOrchestratorService.java`、`MrpTaskWorkerService.java`
- `backend/src/main/java/com/mrp/importexport/excel/ConfigExcelParser.java`、`TemplateGenerator.java`
- `backend/src/main/java/com/mrp/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/resources/db/migration/V043__material_policy_fallback_notices.sql`（新迁移；验收后不可修改）

### 前端

- `frontend/src/api/mrp.ts`
- `frontend/src/views/mrp/components/PolicyMaterialPicker.vue`、`MaterialPolicyNoticePanel.vue`
- `frontend/src/views/mrp/MaterialPolicyConfig.vue`、`StockPolicyConfig.vue`、`MrpWorkspace.vue`、`MrpConfigurationCenter.vue`
- `frontend/src/views/mrp/components/DailyPlanningGrid.vue`、`RequirementTrace.vue`
- `frontend/src/utils/mrpDisplay.ts`

### 测试与文档

- `MaterialPolicyFallbackTest`、`MaterialPolicyNoticeServiceTest`、`ConfigMasterServiceTest`、`BatchOrchestratorServiceTest`、`MrpTaskWorkerServiceTest`、`ConfigExcelParserTest`、`GlobalExceptionHandlerTest`。
- `.harness/calculation-contract.md`、`.harness/api-contract.md`、`.harness/source-precedence.md` C017、`.harness/decision-log.md`、`.harness/prompts/12-masterdata-and-decisions.md`、`.harness/workflow.yaml`。
- `assets/MRP业务规则库.md`、`docs/MRP需求文档v4.0（Phase 2）.md`、`prototype/MRP日排产Phase2可交互原型.html`（已确认新清单 tab/panel 和两个弹窗存在）。

## 业务不变量及真实链路复核

1. 物料选择经 `mrpApi.searchPolicyMaterials` → Controller → `ConfigMasterService.searchPolicyMaterials` → `MaterialMapper.selectPage/countTotal`；SQL 同时限制 `is_deleted=0` 与 `is_active=true`，关键词匹配编码/名称/规格等物料字段。
2. 新配置写入经 `MaterialPolicyRules.validate` 和有效物料校验；展示历史值的类型仍可兼容 `UNMAINTAINED`，可写类型不接受它。
3. `BatchOrchestratorService` 预检与 `MrpCalculationService` 计算均以冻结策略版本构建 Engine；Engine 只按有效 STOP 停止。缺供应不修改下钻语义。
4. Engine 明细保存 `effective_explosion_policy` 与 `policy_source`；计算后 `MaterialPolicyNoticeService.record` 按完整明细生成清单，不生成阻断决策。
5. 清单 SQL 通过 active run 查询，角色 ALL/FACTORY 数据域校验；发布 `copyRun`，Worker 失败只清理候选 run。
6. 本次只放宽物料策略缺失；分类角色、库存及其他既有业务门禁仍按原规则执行。
7. 数量用 BigDecimal/DECIMAL，冻结版本与历史发布结果不因当前主数据修改而变化。

## 「未维护」使用位置与处置

| 位置 | 处置 |
| --- | --- |
| 物料策略新增/编辑下拉、列表筛选 | 移除选项；历史缺失显示待补充 |
| 物料策略导入说明、Excel 模板与解析、后端写入 | 去掉可写枚举并严格拒绝 |
| 配置中心供应方式描述 | 移除「委外与未维护」旧描述 |
| 日排产、需求追溯供应显示 | 缺失统一显示「未配置」 |
| 计算策略缺失阻断分支、空表门禁 | 改默认继续并保存结果提示 |
| 历史内部值、结果供应哨兵、历史 issue 标签 | 保留兼容，不作为新增选项；不改旧快照 |
| 分类角色、生产日历、业务周、库存策略等「未维护」提示 | 属于其他配置语义，保留 |

## 验证命令与结果

环境：Java 21.0.12.1、Node 24.21.0；未安装、切换或修改技术栈配置，未新增依赖。

```text
cd backend && ./mvnw compile
BUILD SUCCESS
cd backend && ./mvnw test
Tests run: 255, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
cd frontend && npm run build
SUCCESS（保留现有大 chunk 提示）
cd frontend && npm test
4 files, 24 tests passed
```

真实启动：后端使用验收库 URL 通过 `MRP_DB_URL`、`MRP_SERVER_PORT=8081 ./mvnw spring-boot:run` 启动；前端 Vite 5181；系统 Chrome 浏览器真实请求转发到 8081，未模拟返回业务数据。HTTP 异常修复后已重新编译、全测、重启 8081 并复测。

### API 验收

- 两页编码、中文名称、规格搜索，25 条分页、停用过滤通过；无 token 401。
- 物料策略未维护供应/下钻、未知料号、停用料号均 400；库存策略未知/停用料号也均 400。
- 空策略表创建 batch 1，预检达到 READY_TO_CALCULATE。
- 具有批次读取权限但无工厂数据域的用户返回 403；不存在批次返回 404；匿名清单请求 401。

### 真实计算与历史一致性

fixture：经营计划 A=280，BOM A→B×2→C×3，库存 0，2027-02 生产日历完整。

| 批次 | 策略/操作 | 真实结果 |
| --- | --- | --- |
| 1 | 空物料策略 | 末级 C=1680，清单 A/B/C 共 3 项，默认策略 |
| 2 | 发布批次 1 | 清单内容及来源完整复制至新 active run |
| 3 | B 配置外购 STOP | 末级 B=560，清单仅 A；未访问 C 不列入 |
| 4 | 发布批次 3 | 发布成功，沿用冻结结果 |
| 5 | B 历史缺供应但有 STOP | 末级 B=560，清单 A+B；B 缺供应、STOP 仍生效 |

修改当前 B 策略后，批次 1/2/3 的历史结果和清单均不变。HTTP 修复后重新读取 2/3/5，清单数量仍为 3/1/2。

### 浏览器验收

- READY_TO_CALCULATE 点击计算，自动刷新到 READY 并显示 3 个缺失，展开全部来源。
- 两页新增均验证规格搜索、第二页物料、无匹配提示、完整选择标签、真实保存成功。
- 物料策略两个枚举下拉无「未维护」，外购 STOP 保存成功；库存策略选择仓库+物料保存成功。
- 本轮稳定截图重新访问两页和发布批次 2 清单；截图已目视检查无遮挡/重叠。

### 可复核证据

- [测试摘要](../../test-evidence/material-policy-2026-10-14/test-summary.txt)
- [HTTP 检查](../../test-evidence/material-policy-2026-10-14/http-checks.txt)
- [计算检查](../../test-evidence/material-policy-2026-10-14/calculation-checks.txt)
- [冻结结果 API JSON](../../test-evidence/material-policy-2026-10-14/api-verification.json)
- [浏览器检查](../../test-evidence/material-policy-2026-10-14/browser-checks.txt)
- [物料策略搜索](../../test-evidence/material-policy-2026-10-14/material-search.png)、[物料策略选中](../../test-evidence/material-policy-2026-10-14/material-picker.png)
- [库存策略搜索](../../test-evidence/material-policy-2026-10-14/stock-search.png)、[库存策略选中](../../test-evidence/material-policy-2026-10-14/stock-picker.png)
- [结果缺失清单](../../test-evidence/material-policy-2026-10-14/notices.png)

## 验收发现及修复记录

- 首次独立验收库创建时未明确 collation，V018 出现 mix collations；保留失败库 `mrp_policy_acceptance`，新建 `mrp_policy_acceptance_v2` 明确 `utf8mb4_unicode_ci` 后 43 个迁移成功，没有修改旧迁移。
- 首次浏览器脚本匹配 `**/api/**` 误拦截源码模块，导致白屏；修正为 `**/api/v1/**` 后通过。截图脚本未点击打开选择器、误假设第一页排序，已修复并重新生成稳定截图。
- Excel 错误报告测试首次断言到示例行，改为真实错误所在第四行，随后全量通过；报告保留源 Excel 第 5 行编号。
- 工厂域拒绝真实请求曾返回 500；根因是全局泛化异常捕获 ResponseStatusException。修复后 MockMvc 403/404 测试及真实 401/403/404 均通过。

## 未完成与风险

- 旧结果不会补算缺失清单；历史空清单明确提示可能尚未记录，不宣称全部配置完整。
- `UNMAINTAINED` 仍作为历史读取和结果内部缺供应哨兵，用户可写配置中已移除。
- 本次真实验收使用独立 MySQL schema `mrp_policy_acceptance_v2`；保留原 8080 服务与其业务库，本报告不宣称 8080 已更新。
- 部署新代码时需要应用 V043 并重启对应 API/Worker；V043 已在验收库应用，不能再修改。
- 阶段独立 Reviewer 尚未 PASS，项目状态保持 REVIEW。本次未进行全项目性能/Docker 最终 UAT，不将其列为通过。

## 决策/待确认

本需求无新增业务待确认项。以用户本轮确认和 C017 为准。

## 下一步

独立阶段复核本次补充任务；按项目部署流程应用 V043，部署前后端并重启 API/Worker。
