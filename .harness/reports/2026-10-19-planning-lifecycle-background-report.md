# 排产发布和修订后台任务报告

## 状态
REVIEW

## 本次完成
- 核查既有同步链路：发布校验守恒、日产能、校验码，完整复制需求、末级需求及日排产；修订锁定日历/业务周、重建工厂库存池、复制执行记录。
- 发布/修订 POST 改为持久任务提交，返回 202 和任务 ID；专用 Worker 轮询执行，查询接口提供真实进度、阶段和结果版本。
- 同批次重复提交复用任务；业务结果和任务 SUCCEEDED 在同一事务提交，失去任务所有权则整体回滚。
- 页面重入从服务端恢复加载态并禁止重复提交；状态查询失败时保持禁用并自动重试。

## 修改文件
- backend/src/main/java/com/mrp/mrp/service/MrpLifecycleTaskService.java、MrpLifecycleWorkerService.java、BatchOrchestratorService.java
- backend/src/main/java/com/mrp/mrp/controller/MrpBatchLifecycleController.java；task/CalcTaskMapper.java、mapper/task/CalcTaskMapper.xml；config/SchedulingConfig.java
- frontend/src/api/mrp.ts、frontend/src/views/mrp/MrpWorkspace.vue
- backend/src/test/java/com/mrp/mrp/service/MrpLifecycleTaskServiceTest.java、MrpLifecycleWorkerServiceTest.java；frontend/src/views/mrp/__tests__/MrpWorkspaceProgress.test.ts
- test-kit/2026-10-19/e2e/lifecycle-background.spec.mjs、verify-lifecycle-final.py

## 业务不变量
- READY 才发布；PUBLISHED 才修订；原校验、指纹和容量确认保留。
- 发布版独立事实，修订版从已锁定快照复制/重建；不更改物料、分类、BOM 正式数据。
- 同批次同操作至多一个未完成/成功任务，事务失败不留下半版本。

## 验证命令与结果
```text
backend ./mvnw compile: BUILD SUCCESS
backend ./mvnw test: 552 tests, 0 failures, 0 errors, BUILD SUCCESS
frontend npm run build: success
frontend npm test -- --run: 76 passed
隔离 MySQL + Chrome E2E: #57 发布→#58，修订→#59，切页重入与重复提交同任务，1 passed
最终事务代码隔离 MySQL + 真实 HTTP: #60 发布→#61，修订→#62；发布事实 3048 / 3108 / 38850 行，守恒 mismatchCount=0；修订提交 21ms
主服务与隔离服务 /actuator/health: 200 / 200
```

## 未完成与风险
- #60 是独立隔离批次，而 #59 已成为当前版本；页面把 #60 视作历史版本并隐藏发布按钮，因此最终事务补丁用真实 API 验证，完整 Chrome 交互证据来自补丁前 #57 流程。
- 大版本发布仍在单个长事务内复制全部事实，用户无需等待 HTTP，但数据库 CPU/IO/事务日志开销未消失。需要另行量测高并发和异常回滚。
- 仅发布/修订属于本次后台化范围；预检等既有同步操作不在本次修改。

## 决策/待确认
- 不新增中间件或迁移，复用 calc_task；发布/修订 API 成功返回 202，客户端通过任务查询获取最终版本。

## 下一步
- 独立 Review 异步任务生命周期、发布长事务和高并发容量。
