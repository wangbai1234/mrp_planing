# MRP Harness Project Contract（Phase 1 历史兼容 + Phase 2 当前开发）

本文件是交给 Mimo 执行 MRP 系统开发的唯一项目入口。任何实现、重构、测试和交付动作，都必须先读取本文件，再按 `.harness/` 下的约束和 Prompt 执行。

## 1. 项目目标

Phase 1 的整机周排产已经形成历史实现和验收记录，必须保持只读兼容。当前开发目标是 Phase 2：把整机月经营计划沿 BOM 管理中最新同步的全部有效关系逐层扣减各级库存，得到可解释的末级需求，并生成日排产事实；周/月只从日事实聚合。新批次不选择或生成 BOM 版本快照；已有历史批次继续读取原输入。

Phase 2 当前范围：

- 经营计划仍按整机料号、自然月维护；
- 库存覆盖整机、机头、PCBA、原材料和其他 BOM 节点；
- BOM 递归展开，每层先扣当前物料库存，只传播净需求；
- 末级需求可由结构叶子形成，也可由业务维护的 `STOP` 策略在机头、PCBA 等层级终止；
- 分类角色、供应方式、下钻策略、缺库存确认和非生产日承接由业务在决策工作台维护；共享库存默认按来源月+经营计划顺序确定性分配，人工改动留审计；
- 库存按仓库范围隔离；当前工厂可维护多个仓库，批次只扣减本厂冻结的良品仓，按正整数优先级升序、未指定时按首次维护顺序；历史批次保持原冻结映射。安全库存是硬保底，预警库存用于风险提示；
- 日排产是唯一事实；新v5规则按来源月实际业务周数均分月净需求，周余数全部归第一份，各份整体提前一个业务周，再按生产日均分，无额外减7天；周按冻结业务周、月按实际生产自然月聚合；v4及更早规则按历史回放，详见C022/C020；
- 已出货本期不参与公式、checksum、预检或发布门禁，标记为 `TODO-SHIPMENT`；
- Phase 1 周计划表和 API 保留历史只读能力，新功能使用新增模型和 API。

完整业务基线见 `docs/MRP需求文档v4.0（Phase 2）.md`；实施细节见 `docs/MRP多层净需求与日排产开发手册.md`。

## 2. 不可违反的工程原则

1. 数据库是业务事实和最终结果的唯一真源，Redis 只做缓存、锁、进度和幂等辅助。
2. 经营计划、库存和配置仍绑定输入版本；BOM 新批次直接读取 BOM 管理当前同步数据，并记录内容校验码。预检、计算和发布前校验 BOM 未被重新同步；变化时必须重建批次。历史批次保留原 BOM 输入与结果。
3. Phase 2 数量、BOM 比例、库存分配和排产均使用 MySQL `DECIMAL(18,6)` / Java `BigDecimal`；禁止用 `float` / `double`，禁止无约束 `.longValue()` 截断。Phase 1 旧 `BIGINT/long` 只保留兼容读取。
4. 自动值、人工覆盖值、最终生效值分开保存，人工修改不得覆盖自动值。
5. 排产计算必须可重复：相同经营计划、库存、BOM、分类角色、物料策略、生产日历、业务决策和规则版本，结果及 checksum 必须一致。
6. 大任务不在同步 HTTP 请求中等待；通过 Spring Batch/Worker 异步执行，接口返回任务号，前端轮询任务状态。后台计算不得等待交互弹窗。
7. 所有导入先进入 staging 和校验流程，整份文件校验通过并由用户确认后，才能生成正式版本。
8. 所有写操作必须有审计记录；长期主数据版本和单次批次业务决策必须分离。日人工修改保留系统值、人工值和最终值。
9. 不为当前项目引入消息队列、对象存储、搜索引擎、独立调度中心或微服务集群。
10. 不以“以后再修”代替门禁；未通过当前阶段验收，不得进入下一阶段。已确认不在本期实现的事项必须使用明确 `TODO`，且不得参与本期计算。

## 3. 冻结技术栈

### 构建工具链

