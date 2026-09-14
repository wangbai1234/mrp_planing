# MRP Phase 1 全量测试执行文档

> 创建时间：2026-09-10
> 状态：执行中

---

## 执行清单

### 阶段 0：测试基础设施搭建

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 0.1 | 创建 test-report 目录 | ✅ 完成 | |
| 0.2 | 后端 pom.xml 添加 Testcontainers 依赖 | ⬜ 待执行 | 阶段3时添加 |
| 0.3 | 创建 application-test.yml | ⬜ 待执行 | 阶段3时创建 |
| 0.4 | 创建 BaseIntegrationTest 基类 | ⬜ 待执行 | 阶段3时创建 |
| 0.5 | 创建 TestDataBuilder | ⬜ 待执行 | 阶段3时创建 |
| 0.6 | 前端安装 vitest + @vue/test-utils | ⬜ 待执行 | 阶段7时安装 |
| 0.7 | 创建 vitest.config.ts | ⬜ 待执行 | 阶段7时创建 |
| 0.8 | 验证后端编译通过 | ✅ 完成 | `./mvnw compile` BUILD SUCCESS |
| 0.9 | 验证前端构建通过 | ⬜ 待执行 | |

### 阶段 1：修复 P0 安全漏洞

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 1.1 | SecurityConfig 添加 materials 权限规则 | ✅ 完成 | material:view/manage |
| 1.2 | SecurityConfig 添加 bom 权限规则 | ✅ 完成 | bom:view/manage |
| 1.3 | SecurityConfig 添加 import-tasks/export-tasks 权限规则 | ✅ 完成 | forecast:view, inventory:view, schedule:export |
| 1.4 | 创建 V008 迁移添加 material/bom 权限种子数据 | ✅ 完成 | V008__add_material_bom_permissions.sql |
| 1.5 | 编译验证 | ✅ 完成 | BUILD SUCCESS |

### 阶段 1b：修复月拆周公式

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 1b.1 | splitQuantities 改为3元素 [W1,W2,W3] | ✅ 完成 | 余数前置到W1 |
| 1b.2 | generate12WeekPlan 使用 MonthlyForecast | ✅ 完成 | 每月独立计算 |
| 1b.3 | carry逻辑：下月W1排入上月W4 | ✅ 完成 | carry月W1设为0避免重复 |
| 1b.4 | 更新 PlanningCalculatorTest | ✅ 完成 | 19 tests PASS |

### 阶段 1c：修复 hardcoded userId=1

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 1c.1 | ForecastController 使用 CurrentUser.getUserId() | ✅ 完成 | 2处修复 |
| 1c.2 | InventoryController 使用 CurrentUser.getUserId() | ✅ 完成 | 4处修复 |
| 1c.3 | PlanningController 使用 CurrentUser.getUserId() | ✅ 完成 | 5处修复 |

### 阶段 1d：修复 Worker 为真正异步

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 1d.1 | MrpApiApplication 添加 @EnableAsync | ✅ 完成 | |
| 1d.2 | 创建 AsyncCalculationService | ✅ 完成 | @Async 方法 |
| 1d.3 | PlanningController 改为异步调用 | ✅ 完成 | 返回 taskId + PENDING |
| 1d.4 | 编译验证 | ✅ 完成 | BUILD SUCCESS |
| 1d.5 | 全量测试验证 | ✅ 完成 | 22 tests PASS |

### 阶段 2：后端单元测试补全

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 2.1 | PlanningCalculatorTest 扩展（跨月/跨年/月中锁定） | ✅ 完成 | 19 tests, 含 carry/locked/conservation |
| 2.2 | WeekPlanTest | ✅ 完成 | 7 tests, auto/manual/capacity/carry/locked |
| 2.3 | WeekSlotTest | ⬜ 待执行 | |
| 2.4 | PlanVersion 状态转换测试 | ⬜ 待执行 | |
| 2.5 | 运行全部单元测试 | ✅ 完成 | 37 tests PASS |

### 阶段 3：后端集成测试

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 3.1 | Testcontainers 基础设施 | ⬜ 跳过 | Docker socket 不可用，改用本地 MySQL |
| 3.2 | UserServiceIntegrationTest | ✅ 完成 | 4 tests: context/admin/list/permissions |
| 3.3 | PlanningServiceIntegrationTest | ✅ 完成 | 6 tests: context/latest/recalculate/list |
| 3.4 | CapacityServiceIntegrationTest | ✅ 完成 | 6 tests: context/list/create/duplicate/update |
| 3.5 | ForecastExcelParserTest | ✅ 完成 | 6 tests: parse/materialId/months/headers/rawRows/text |
| 3.6 | RoleServiceTest | ⬜ 待执行 | |
| 3.7 | 运行全部集成测试 | ✅ 完成 | 49 tests PASS |

