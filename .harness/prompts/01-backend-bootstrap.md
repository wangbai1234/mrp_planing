# Prompt 01：建立后端工程骨架

前置：Prompt 00 已完成且没有未解决的 P0 业务冲突。

读取 `project.md`、`.harness/architecture.md`、`.harness/security-and-ops.md`、`.harness/code-standards.md` 和发现报告。创建 `backend/` Spring Boot 工程：

- Java 21、Maven 3.9+ / Maven Wrapper、Spring Boot 3.x；
- profile：`local`、`test`、`prod`；
- API 和 Worker 共用一个镜像，通过 profile 选择启动方式；
- 引入最小依赖：Web、Validation、Security、MyBatis、MySQL Driver、Flyway、Redis/Redisson、Spring Batch、POI、JWT、Actuator、JUnit 5；
- 提供 Maven Wrapper，确保构建不依赖开发机全局 Maven；
- 在项目文档或构建配置中固定 Maven Wrapper 版本；Docker/CI 构建不得依赖未声明的全局 Maven；
- 为后续托管 Vue build 产物预留 Spring Boot static resources 目录；
- 只创建模块边界和健康检查，不实现排产业务；
- 配置从环境变量读取，提交 `.env.example`，不提交密钥；
- 建立统一响应、统一错误、traceId 和基础异常处理。

验证：优先使用 `./mvnw` 执行编译和单元测试，再做应用启动、MySQL/Redis 连通性健康检查。输出任务报告和依赖清单。任何新增依赖必须说明替代方案和成本。完成后将阶段状态设为 REVIEW，等待复核，不自动执行 02。
