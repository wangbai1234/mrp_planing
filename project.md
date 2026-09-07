# MRP Harness Project Contract

本文件是交给 Mimo 执行 MRP 系统开发的唯一项目入口。任何实现、重构、测试和交付动作，都必须先读取本文件，再按 `.harness/` 下的约束和 Prompt 执行。

## 1. 项目目标

把当前线下 Excel 的经营计划、库存、CRM 已出货和整机排产流程，落成一个可追溯、可复核、可批量计算的 Phase 1 系统。

Phase 1 只做整机级、周维度排产：

- 经营计划：未来连续 6 个月 Forecast；
- 库存：按工厂、仓库和快照日期导入；不良品仓不计入；
- 已出货：CRM 按整机料号 + 月份累计；
- 排产：从当前周起固定输出未来连续 12 周；
- 产能：按工厂/产线维护周产能；不做日能力到周能力换算；
- 人工调整、重算、发布、版本对比、字段自定义导出；
- BOM 深化、缺料核算和 PCBA 排产属于 Phase 2，不得混入 Phase 1。

## 2. 不可违反的工程原则

1. 数据库是业务事实和最终结果的唯一真源，Redis 只做缓存、锁、进度和幂等辅助。
2. 所有正式计算必须绑定输入快照和规则版本，禁止在一次计算中混用“最新数据”。
3. 数量使用 `BIGINT` / Java `long`；比例和金额如未来出现，使用 `BigDecimal`，禁止用 `float` / `double` 做业务计算。
4. 自动值、人工覆盖值、最终生效值分开保存，人工修改不得覆盖自动值。
5. 排产计算必须可重复：相同输入版本、产能版本、规则版本和当前周，结果必须一致。
6. 大任务不在同步 HTTP 请求中等待；通过 Spring Batch 异步执行，接口返回任务号，前端轮询任务状态。
7. 所有导入先进入 staging 和校验流程，整份文件校验通过并由用户确认后，才能生成正式版本。
8. 所有写操作必须有审计记录；人工修改不生成版本，重算才生成排产新版本。
9. 不为当前项目引入消息队列、对象存储、搜索引擎、独立调度中心或微服务集群。
10. 不以“以后再修”代替门禁；未通过当前阶段验收，不得进入下一阶段。

## 3. 冻结技术栈

### 构建工具链

- Java 21 LTS JDK：后端编译、测试和运行时基础；
- Maven 3.9+ / Maven Wrapper：后端依赖管理、编译、测试、打包和插件执行；必须提交 `mvnw`、`mvnw.cmd` 与 `.mvn/wrapper/`，所有环境优先使用 `./mvnw`；
- Node.js LTS + npm：前端依赖安装、Vite 构建和 lint；前端必须通过 `.nvmrc` 或 `package.json` `engines` 固定 Node 主版本。

上述工具随开发/CI/Docker 构建使用，不是生产运行时中间件，不增加生产容器数量。

### 前端

- Vue 3 + TypeScript + Vite；
- Element Plus；
- Vue Router（页面路由）；
- 浏览器原生 `fetch` 封装 API，不默认引入 Axios；
- 排产宽表使用 Element Plus Table V2 或等价原生虚拟化方案；
- 不默认引入 Pinia；只有跨页面状态已经证明难以维护时才申请增加。

### 后端

- Java 21 LTS；
- Maven 3.9+ / Maven Wrapper（具体约束见上方“构建工具链”）；
- Spring Boot 3.x；
- Spring Web、Validation、Security；
- MyBatis，SQL 必须使用 XML Mapper；不使用 MyBatis-Plus 代替显式 SQL；
- Flyway Core + MySQL 支持：只作为应用内数据库迁移库，不是独立中间件；
- Spring Batch；
- Apache POI SAX/Event API；
- JWT（使用 Spring Security 可兼容的 JOSE 实现）；
- JUnit 5。

### 基础设施

- MySQL 8.x：业务主库、版本、批处理元数据和审计日志；
- Redis + Redisson：热点摘要、任务进度、分布式锁、幂等键；
- Docker：本地和生产镜像；
- 文件先保存到 Docker 挂载卷，不引入 MinIO。

### 依赖决策

浏览器端不默认引入 Axios，状态管理不默认引入 Pinia；如果实现过程中确实需要，必须按“新增依赖审批”规则在任务报告和 `.harness/decision-log.md` 中说明。

Maven 是后端项目的必需构建工具和依赖管理入口，不是运行时中间件。后端必须提交 Maven Wrapper，并在本地、CI 和 Docker 构建中优先使用 `./mvnw`；MySQL、Redis 和 Docker 才是当前需要单独运行或提供运行环境的基础设施。

### 默认不引入的运行组件

RocketMQ、Kafka、RabbitMQ、XXL-JOB、Quartz、MinIO、Elasticsearch、MongoDB、Neo4j、Spark、Flink、Kubernetes 等不作为当前默认方案。Nginx 也不作为初期必选组件：如果部署环境已有云负载均衡、Ingress 或公司统一网关负责 TLS/路由，则继续不引入 Nginx；如果应用需要直接暴露公网，则上线前必须提供一个受控的 TLS/反向代理入口（可评估 Nginx、Caddy 或现有网关），并按 `.harness/change-control.md` 记录决定。

## 4. 系统形态

