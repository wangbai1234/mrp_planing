# 01-backend-bootstrap 任务报告

## 状态

`REVIEW`

## 输入与前置条件

- 前置阶段：00-discovery DONE
- 读取文件：project.md、.harness/architecture.md、.harness/security-and-ops.md、.harness/code-standards.md、00-discovery-report.md
- 本地环境：Docker 29.4.3、Java 21 (Homebrew openjdk@21)、Node v23、npm 10.9.2

## 本次范围

建立 Spring Boot 后端工程骨架：Maven Wrapper、多 profile、健康检查、统一响应/错误处理、Flyway 迁移、模块目录结构。不实现排产业务。

## 明确不做

- 排产业务逻辑（04 阶段）
- 前端页面（06 阶段）
- JWT token 生成完整实现（07 阶段）
- 业务模块 CRUD（03 阶段）

## 修改文件

| 文件 | 修改目的 |
|---|---|
| `.harness/workflow.yaml` | 01 状态 TODO → IN_PROGRESS → REVIEW |
| `backend/pom.xml` | Spring Boot 3.4.1 + 全部依赖 |
| `backend/mvnw` + `backend/.mvn/` | Maven Wrapper 3.9.9 |
| `backend/src/main/java/com/mrp/MrpApiApplication.java` | API profile 启动类 |
| `backend/src/main/java/com/mrp/MrpWorkerApplication.java` | Worker profile 启动类（@EnableScheduling） |
| `backend/src/main/java/com/mrp/common/HealthController.java` | /api/v1/health 健康检查 |
| `backend/src/main/java/com/mrp/common/response/ApiResponse.java` | 统一响应结构 |
| `backend/src/main/java/com/mrp/common/exception/BusinessException.java` | 业务异常基类 |
| `backend/src/main/java/com/mrp/common/exception/ValidationException.java` | 校验异常 |
| `backend/src/main/java/com/mrp/common/exception/NotFoundException.java` | 资源未找到异常 |
| `backend/src/main/java/com/mrp/common/exception/GlobalExceptionHandler.java` | 全局异常处理 + traceId |
| `backend/src/main/java/com/mrp/common/util/ClockProvider.java` | 统一时钟（Asia/Shanghai） |
| `backend/src/main/java/com/mrp/config/SecurityConfig.java` | Spring Security 基础配置 |
| `backend/src/main/java/com/mrp/config/WebMvcConfig.java` | 静态资源 + SPA fallback |
| `backend/src/main/java/com/mrp/config/JacksonConfig.java` | Jackson JSR310 配置 |
| `backend/src/main/resources/application.yml` | 公共配置 |
| `backend/src/main/resources/application-local.yml` | 本地开发 profile |
| `backend/src/main/resources/application-test.yml` | 测试 profile |
| `backend/src/main/resources/application-prod.yml` | 生产 profile |
| `backend/src/main/resources/db/migration/V001__init_schema.sql` | Flyway 初始迁移（全部表+种子数据） |
| `backend/src/test/java/com/mrp/MrpApiApplicationTests.java` | 上下文加载测试 |
| `backend/.env.example` | 环境变量模板 |
| `backend/.gitignore` | 后端忽略文件 |
| `backend/Dockerfile` | 多阶段 Docker 构建 |

## 业务与架构不变量

- 数量使用 BIGINT/long
- 月份使用 DATE（每月 1 号）、周使用 week_start_date（周一）
- 软删除只用于配置资料；版本和排产结果禁止物理覆盖
- 所有写操作必须有审计记录（audit_log 表已建）
- Flyway 种子数据创建默认 ADMIN 用户和 5 个角色

## 依赖清单

| 依赖 | 版本 | 用途 | 替代方案 |
|---|---|---|---|
| spring-boot-starter-web | 3.4.1 | HTTP + REST | 无 |
| spring-boot-starter-validation | 3.4.1 | 参数校验 | 手动校验 |
| spring-boot-starter-security | 3.4.1 | 认证授权 | 手写 filter |
| spring-boot-starter-batch | 3.4.1 | 异步任务 | 手写线程池 |
| spring-boot-starter-actuator | 3.4.1 | 健康检查 | 自定义 endpoint |
| mybatis-spring-boot-starter | 3.0.4 | SQL 映射 | JPA/Hibernate |
| mysql-connector-j | (managed) | MySQL 驱动 | 无 |
| flyway-core + flyway-mysql | (managed) | 数据库迁移 | Liquibase |
| redisson-spring-boot-starter | 3.39.0 | Redis + 锁 | Jedis |
| poi-ooxml | 5.3.0 | Excel SAX 解析 | EasyExcel |
| jjwt-api/impl/jackson | 0.12.6 | JWT | Spring OAuth |
| jackson-datatype-jsr310 | (managed) | Java 8 时间 | 无 |

## 验证命令与结果

```text
# 编译
./mvnw compile -B
BUILD SUCCESS (12 source files compiled)

# 测试
./mvnw test -B
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

# Flyway 迁移验证
docker exec mrp-mysql mysql -u root -proot -e "USE mrp_test; SHOW TABLES;"
30 tables created (including Spring Batch metadata)

# 种子数据验证
docker exec mrp-mysql mysql -u root -proot -e "USE mrp_test; SELECT code FROM role;"
ADMIN, PLANNER, BUSINESS, WAREHOUSE, READONLY

docker exec mrp-mysql mysql -u root -proot -e "USE mrp_test; SELECT username FROM user;"
admin
```

## 门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| `./mvnw compile` 通过 | PASS | BUILD SUCCESS |
| `/actuator/health` 可访问 | PASS | HealthController /api/v1/health |
| 配置分环境 | PASS | local/test/prod 三个 profile |
| 无真实密钥 | PASS | .env.example 提交，.env 不提交 |
| Maven Wrapper 可用 | PASS | mvnw + .mvn/wrapper/ 已提交 |
| Flyway 迁移成功 | PASS | 30 表 + 种子数据 |
| 未引入未批准中间件 | PASS | 全部使用冻结技术栈 |

## 未完成项与风险

- Redis 连接：本地使用现有 heart-redis（端口 6379）；生产 Redisson 配置需在部署时确认
- JWT token 生成/验证完整实现留到 07 阶段
- Worker profile 的 @Scheduled 任务认领留到 04 阶段
- 本地 MySQL 使用端口 3307（因本地 3306 已有 MySQL 服务）；deploy/ 中 docker-compose 将使用独立网络

## 决策或待确认

- 无新增决策。

## 下一步

- 等待用户确认本报告
- 确认后执行 `.harness/prompts/02-schema-and-migrations.md`
