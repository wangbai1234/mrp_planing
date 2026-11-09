# 排产缺库存与物料主数据提示修复报告

## 状态

REVIEW；待独立 Reviewer 复核，不变更阶段总状态。

## 本次完成

- 核对主库批次 #104（整机 6810AA800157、经营计划版本 1222、库存快照 6、BOM 快照 35）：修复前有 15 条缺库存和 1 条 SO-VX00-99 分类角色缺失异常。
- 冻结 BOM 中 5051AA800042 不是该整机的下级节点；5052AA800025 经 5070AA800080 进入路径，但快照 6 有 GOOD 库存 500，因此两者都不应出现在缺库存异常中。该整机的缺库存异常实际包含 5051AA800043～046、5052AA800024/026。
- SO-VX00-99 在冻结 BOM 中为该整机直接子项，但本地物料管理表无记录；预检现单独生成 MATERIAL_MISSING，若同时无库存行，再生成 INVENTORY_MISSING，不再误报为 ROLE_MISSING。真实存在于 BOM/NC 不等于本地物料管理已维护。
- 预检按 issueKey 去重后再统计，使多来源月同一问题的批次计数与异常行数一致。
- 已在无人工决策的批次 #104 通过正式 API 重新预检：当前 17 条 OPEN 异常，包括 16 条缺库存及 1 条物料主数据缺失；SO-VX00-99 同时命中两类。未改动任何冻结输入或库存数据。

## 修改文件

- backend/src/main/java/com/mrp/mrp/service/CategoryRoleVersionService.java
- backend/src/main/java/com/mrp/mrp/engine/NetRequirementEngine.java
- backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java
- backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java
- backend/src/test/java/com/mrp/mrp/service/CategoryRoleVersionServiceTest.java
- backend/src/test/java/com/mrp/mrp/service/BatchOrchestratorServiceTest.java
- backend/src/test/java/com/mrp/mrp/engine/MissingInputIssuesTest.java
- frontend/src/utils/mrpDisplay.ts、frontend/src/utils/__tests__/mrpDisplay.test.ts
- frontend/src/views/mrp/components/RequirementTrace.vue

## 业务不变量

- BOM、经营计划、库存和分类角色版本保持冻结；缺库存行与有记录且为零继续区分。
- 未维护角色与未导入物料主数据分别提示；缺角色时仍阻断继续下钻。
- 已解决的业务决策不自动重开；本次刷新前 #104 无已解决项。

## 验证命令与结果

- `cd backend && ./mvnw compile -q`：通过。
- `cd backend && ./mvnw test -q`：计数修复后全量回归通过，退出码 0。
- `cd backend && ./mvnw -q -Dtest=MissingInputIssuesTest,CategoryRoleVersionServiceTest,BatchOrchestratorServiceTest test`：通过。
- `cd frontend && npm run build`：通过；`npm test -- --run src/utils/__tests__/mrpDisplay.test.ts`：2/2 通过。
- 实际 Spring Boot 8280 健康接口 200；管理员登录、批次 #104 transition + precheck、issues/summary 查询均成功，17 条状态/计数一致。
- Chromium 浏览器访问 8280 的排产 #104 页面；决策抽屉能看到“物料管理中缺少物料主数据”和“缺少库存记录”，SO-VX00-99 可见，页面异常为 0。
- 原有 5180/8080 入口也经 Chromium 登录并进入 #104 决策抽屉验证，同样展示两类提示且页面异常为 0；主服务健康接口 200。

## 未完成与风险

- 本地物料主数据中尚无 SO-VX00-99，业务需要核实是否应导入并分类；本次不凭 BOM 自动补建主数据。
- 独立 Reviewer 未执行，维持 REVIEW。