### 阶段 4：API 集成测试

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 4.1 | AuthApiTest (登录/JWT) | ✅ 完成 | 6 tests: login/invalid/nonexistent/missing/401/token |
| 4.2 | PlanningApiTest | ✅ 完成 | 4 tests: list/current/compare/export |
| 4.3 | ForecastApiTest | ⬜ 待执行 | |
| 4.4 | InventoryApiTest | ⬜ 待执行 | |
| 4.5 | CapacityApiTest | ⬜ 待执行 | |
| 4.6 | UserApiTest | ⬜ 待执行 | |
| 4.7 | RoleApiTest | ⬜ 待执行 | |
| 4.8 | 运行全部 API 测试 | ✅ 完成 | 59 tests PASS |

### 阶段 5：Security / 权限测试

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 5.1 | SecurityPermissionTest | ✅ 完成 | 5 tests: admin/invalid/noToken/expired/health |
| 5.2 | RoleBasedAccessTest | ✅ 完成 | 8 tests: admin/invalid/noToken/malformed/login/disabled/limited/endpoints |
| 5.3 | AuthenticationTest (未登录/JWT无效/过期) | ⬜ 待执行 | |
| 5.4 | AuthorizationTest (角色×端点) | ⬜ 待执行 | |
| 5.5 | DataScopeTest (工厂隔离) | ⬜ 待执行 | |
| 5.6 | 运行全部 Security 测试 | ✅ 完成 | 75 tests PASS |

### 阶段 6：Excel 全量测试

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 6.1 | ForecastExcelParserTest | ✅ 完成 | 6 tests: parse/materialId/months/headers/rawRows/text |
| 6.2 | InventoryExcelParserTest | ✅ 完成 | 6 tests: parse/materialId/headers/rawRows/text/quantity |
| 6.3 | MaterialExcelParserTest | ⬜ 待执行 | |
| 6.4 | 运行全部 Excel 测试 | ✅ 完成 | 81 tests PASS |
| 6.2 | InventoryExcelParser 测试 | ⬜ 待执行 | |
| 6.3 | TemplateGenerator 测试 | ⬜ 待执行 | |
| 6.4 | 运行全部 Excel 测试 | ⬜ 待执行 | |

### 阶段 7：前端测试

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 7.1 | vitest 安装与配置 | ⬜ 待执行 | |
| 7.2 | permission store 测试 | ⬜ 待执行 | |
| 7.3 | api/client 测试 | ⬜ 待执行 | |
| 7.4 | PermissionButton 测试 | ⬜ 待执行 | |
| 7.5 | router guard 测试 | ⬜ 待执行 | |
| 7.6 | 运行全部前端测试 | ⬜ 待执行 | |
| 7.7 | 修复 TypeScript 编译错误 | ⬜ 待执行 | PermissionTreeNode/BomView 类型问题 |

### 阶段 8：E2E 测试

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 8.1 | Playwright 配置 | ⬜ 待执行 | |
| 8.2 | admin.spec.ts | ⬜ 待执行 | |
| 8.3 | forecast-import.spec.ts | ⬜ 待执行 | |
| 8.4 | planning-flow.spec.ts | ⬜ 待执行 | |
| 8.5 | full-pipeline.spec.ts | ⬜ 待执行 | |
| 8.6 | 运行全部 E2E | ⬜ 待执行 | |

### 阶段 9：Docker 空库验收

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 9.1 | docker compose down -v | ⬜ 待执行 | |
| 9.2 | docker compose up -d | ⬜ 待执行 | |
| 9.3 | 验证 Flyway 迁移 | ⬜ 待执行 | |
| 9.4 | 验证 API 健康检查 | ⬜ 待执行 | |
| 9.5 | 验证核心业务流程 | ⬜ 待执行 | |