采用模块化单体，而不是一开始拆微服务。一个后端代码库包含 API 和计算 Worker 两种启动 profile；两者共享同一套领域代码、数据库和 Redis。

```text
Vue build assets ──┐
                   ▼
backend-api (Spring Boot, API + static assets)
        │ MySQL / Redis
        ▼
backend-worker (同一镜像，worker profile)
        │ Spring Batch + @Scheduled 任务认领
        ▼
MySQL 8.x + Redis
```

无消息队列时，异步任务通过 MySQL `calc_task` / `import_task` 状态表传递：API 创建 `PENDING` 任务，Worker 定时认领并执行。Redisson 锁只用于防止重复认领和并发重算，不保存最终结果。

生产部署初始形态：1 个 API/前端静态资源容器、1 个 Worker 容器、1 个 MySQL、1 个 Redis。若部署在受控内网或已有入口网关后，Vue 构建产物由 Spring Boot API 直接托管，不额外依赖 Nginx；若直接公网部署，则先落实 TLS/反向代理入口，再开放流量。计算压力增加时先横向增加 Worker；不能通过增加线程数无限堆积任务。任何新增反向代理都必须更新架构决策。

## 5. 目录约定

```text
MRP新版/
├── project.md                         # Harness 总入口
├── .harness/                          # 架构、契约、门禁和任务 Prompt
├── backend/                           # 单一 Spring Boot 工程
├── frontend/                          # Vue 3 工程
├── deploy/                            # Docker Compose、环境样例、启动脚本
├── docs/                              # 业务需求和用户文档
├── assets/                            # 业务知识、规则、UAT、记忆池
├── templates/                         # Excel 标准模板
└── scripts/                           # 可复用工具脚本
```

后端包按业务模块划分：`auth`、`masterdata`、`forecast`、`inventory`、`shipment`、`capacity`、`planning`、`versioning`、`importexport`、`audit`、`common`。禁止按 controller/service/mapper 三层把所有业务混成大目录。

## 6. 开发执行协议

Mimo 每执行一个任务都必须：

1. 读取 `project.md`、对应 `.harness/*.md`、当前任务 Prompt 和相关 `docs/`、`assets/`；
2. 读取 `.harness/source-precedence.md`，按已裁决冲突执行；
3. 先列出将修改的文件、业务不变量和验证命令；
4. 只实现当前任务，不顺手扩展未授权功能；
5. 修改后运行当前任务要求的检查；
6. 在任务报告中写明：完成项、未完成项、实际命令、测试结果、风险和下一步；
7. 如果发现未裁决的需求冲突，停止业务实现，记录到 `.harness/decision-log.md`，不得自行选一个规则；
8. 如果门禁失败，先修复或明确阻塞原因，不得把失败标为“已完成”。

## 7. 任务顺序和依赖

必须按以下顺序执行：

1. `00-discovery.md`：审计现有资产、冻结决策；
2. `01-backend-bootstrap.md`：建立后端工程和运行骨架；
3. `02-schema-and-migrations.md`：建立 MySQL 表和迁移；
4. `03-import-and-masterdata.md`：经营计划、库存、CRM、产能导入；
5. `04-calculation-engine.md`：Spring Batch 排产计算；
6. `05-planning-api.md`：版本、调整、重算、发布、导出 API；
7. `06-frontend.md`：Vue 页面和 12 周宽表；
8. `07-integration-and-security.md`：权限、工厂数据域、前后端联调；
9. `08-test-performance.md`：规则、集成、并发和数据量测试；
10. `09-docker-uat.md`：容器化、部署、UAT 和交付。

每个任务的前置条件、输出物和通过标准见 `.harness/prompts/`。
Mimo 在开始和结束任务时同步更新 `.harness/workflow.yaml` 中对应阶段的 `status`，并把报告写到声明的 `output` 路径。禁止同时将两个阶段标为 `IN_PROGRESS`。跨会话交接遵循 `.harness/handoff.md`，所有开放缺陷写入 `.harness/issue-log.md`。技术组件边界以 `.harness/technology-baseline.md` 为准。

## 8. 启动和验证命令约定

```bash
# 后端
cd backend
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 前端
cd frontend
npm ci
npm run build
npm run lint

# 基础设施
docker compose -f deploy/docker-compose.yml up -d mysql redis
```

命令名称可以按实际工程调整，但必须在任务报告中给出等价命令和输出摘要。禁止用 H2 代替 MySQL 作为唯一集成验证数据库。

Maven 构建必须优先使用 `./mvnw`（Windows 使用 `mvnw.cmd`）；禁止要求开发者预装特定全局 Maven 版本。

## 9. 交付完成定义

只有同时满足以下条件，项目才可报告 Phase 1 完成：

- 需求文档中的已确认规则全部有代码、测试和页面证据；
- 导入能识别真实 Excel 日期和跨年月份，料号保持文本；
- 同一输入快照重复计算结果一致；
- 人工调整、恢复自动值、重算、发布、版本对比符合规则；
- 12 周排产固定、横向可查看、来源月份正确归集；
- 周产能可维护且超限提示准确，不做日能力换算；
- 无高危权限缺陷、无未处理控制台错误、无阻断级测试失败；
- Docker 环境可从空库启动，迁移可重复执行；
- UAT 案例库全部执行并有结果记录；
- 最终交付包含运行手册、配置说明、备份恢复说明和已知限制。
