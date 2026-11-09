# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

MRP 排产系统：把线下 Excel 经营计划→排产流程系统化。**当前开发是 Phase 2**（多层净需求 + 日排产）。Phase 1 整机周排产已验收，只读兼容，不得改其表、API 或 Flyway 历史来冒充 Phase 2 进度。

开工前必读：`project.md`（唯一项目契约）、`AGENTS.md`、`.harness/execution-laws.md`。阶段顺序、门禁和 Prompt 在 `.harness/workflow.yaml` 与 `.harness/prompts/`（Phase 2 是 `10`–`19`，`00`–`09` 是已完成的 Phase 1）。未裁决的业务冲突记入 `.harness/decision-log.md`，不得自行选规则。业务基线：`docs/MRP需求文档v4.0（Phase 2）.md`、`docs/MRP多层净需求与日排产开发手册.md`。

## Commands

Java 21、Node 24（`frontend/.nvmrc` 与 `package.json` `engines`）、Maven Wrapper。环境不满足时停下来告诉用户，不要改版本配置或替用户安装运行时。

```bash
# 基础设施（集成验证必须用 MySQL，禁止用 H2 代替）
docker compose -f deploy/docker-compose.yml up -d mysql redis

# 后端（在 backend/；始终用 ./mvnw，不要用全局 mvn）
./mvnw test
./mvnw -Dtest=ClassName test
./mvnw -Dtest=ClassName#method test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local          # API，默认 8080
# Worker 是同一工程的另一个 main：com.mrp.MrpWorkerApplication（worker profile）

# 前端（在 frontend/）
npm ci
npm run dev          # Vite，端口 5180，/api 代理到 localhost:8080
npm test             # vitest run（src/**/*.{test,spec}.ts）
npm run test:watch
npx vitest run src/utils/__tests__/foo.test.ts
npm run build        # vue-tsc -b && vite build
npx playwright test  # e2e/，baseURL http://localhost:5180，会拉起 npm run dev
npx playwright test e2e/phase2-mrp.spec.ts
```

后端测试是 JUnit 5 + Testcontainers MySQL（`backend/src/test`）。改完代码先编译再测；报告功能完成前要实际起服务并用浏览器或 curl 验证，不能只凭单测。

## Architecture

模块化单体，不是微服务。一个后端代码库、两个启动类：`MrpApiApplication`（API，并托管 `resources/static` 里的 Vue 产物）和 `MrpWorkerApplication`（同一镜像的 worker profile）。无消息队列：API 往 MySQL 写 `PENDING` 的 `calc_task` / `import_task`，Worker 用 Spring Batch + `@Scheduled` 认领执行；Redisson 只做锁、进度、幂等，不存最终结果。大计算不得堵在同步 HTTP 里，接口返回任务号，前端轮询。

```text
Vue (frontend/) ──/api──► backend-api :8080 ──┐
                                               ├── MySQL 8（唯一业务真源）
                        backend-worker ────────┘    Redis（缓存/锁/进度）
```

后端包在 `com.mrp` 下**按业务模块**划分（`auth` `masterdata` `forecast` `inventory` `shipment` `capacity` `bom` `mrp` `planning` `importexport` `audit` `task` `common`），模块内再分 controller/service/repository/domain。禁止按技术层把所有业务堆进一个大目录。MyBatis SQL 必须写 XML Mapper，不用 MyBatis-Plus。BOM 另有只读外部源：`bom/local` 走主库，外部 BOM 走独立数据源（`PrimaryDataSourceConfig`，含 Oracle 驱动）。

Phase 2 计算链（`mrp` + `planning`）：经营计划按整机料号、自然月 → 绑定不可变 BOM 快照逐层展开，每层先扣本层库存再传播净需求 → 末级可以是结构叶子，也可以被物料策略 `STOP` 提前终止 → 日排产是唯一事实（月净需求按有效生产日分配后整体提前 7 个自然日）→ 周/月只从日事实只读聚合。相同输入快照 + 规则版本必须得到相同结果和 checksum；自动值、人工覆盖值、最终值分开存。数量一律 `DECIMAL(18,6)` / `BigDecimal`，禁止 `double` 或无约束截断；Phase 1 的 `BIGINT/long` 只保留兼容读取。已出货本期不进公式（`TODO-SHIPMENT`）。

Flyway 在 `backend/src/main/resources/db/migration`（`V001`–`V020` 左右是 Phase 1，`V021` 起是 Phase 2）。**已应用的迁移不可修改**，只能新增下一个版本。

前端是 Vue 3 + TypeScript + Vite + Element Plus + Vue Router，`src/views/<模块>` 对页面，`src/api` 用浏览器原生 `fetch` 封装（不引入 Axios）。契约默认不引入 Pinia，但 `src/stores` 已存在——沿用现状，不要再加新的状态库。排产宽表用 Element Plus Table V2 或等价虚拟化。UI 以 `prototype/` 为契约（推荐 `MRP滚动排产可交互原型.html`），组件必须用 Element Plus，不用 emoji。导入/列表页面另见 `assets/import-design-rules.md`、`.harness/import-rules.md`、`.harness/list-rules.md`。

侧边栏和权限配置页共用 `frontend/src/config/menu.ts` 一份声明，分组为「计划 / 基础信息 / 配置 / 系统」。新增页面在该文件加一项即可，权限页按权限码前缀自动归入对应分节；不要在 `App.vue` 里另写菜单，也不要再按 `permission.parent_id` 给权限页排版。

## Hard rules

- 不引入契约之外的中间件：无 MQ、对象存储、搜索引擎、独立调度中心、微服务。Nginx 不是初期组件。
- 导入先入 staging，整文件校验通过且用户确认后才生成正式版本。
- 写操作留审计；长期主数据版本与单次批次业务决策分开。
- 前端任务先读对应原型；Excel 导入先读 `.harness/import-rules.md`。
- 依赖审批：新增库要在任务报告和 `.harness/decision-log.md` 说明。