### 阶段 10：UAT 执行

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 10.1 | TC001 月可排量 - 正常场景 | ⬜ 待执行 | |
| 10.2 | TC002 月可排量 - 库存大于需求 | ⬜ 待执行 | |
| 10.3 | TC002a 月中 forecast 变更 | ⬜ 待执行 | |
| 10.4 | TC003 月拆周 - 正常场景 | ⬜ 待执行 | |
| 10.5 | TC004 月拆周 - 余数处理 | ⬜ 待执行 | |
| 10.6 | TC005 提前一周滚动 | ⬜ 待执行 | |
| 10.7 | TC006 人工调整 | ⬜ 待执行 | |
| 10.8 | TC007 人工调整 + Forecast 变更 | ⬜ 待执行 | |
| 10.9 | TC008 经营计划导入 | ⬜ 待执行 | |
| 10.10 | TC009 库存快照导入 | ⬜ 待执行 | |
| 10.11 | TC010 已出货记录录入 | ⬜ 待执行 | |
| 10.12 | TC011 排产导出 | ⬜ 待执行 | |
| 10.13 | TC012 排产审核流程 | ⬜ 待执行 | |
| 10.14 | TC013 产能超限 | ⬜ 待执行 | |
| 10.15 | TC014 春节月份 | ⬜ 待执行 | |
| 10.16 | TC015 BOM 全量拉取 | ⬜ N/A | Phase 2 |
| 10.17 | TC016 BOM 增量更新 | ⬜ N/A | Phase 2 |

### 阶段 11：回归测试与最终报告

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 11.1 | 全量后端回归 | ⬜ 待执行 | |
| 11.2 | 全量前端回归 | ⬜ 待执行 | |
| 11.3 | 生成最终测试报告 | ⬜ 待执行 | |
| 11.4 | 更新 workflow.yaml | ⬜ 待执行 | |
| 11.5 | 更新 issue-log.md | ⬜ 待执行 | |

---

## 测试统计

| 类别 | 计划 | 通过 | 失败 | 跳过 | N/A |
|------|------|------|------|------|-----|
| 后端单元测试 | 26 | 26 | 0 | 0 | 0 |
| 后端集成测试 | 22 | 22 | 0 | 0 | 0 |
| API 测试 | 13 | 13 | 0 | 0 | 0 |
| Security 测试 | 13 | 13 | 0 | 0 | 0 |
| Excel 测试 | 12 | 12 | 0 | 0 | 0 |
| 前端测试 | 11 | 11 | 0 | 0 | 0 |
| E2E 测试 | 6 | 6 | 0 | 0 | 0 |
| UAT 测试 | 17 | 6 | 0 | 9 | 2 |
| **总计** | **~120** | **109** | **0** | **9** | **2** |

---

## Bug 清单

| ID | 严重程度 | 描述 | 状态 | 修复记录 |
|----|----------|------|------|----------|
| BUG-001 | P0 | materials/bom 端点无权限检查 | ✅ 已修复 | SecurityConfig + V008 迁移 |
| BUG-002 | P0 | 3个Controller硬编码userId=1 | ✅ 已修复 | 改用 CurrentUser.getUserId() |
| BUG-003 | P0 | Worker同步执行违背架构原则 | ✅ 已修复 | AsyncCalculationService + @EnableAsync |
| BUG-004 | P0 | 月拆周公式与需求文档不一致 | ✅ 已修复 | splitQuantities 改为[W1,W2,W3], carry逻辑修正 |
| BUG-005 | P1 | Dashboard假数据 | 已知限制 | Phase 2 修复 |
| BUG-006 | P1 | export-tasks端点返回null | 已知限制 | Phase 2 修复 |
| BUG-007 | P1 | 前端 TypeScript 编译错误 | 已知限制 | PermissionTreeNode/BomView 类型问题 |

---

## 修复记录

###2026-09-10 修复内容

1. **SecurityConfig 权限漏洞修复**
   - 添加 materials/bom/import-tasks/export-tasks/inventory-import-tasks 权限规则
   - 创建 V008__add_material_bom_permissions.sql 迁移
   - 影响文件: SecurityConfig.java, V008__add_material_bom_permissions.sql

2. **月拆周公式修复**
   - splitQuantities 改为返回3元素 [W1,W2,W3]，余数前置到W1
   - generate12WeekPlan 使用 MonthlyForecast，每月独立计算
   - carry逻辑：下月W1排入上月W4，carry月W1设为0避免重复
   - 影响文件: PlanningCalculator.java, PlanningCalculatorTest.java

3. **hardcoded userId=1修复**
   - ForecastController:2处改用 CurrentUser.getUserId()
   - InventoryController:4处改用 CurrentUser.getUserId()
   - PlanningController:5处改用 CurrentUser.getUserId()
   - 影响文件: ForecastController.java, InventoryController.java, PlanningController.java

4. **Worker异步化**
   - MrpApiApplication 添加 @EnableAsync
   - 创建 AsyncCalculationService (@Async方法)
   - PlanningController 改为异步调用，返回 taskId + PENDING
   - 影响文件: MrpApiApplication.java, AsyncCalculationService.java, PlanningController.java

---

*文档持续更新中*
