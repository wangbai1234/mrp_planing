# Redis 与 Redisson 使用契约

## 1. 允许用途

- 任务进度镜像；
- 当前版本摘要和高频只读查询缓存；
- `Idempotency-Key` 短期结果；
- 同一业务范围重算锁；
- 登录 token 版本/撤销辅助；
- 接口限流（确有需要时）。

禁止保存唯一正式排产结果、正式版本状态、经营计划明细或库存事实。Redis 清空后，系统必须从 MySQL 恢复并正常运行。

## 2. Key 规范

```text
mrp:{env}:task:progress:{taskId}
mrp:{env}:plan:summary:{planVersionId}:{factoryCode}
mrp:{env}:idempotency:{userId}:{requestKey}
mrp:{env}:lock:recalc:{factoryCode}
mrp:{env}:auth:token-version:{userId}
```

所有业务缓存必须有 TTL。缓存值包含对应数据库版本 id，禁止用模糊“latest”作为唯一失效依据。

## 3. TTL 建议

- 任务进度：任务结束后 24 小时；
- 版本摘要：5–30 分钟，版本发布/人工调整后主动失效；
- 幂等结果：24 小时或业务请求有效期；
- 锁：必须设置租约或使用 watchdog，并在 finally 中释放。

实际 TTL 在配置中定义并记录，不写死在多个类中。

## 4. 降级

Redis 不可用时：

- 正式读写仍以 MySQL 继续工作；
- 新计算任务可以入 MySQL，但 Worker 若无法安全获得业务锁，应延后认领而不是重复计算；
- API 任务进度从 MySQL 降级读取；
- 缓存错误不能返回旧版本作为最新事实。
