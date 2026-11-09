# BOM 从 NC 后台同步任务报告

## 状态

REVIEW：实现和联调完成，等待独立复核。

## 本次完成

- `/api/v1/bom/sync` 改为立即返回 `202` 与任务 ID；任务状态持久化在 `bom_sync_status`，同一时刻只允许一个待执行或运行任务。
- 后台 Worker 从 NC 先读取上海组织的总行数，再使用单次 Oracle 游标流式拉取；每 1000 条持久化实际拉取数量。拉取完成后，在一个 MySQL 事务中替换本地 BOM、保存数据质量摘要并标记任务成功。
- BOM 页面每 2 秒读取任务进度，采用与 MRP 计算一致的 Element Plus 8px 进度条；可关闭弹窗、刷新或离开页面后返回继续查看。失败时显示原因，成功后刷新列表。
- 增加了任务领取、心跳、失联失败和重复提交保护。

## 修改文件

- `backend/src/main/java/com/mrp/bom/{controller/BomController.java,domain/BomSyncTask.java,repository/BomMapper.java,service/BomService.java,service/BomSyncTaskService.java,service/BomSyncWorkerService.java,service/BomSyncCommitService.java}`
- `backend/src/main/java/com/mrp/bom/local/repository/BomSyncTaskMapper.java`、`backend/src/main/resources/mapper/bom/{BomMapper.xml,BomSyncTaskMapper.xml}`、`backend/src/main/java/com/mrp/config/SchedulingConfig.java`
- 新迁移 `V050__bom_sync_background_task.sql`、`V051__bom_sync_validation_result.sql`
- `frontend/src/views/bom/BomView.vue`；BOM 服务与 Worker 测试。

## 业务不变量

- 只同步上海组织；NC 返回空数据或拉取数量不一致时不删除现有本地 BOM。
- 本地父项、明细和任务成功状态同事务提交；失败回滚并保留旧 BOM。
- 进度表示实际已拉取行数，不估算剩余时间；100% 只在任务成功后展示。

## 验证命令与结果

```text
cd backend && ./mvnw compile
PASS
cd backend && ./mvnw -Dtest=BomServiceTest,BomSyncMysqlTest,BomSyncWorkerServiceTest test
8 项通过
cd frontend && npm run build
PASS
cd frontend && npm test
81 项通过
cd backend && ./mvnw test
564 项通过，0 failures / errors / skipped
```

- 独立 MySQL schema 从空库完成 V001-V051 迁移；实际启动后端 8082、前端 5182 并通过浏览器操作。
- 真实 NC 同步任务 `#2` 返回 `202`，2 分 24 秒内拉取 146451 条明细并写入 3434 个父项；状态接口返回 `SUCCESS`，浏览器显示 100%、质量摘要和 3434 条列表数据。
- 刷新页面后恢复运行中弹窗；无认证 GET/POST 新接口均返回 401。
- 首个隔离 schema 因默认 collation 与旧迁移冲突，保留现场；使用项目既有 `utf8mb4_unicode_ci` 新建第二个隔离 schema 后验证通过。

## 未完成与风险

- 独立阶段复核尚未进行。
- 进度总数取自拉取前的 NC 计数。若 NC 在计数与读取之间变更导致行数不一致，任务失败并保留旧 BOM；业务方可以重新发起。

## 决策/待确认

无新增业务决策或依赖。

## 下一步

进行独立复核；通过后再将本任务状态转为 DONE。
