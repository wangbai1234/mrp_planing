# 排产计算回退循环修复报告

## 状态
REVIEW

## 本次完成
- 主库批次 #106 的任务 #169–#177 均失败，Worker 复位到待计算。部分旧进程无法读取无快照新批次；当前代码对同键末级需求重复写入。
- 写入前按唯一键维度合并多条 BOM 路径的末级需求，保留 BigDecimal 数量和路径级明细。
- 工作台在复位待计算时读取最新任务，显示编号和失败原因。

## 修改文件
- backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java
- backend/src/test/java/com/mrp/mrp/service/MrpCalculationServiceDemandMergeTest.java
- frontend/src/views/mrp/MrpWorkspace.vue
- frontend/src/views/mrp/__tests__/MrpWorkspaceProgress.test.ts
- .harness/issue-log.md 和本报告

## 业务不变量
- 仅相同 run、工厂、来源整机、末级物料、类型、供应方式、来源月的数量求和。
- 失败不会显示为成功；成功后仍需 run 校验才能进入 READY。

## 验证命令与结果
- 后端 ./mvnw compile：PASS；./mvnw test -q：PASS；专项 MrpCalculationServiceDemandMergeTest,MrpTaskWorkerServiceTest：PASS。
- 前端 npm run build：PASS；npm test -- --run：82/82 PASS。
- 8382 新服务认证后查询计算任务：返回 #177 FAILED 及实际错误。
- Playwright 访问 planning/106：显示任务 #177 和失败原因。
- 用户授权后停用旧主库服务，并用当前构建重启 8080。任务 #178 SUCCEEDED，run #58 COMPLETED，批次 #106 进入 WAITING_DECISION。
- 待分配 24 条，原因 CALENDAR_INCOMPLETE，数量合计 5167，目标日期均为 2026-09-21。重启后 Playwright 复核页面显示待业务处理与决策入口。

## 未完成与风险
- 旧主库 Worker 已停用，隔离测试 8180 服务保持不变。
- 真实计算已成功；缺失生产日历使批次无法进入 READY，需业务处理 24 条待分配，因此保持 REVIEW。

## 决策/待确认
- 无业务规则冲突。

## 下一步
- 补齐 2026-09-21 相关生产日历或在工作台明确处理待分配，再核验数量守恒和发布门禁。
