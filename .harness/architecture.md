# MRP 架构约束

## 1. 组件边界

### API 应用

负责认证、页面查询、导入任务创建、任务状态查询、人工调整、发布、版本对比和导出任务创建。API 不执行长时间排产计算，不在 Controller 中直接写多张结果表。

### Worker 应用

与 API 共用代码和镜像，以 `worker` profile 启动。通过 `@Scheduled` 认领 `PENDING` 的导入和计算任务，调用 Spring Batch。Worker 必须支持优雅停机：任务处于安全检查点时可重启，不能把半成品版本标为可用。

任务认领、租约、心跳、恢复和有限重试必须遵循 `task-execution-contract.md`。

### MySQL

保存业务事实、不可变输入版本、排产结果、批处理元数据和审计日志。所有跨表业务状态变更必须在事务中完成。

### Redis / Redisson

只允许保存短期缓存、进度、幂等键和锁。缓存丢失后系统仍能从 MySQL 恢复任务状态和最终结果。

Key、TTL、锁和降级规则遵循 `redis-contract.md`。

初期不设置 Nginx 层；Spring Boot API 同时托管 Vue 构建产物，并负责 SPA fallback、静态资源缓存头和 `/api` 路由。只有出现下列可测量需求时才增加反向代理：

- 需要在入口统一做 TLS 终止或证书轮换；
- 需要多个域名/路径路由，或 API 与静态资源拆分；
- 需要多实例负载均衡、限流或边缘缓存；
- Spring Boot 直接托管静态资源已成为可观测的性能瓶颈。

如果部署环境已有云负载均衡、Ingress 或公司统一网关，则不需要额外 Nginx；如果应用直接暴露公网，必须先落实受控的 HTTPS/反向代理入口。引入 Nginx（或其他代理）属于架构变更，必须先更新 `.harness/decision-log.md`、部署拓扑、健康检查和 UAT，不得仅为“常见做法”而添加。

### 文件卷

上传原始文件、解析错误报告和导出文件保存到 Docker 挂载卷。数据库保存文件元信息、校验和、相对路径和保留期限。禁止把绝对本机路径写入业务表。

## 2. 后端分层

```text
Controller  -> Application Service -> Domain Service / Batch Job
                                      -> Repository(MyBatis XML)
                                      -> MySQL / Redis
```

- Controller 只做鉴权、参数校验、调用应用服务和返回统一响应；
- Application Service 组织事务和业务用例；
- Domain Service 放排产公式、拆周和校验，不依赖 HTTP、Redis 或 MyBatis；
- Repository 的 SQL 显式写在 XML，复杂查询必须有索引说明；
- Batch Step 不直接操作 Controller 或前端 DTO；
- DTO、Entity、Domain Model 分离，禁止数据库 Entity 直接作为 API 响应。

## 3. 前端分层

```text
views -> composables / feature services -> api client(fetch) -> backend
```

页面按 `planning`、`forecast`、`inventory`、`capacity`、`versions`、`manual` 划分。排产页的 12 周表格必须保留固定物料列、横向滚动、来源月份标签、人工/锁定/超限状态和详情抽屉。

不要把业务公式复制到多个 Vue 组件。页面展示值来自 API；前端可以做格式化和即时输入校验，但不能成为正式排产计算的唯一实现。

## 4. 异步任务模型

```text
POST /api/v1/recalculations
  -> calc_task(PENDING)
  -> worker claim with Redisson lock
  -> Spring Batch execution
  -> calc_task(RUNNING / progress)
  -> plan_version(READY)
  -> calc_task(SUCCEEDED)
```

任务失败：`calc_task=FAILED`，排产当前版本不变；必须保留失败原因和输入 checksum。重试只能创建新任务或在安全检查点重试，不能覆盖已有 READY 版本。

## 5. 依赖管理

每增加一个依赖，必须在任务报告中说明：解决的问题、为什么现有栈不能解决、运行时成本、删除方案。默认不增加基础设施依赖。

Maven 3.9+ / Maven Wrapper 是 Java 工程的必需构建入口，负责依赖解析、编译、测试、打包和插件执行；它不作为独立运行服务，也不增加生产容器数量。Wrapper 版本必须固定并纳入仓库，避免开发机和 CI 使用不同 Maven 版本。
