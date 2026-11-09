# 发布排产进度条改动报告

## 状态
REVIEW

## 本次完成
- 排产中心状态栏复用 Element Plus 计算进度条，发布与修订任务显示服务端进度百分比和阶段说明。
- 页面切换后通过 lifecycle-task 恢复进度；轮询失败保留最后进度并提示重试。
- 任务未完成前最高显示 99%；失败时展示红色异常进度条和错误说明，成功后回到常规状态栏。

## 修改文件
- frontend/src/views/mrp/MrpWorkspace.vue
- frontend/src/views/mrp/components/CalculationProgress.vue
- frontend/src/views/mrp/__tests__/MrpWorkspaceProgress.test.ts

## 业务不变量
- 发布任务仍在后端异步执行；本次不改发布业务逻辑、进度存储或排产事实。
- 计算与发布进度共用状态栏位置，不同时显示两个进度条。

## 验证命令与结果
- 前端 npm run build：通过。
- 前端 npm test -- --run：12 个测试文件、79 项通过。
- 后端 ./mvnw compile -q：通过。
- 后端 ./mvnw -q -Dtest=MrpLifecycleTaskServiceTest,TaskProgressServiceTest test：4 项通过。
- 隔离环境前端 5181、后端 8180 启动；浏览器登录并加载 /planning 与 /planning/11；真实 lifecycle-task 与 calculation-task API 返回 200。

## 未完成与风险
- 未对既有业务批次执行发布；运行中进度条的页面逻辑由组件测试验证。
- 后端全量测试在 Testcontainers 新建 MySQL 的 Flyway 迁移处超过四分钟未结束；中止并保留现场，相关单测通过。

## 下一步
- 独立复核后可在隔离库新批次执行发布，观察真实运行进度。
