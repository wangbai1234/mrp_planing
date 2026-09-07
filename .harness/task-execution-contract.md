# 无消息队列任务执行契约

本项目不引入 MQ。导入、重算、导出和 CRM 同步都通过 MySQL 任务表 + Worker 轮询执行；MySQL 是任务状态真源，Redis 是锁和进度加速层。

## 1. 状态机

```text
PENDING -> CLAIMED -> RUNNING -> SUCCEEDED
                      ├-------> FAILED
                      ├-------> RETRY_WAIT -> CLAIMED
                      └-------> CANCELLED
```

只有合法状态迁移可以执行。API 创建任务后立即返回 `202 + taskId`。`SUCCEEDED`、`FAILED`、`CANCELLED` 是终态，禁止回退。

## 2. 任务字段

任务表至少包含：

```text
id
task_type
business_scope
request_key
status
priority
worker_id
lease_until
heartbeat_at
progress_current
progress_total
attempt_count
max_attempts
next_run_at
input_payload / input_version_ids
input_checksum
result_resource_id
error_code
error_message
created_by
created_at / started_at / finished_at
version
```

`request_key` 有唯一约束，用于用户重复点击幂等。`version` 用于乐观锁。

## 3. 认领

Worker 每次认领少量任务：

1. MySQL 事务中按优先级和创建时间查询可执行任务；
2. 使用 `SELECT ... FOR UPDATE SKIP LOCKED`；
3. 更新 `CLAIMED`、`worker_id`、`lease_until`、`attempt_count`；
4. 提交事务后启动对应 Spring Batch Job；
5. 同一工厂/相同输入版本的排产重算再使用 Redisson 业务锁防重复并行。

不能先读出任务再无条件更新，否则多 Worker 会重复执行。

## 4. 心跳和租约

- RUNNING 任务定期更新 MySQL `heartbeat_at` 和 `lease_until`；
- Redis 可镜像进度供前端快速查询，但 MySQL 必须保留可恢复进度；
- Worker 崩溃后，租约过期任务由恢复扫描器转为 `RETRY_WAIT`；
- Spring Batch Step 必须使用稳定 Job Parameters，利用 JobRepository 判断是否可从检查点继续；
- 不允许两个 Worker 同时续租同一任务。

## 5. 重试

- 默认最大尝试次数 3，配置化但不能无限；
- 数据校验错误、业务冲突和非法文件不可自动重试；
- 临时数据库/Redis连接错误可以指数退避重试；
- 重试前确认没有 READY 结果；
- 已生成的 FAILED 临时版本和 staging 数据按任务 id 隔离，不污染当前版本。

## 6. 取消

取消是协作式：API 设置 `cancel_requested=true`，Worker 在 chunk 边界检查并安全停止。不能在事务中间强杀线程。已经进入正式版本原子提交阶段的任务不可取消，只能等待成功或失败。

## 7. 进度

进度阶段至少区分：准备输入、计算、产能校验、批量写入、结果校验、版本提交。前端展示阶段和数量，不承诺不准确的秒级剩余时间。

## 8. 并发限制

- Worker 使用固定线程池；线程数和 Spring Batch 分片数配置化；
- 同一工厂同一类重算默认串行，不同工厂可以并行；
- MySQL 连接池上限、Worker 并行度和 chunk 大小联合配置，禁止线程数超过数据库可承受连接数；
- 压测前不得把默认并行度设置为 CPU 核数的任意倍数。