- Java 21 LTS JDK：后端编译、测试和运行时基础；
- Maven 3.9+ / Maven Wrapper：后端依赖管理、编译、测试、打包和插件执行；必须提交 `mvnw`、`mvnw.cmd` 与 `.mvn/wrapper/`，所有环境优先使用 `./mvnw`；
- Node.js 24 LTS + npm 10/11：前端依赖安装、Vite 构建和测试；`.nvmrc` 与 `package.json` `engines` 必须同时固定为 Node 24，CI 和 Docker 构建也必须使用 Node 24，不得使用未声明的 `latest`。

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

后端包按业务模块划分：`auth`、`masterdata`、`forecast`、`inventory`、`shipment`、`capacity`、`bom`、`mrp`、`planning`、`versioning`、`importexport`、`audit`、`common`。禁止按 controller/service/mapper 三层把所有业务混成大目录。

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

`00` 至 `09` 是已完成的 Phase 1 历史阶段，不得修改其状态冒充 Phase 2 进度。Phase 2 按以下顺序执行：

1. `10-contract-migration.md`：真实代码审计、需求/契约迁移和新原型范围冻结；
2. `11-phase2-schema.md`：只新增 Flyway 迁移和持久化骨架；
3. `12-masterdata-and-decisions.md`：分类角色、物料策略、生产日历和决策框架；
4. `13-inventory-normalization.md`：全层级库存导入、唯一真源和共享库存分配；
5. `14-bom-snapshot-and-net-requirement.md`：历史 BOM 快照兼容和逐层净需求引擎；新批次采用当前同步 BOM（C023）；
6. `15-production-calendar-and-daily-planning.md`：日分配、提前 7 天、待分配和周/月聚合；
7. `16-phase2-api-and-task-flow.md`：批次 API、异步任务、短事务 Writer、发布；
8. `17-phase2-frontend.md`：业务工作台、日排产和只读汇总页面；
9. `18-integration-performance-security.md`：集成、兼容、性能和安全；
10. `19-docker-uat-final-audit.md`：Docker 空库/升级、UAT 和最终审计。

阶段内可以多 Agent 并行，但 Coordinator 必须先冻结共享契约并分配互斥文件所有权；迁移文件、共享 DTO、工作流和安全配置同一时间只能由一个 Agent 写。实现 Agent 完成后置 `REVIEW`，独立 Reviewer 复核；失败进入修复并复核，PASS 才置 `DONE`。新工作流允许门禁通过后自动进入下一阶段，业务未决、环境问题或破坏性操作必须停止。

每个任务的前置条件、输出物和通过标准见 `.harness/prompts/`。跨会话交接遵循 `.harness/handoff.md`，开放缺陷写入 `.harness/issue-log.md`，技术组件边界以 `.harness/technology-baseline.md` 为准。

## 8. 启动和验证命令约定

```bash
# 后端
cd backend
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 前端（Node 24 LTS；先确认与 .nvmrc/package.json 契约一致）
cd frontend
npm ci
npm run build
npm test
npx vue-tsc --noEmit

# 基础设施
docker compose -f deploy/docker-compose.yml up -d mysql redis
```

命令名称可以按实际工程调整，但必须在任务报告中给出等价命令和输出摘要。禁止用 H2 代替 MySQL 作为唯一集成验证数据库。

Maven 构建必须优先使用 `./mvnw`（Windows 使用 `mvnw.cmd`）；禁止要求开发者预装特定全局 Maven 版本。

## 9. 交付完成定义

只有同时满足以下条件，项目才可报告 Phase 2 完成：

- v4.0 已确认规则全部有代码、自动化测试和运行证据；
- A/B/C/D 黄金示例、逐层库存扣减、STOP/EXPLODE、共享库存和异常决策通过；
- 日事实、提前 7 天、生产日历、待分配、日周月守恒和人工调整通过；
- 已出货数据变化不影响本期结果和 checksum；
- 相同锁定输入/决策重复计算结果一致，失败不产生 READY 半版本；
- Phase 1 历史数据和接口只读兼容；
- 权限、工厂数据域、并发、幂等、事务、性能和秘密检查无阻断缺陷；
- 后端/前端编译与测试、真实 API/浏览器、Playwright、Docker 空库与 V020 升级 UAT 全部通过；
- 所有阶段均经独立 Reviewer PASS，失败修复闭环和证据完整；
- 最终交付包含运行手册、配置说明、备份恢复说明、已知限制和 `TODO-SHIPMENT`。
