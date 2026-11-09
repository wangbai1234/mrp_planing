# 任务报告：C026 排产与缺料分岗页面

## 状态

REVIEW

## 本次完成

- 排产页移除双岗冗余状态、物控策略提示、跨页跳转、日历检查入口和执行抽屉；整机确认作为当前阶段主操作，确认后按复核指纹显示“整机已确认”。
- 缺料页移除排产跳转，明确显示是否可以启动分析及阻断原因，提供刷新状态；保留来源整机只读追溯与反馈。
- 版本信息抽屉只展示冻结输入；生产执行独立为已发布版本页面。
- 预检在计算前阻断冻结日历和业务周缺项；新迁移 V063 收回 `MATERIAL` 的排产访问及 `PLANNER` 的缺料访问；整机网格和日排产 API 增加权限校验。
- 新增 C026 裁决、原型与产品说明，并更新浏览器验收断言。

## 修改文件

- `frontend/src/views/mrp/MrpWorkspace.vue`、`ShortageWorkspace.vue`、`ExecutionWorkspace.vue`、`components/ExecutionConfirm.vue`、`src/utils/mrpDisplay.ts`、菜单与路由
- `backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java`、`controller/RollingPlanningController.java`、`controller/MrpDailyPlanningController.java`
- `backend/src/main/resources/db/migration/V063__separate_material_and_schedule_workbench_access.sql`
- 对应测试、`prototype/MRP排产与缺料分岗独立页面原型.html`、本裁决文档与决策记录

## 业务不变量

- 整机确认写复核记录，不改 `SCHEDULE_READY` 批次状态；物料分析仍由物控显式启动。
- 来源月、整机日事实、物料净需求、库存分配及最终发布继续共享 batch/run；已发布历史不改写。
- 锁定日、发布双岗确认、物料异常与反馈门禁仍有效。

## 验证命令与结果

```text
backend/./mvnw compile                         PASS
backend/./mvnw test -q -Dtest=BatchOrchestratorServiceTest,WholeMachineGridTest,MrpBatchBusinessWeekScopeTest  PASS
backend/./mvnw test -q -Dtest=RollingPlanningIntegrationTest,PlanningViewPermissionTest  PASS
backend/./mvnw test -q                         651/651 PASS，0 失败、0 错误、0 跳过
frontend/npm run build                          PASS
frontend/npm test -- --run                      107/107 PASS
frontend/npx playwright test e2e/two-workbench-compact.spec.ts --project=chromium --reporter=line  3/3 PASS
git diff --check                                PASS
curl http://localhost:5180/planning             200
curl http://localhost:8180/actuator/health     200 UP
真实 API：批次 #122=SCHEDULE_READY，整机复核=CONFIRMED；reviews/calendar-check/rolling-grid/shortage-page 均 200
真实 API：PLANNER 拥有排产访问且无缺料访问；MATERIAL 拥有缺料访问且无排产访问
```

## 未完成与风险

- 后端全量测试和修正过权限上下文的集成测试均通过。前端并行测试曾有两项 5 秒超时；在后端测试结束后单独复跑，107 项全部通过。
- 执行模块的既有规则要求解除日期锁定后才能确认实际完成量；本次只将其迁出排产页面，没有改变该领域约束。
- 自定义角色若被管理员显式授予双岗权限，仍可访问两个页面；默认 `PLANNER`/`MATERIAL` 已分离。

## 决策/待确认

- C026 已由用户确认；本次不变更执行锁定与实际完成量的既有顺序规则。

## 下一步

- 自测与联调已完成；本报告保持 REVIEW，等待独立阶段复核。
