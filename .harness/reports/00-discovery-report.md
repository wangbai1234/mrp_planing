# 00-discovery 任务报告

## 状态

`REVIEW`

## 输入与前置条件

- 读取文件：project.md、AGENTS.md、.harness 全部契约文档（17 份）、docs/MRP需求文档v3.0（Phase 1）.md、docs/MRP排产页面操作手册.md、assets 全部 8 份业务文档、templates 3 份 Excel 模板、prototype 4 份 HTML 原型
- 前置阶段：无（首个阶段）
- 当前基线：project.md 冻结技术栈 + .harness/decision-log.md 已冻结决策 13 条

## 本次范围

发现与事实冻结：审计现有资产、确认 Phase 1 范围、补全追踪矩阵、识别冲突、设计目录结构、制定实施排期。不修改业务代码。

## 明确不做

- BOM 缺料核算和 PCBA 排产（Phase 2）
- 日能力到周能力换算（source-precedence C001 已裁决）
- MQ / XXL-JOB / Quartz / MinIO / ES / MongoDB / 微服务 / K8s
- 独立系统设置页面（source-precedence C004 已裁决）
- "滚动一周"按钮（source-precedence C003 已裁决）
- 排产 Excel 反向导入（需求文档 11.11 明确"尚未确认"）
- 通知提醒和完整异常闭环（待确认问题池 Q011/Q012 标为 Phase 2）
- Nginx 作为初期必选组件（decision-log 已决策，Spring Boot 直接托管静态资源）
- H2 作为唯一集成测试数据库

---

## 一、现有资产审计

### 1.1 目录结构

```text
MRP新版/
├── project.md                         ✅ 存在，内容完整（184 行）
├── AGENTS.md                          ✅ 存在
├── .harness/                          ✅ 存在，含 30+ 文件
├── docs/                              ✅ 存在，3 份文档
├── assets/                            ✅ 存在，8 份业务文档 + 2 份工作日志
├── templates/                         ✅ 存在，3 份 Excel 模板
├── prototype/                         ✅ 存在，4 份 HTML 原型
├── scripts/                           ✅ 存在，1 份 Python 脚本
├── mrp-ui-design/                     ✅ 存在，UI 设计参考
├── backend/                           ❌ 不存在（待 01 阶段创建）
├── frontend/                          ❌ 不存在（待 01 阶段创建）
├── deploy/                            ❌ 不存在（待 09 阶段创建）
└── .gitignore                         ✅ 存在，覆盖 macOS/IDE/Node/Python/临时文件
```

### 1.2 Excel 模板审计

| 模板 | 路径 | 用途 | 状态 |
|---|---|---|---|
| 经营计划导入模板 | `templates/MRP经营计划导入模板.xlsx` | 经营计划 Excel 导入 | ✅ 已生成 |
| 库存快照导入模板 | `templates/MRP库存快照导入模板.xlsx` | 库存快照 Excel 导入 | ✅ 已生成 |
| 十二周排产计划模板 | `templates/MRP十二周排产计划模板.xlsx` | 排产导出参考 | ✅ 已生成 |

模板生成脚本：`scripts/generate_mrp_templates.py`

### 1.3 原型审计

| 原型 | 路径 | 风格 | 状态 |
|---|---|---|---|
| MRP滚动排产可交互原型.html | `prototype/` | 主原型，操作手册引用 | ✅ 主参考 |
| MRP原型图.html | `prototype/` | 早期版本 | 📄 参考 |
| MRP原型图-飞书风格.html | `prototype/` | 飞书风格 | 📄 参考 |
| MRP原型图-企业级风格.html | `prototype/` | 企业级风格 | 📄 参考 |

### 1.4 Harness 文档完整性

| 契约文档 | 状态 | 说明 |
|---|---|---|
| architecture.md | ✅ | 组件边界、分层、异步任务模型 |
| data-contract.md | ✅ | 实体、版本、事务、索引 |
| calculation-contract.md | ✅ | 排产公式、拆周、产能、可重复性 |
| import-contract.md | ✅ | Excel 解析、staging、校验 |
| api-contract.md | ✅ | API 资源、错误格式、权限 |
| security-and-ops.md | ✅ | JWT、审计、配置、备份 |
| task-execution-contract.md | ✅ | 任务认领、心跳、重试 |
| redis-contract.md | ✅ | Key 规范、TTL、降级 |
| test-and-gates.md | ✅ | 测试策略、性能基线、门禁 |
| source-precedence.md | ✅ | 7 条已裁决冲突（C001-C007）+ 1 条未确认边界 |
| traceability-matrix.md | ✅ | 17 条规则，TBD 待补全 |
| code-standards.md | ✅ | Java/SQL/Vue/测试约束 |
| change-control.md | ✅ | 变更分类和审批 |
| technology-baseline.md | ✅ | 运行中间件边界 |
| handoff.md | ✅ | 会话交接规则 |
| issue-log.md | ✅ | 当前无开放问题 |
| decision-log.md | ✅ | 9 条已冻结决策 |
| workflow.yaml | ✅ | 10 个阶段定义 |
| task-state.md | ✅ | 状态机和报告模板 |
| templates/ | ✅ | 3 份报告模板 |

---

## 二、Phase 1 范围清单与排除项

### 2.1 Phase 1 包含

| 模块 | 功能 | 对应规则 |
|---|---|---|
| 经营计划 | Excel 导入、版本管理、6 个月 Forecast | R006, R011 |
| 库存快照 | Excel 导入、按工厂/仓库汇总、排除不良品仓 | R007, R016 |
| 已出货 | CRM 按整机料号+月份累计 | R008 |
| 排产计算 | 月可排量 MAX(0)、月拆周余数前置、提前量、12 周窗口 | R001, R002, R003, R005, R013 |
| 产能 | 按产线维护周产能、超限标记 | R009, R014 |
| 人工调整 | 修改、留痕、恢复自动值 | R004 |
| 重算 | 生成新版本、保留人工覆盖 | R012 |
| 发布 | 草稿→已发布、发布后可改 | R010 |
| 版本对比 | 并排表格、差异高亮 | R012 |
| 导出 | 自定义字段导出 Excel | R017 |
| 自动重算开关 | 经营计划和库存导入后可选自动重算 | C005 |
| 审计 | 全部写操作审计记录 | project.md §2.8 |
| 权限 | 5 角色、工厂数据域 | security-and-ops.md |
| 异步任务 | MySQL 任务表 + Worker 轮询 | task-execution-contract.md |

### 2.2 Phase 1 明确排除

| 排除项 | 原因 | 来源 |
|---|---|---|
| BOM 展开和层级构建 | Phase 2 | project.md §1 |
| 缺料核算 | Phase 2 | project.md §1 |
| PCBA 级排产 | Phase 2 | project.md §1 |
| 通知与提醒 | Phase 2 | Q011 待确认 |
| 异常闭环处理 | Phase 2 | Q012 待确认 |
| 日能力到周能力换算 | 已裁决不做 | C001 |
| "滚动一周"按钮 | 已裁决不做 | C003 |
| 独立系统设置页面 | 已裁决不做 | C004 |
| 排产 Excel 反向导入 | 未确认 | 需求文档 11.11 |

---

## 三、追踪矩阵计划映射

以下将 traceability-matrix.md 中 17 条规则的 TBD 替换为具体计划：

| 规则 | 领域模块 | 数据证据 | API/UI 证据 | 自动化测试计划 | 阶段 |
|---|---|---|---|---|---|
| R001 月可排量 MAX(0) | planning | plan_detail: system_quantity | GET /plans/{id}/grid | 规则单测 + 黄金数据 | 04 |
| R002 月度拆周余数前置 | planning | plan_detail: slot, is_carry | 12周网格 carry 标签 | 规则单测余数/整除 | 04 |
| R003 下月第一份提前到上月W4 | planning | plan_detail: physical_month, source_month | 提前量标签与来源合计 | 规则单测跨月 | 04 |
| R004 人工调整留痕恢复 | planning/audit | plan_override + audit_log | POST/DELETE overrides + 抽屉 | 服务集成测试 | 05 |
| R005 当前周起固定12周 | planning | plan_version: current_week_start | grid + 定位当前周 | API 测试 + 前端验收 | 04, 06 |
| R006 经营计划半月更新及开关 | forecast/planning | forecast_version + calc_task | POST /forecast-imports + 开关 | Excel 测试 + API | 03, 05 |
| R007 库存每日更新及开关 | inventory/planning | inventory_snapshot + calc_task | POST /inventory-imports + 开关 | Excel 测试 + API | 03, 05 |
| R008 CRM按料号+月份累计 | shipment | shipment_batch/detail | POST /shipment-sync-tasks | 服务集成测试 | 03 |
| R009 按产线周产能 | capacity/planning | capacity_version/line | GET/POST/PUT capacity-lines | API 测试 | 03, 05 |
| R010 草稿→已发布发布后可改 | planning/audit | plan_version: status + audit_log | POST /plans/{id}/publish | API 测试 | 05 |
| R011 经营计划版本 | forecast | forecast_version | GET /forecast-versions | 服务集成测试 | 03 |
| R012 重算生成版本人工不生成 | planning/versioning | plan_version + plan_override | POST /recalculations + 对比 | 规则单测 + API | 04, 05 |
| R013 负值归零 | planning | plan_detail: system_quantity=0 | grid 计算详情 | 规则单测边界 | 04 |
| R014 超产能人工调整 | capacity/planning | exception fields | 红色异常 + 调整 | API 测试 + 前端验收 | 05, 06 |
| R015 特殊月份人工处理 | planning | plan_override | 人工调整入口 | 前端验收 | 06 |
| R016 工厂分开多仓汇总排除不良品 | inventory/planning | inventory_detail | 库存口径 | 服务集成测试 | 03 |
| R017 自定义导出字段 | importexport | export_task | POST /plans/{id}/exports + 字段选择器 | API 测试 | 05 |

---

## 四、业务冲突、文档过时点和待确认问题

### 4.1 已裁决冲突（按 source-precedence.md 执行）

| ID | 冲突 | 裁决 |
|---|---|---|
| C001 | 旧规则 R009/知识库写"当天排产有限额" vs 按产线维护周产能 | 只使用 weekly_capacity |
| C002 | 旧规则库 R013 标为待修订 vs MAX(0, ...) | 负值归零 |
| C003 | 早期原型"滚动一周"按钮 vs 固定12周横向查看 | 无按钮，只有定位当前周 |
| C004 | 早期原型独立系统设置 vs 排产页开关 | 不创建独立页面 |
| C005 | R011 旧文字"版本变化自动重算" vs 分别有开关 | 开关控制 |
| C006 | UAT TC005 部分示例与余数前置冲突 | 余数前置，UAT 示例待修订 |

### 4.2 文档过时点

| 文档 | 过时内容 | 应更新为 | 优先级 |
|---|---|---|---|
| MRP业务知识库.md §产能约束 | "当天的排产是有限额的" | 按产线维护周产能 | P2（已裁决 C001，但知识库文字未同步） |
| MRP业务知识库.md §项目基本信息 | "需求文档 v2.0" | v3.0/v3.3 | P3 |
| MRP需求基线.md §产能约束 | "当日排产限额" | 按产线周产能 | P2（已裁决 C001，基线文字未同步） |
| MRP-UAT验收案例库.md TC005 | 部分示例与余数前置规则不完全一致 | 按余数前置修正 | P2（已裁决 C006） |
| MRP-UAT验收案例库.md TC013 | "每日产能限额 500" | 每周产能限额 | P2 |

**决策**：以上过时点已由 source-precedence.md 裁决覆盖，不阻塞实施。在后续阶段编写测试和 UAT 时按最新裁决执行，不反向修改算法。文档更新可在 Prompt 00 完成后作为 A 类变更补充。

### 4.3 待确认问题（已决策）

| ID | 问题 | 决策 | 决策日期 |
|---|---|---|---|
| U001 | 超产能是否硬阻断发布 | ✅ 硬阻断：发布 API 前校验超限，有超限则拒绝发布并返回异常和差额 | 2026-09-03 |
| U002 | CRM 已出货数据同步方式 | ✅ Phase 1 对接真实接口；当前先实现接口抽象层和 Excel 导入 fallback，真实 CRM 接口后续提供 | 2026-09-03 |
| U003 | 用户/权限初始化方式 | ✅ Flyway 种子 SQL 创建默认 ADMIN 用户和 5 角色初始数据 | 2026-09-03 |
| U004 | 部署目标环境 TLS 入口 | ⏳ 本地开发用 Docker Compose；部署环境 TLS 在 09 阶段前确认 | 待定 |

**本地环境检查结果**：Docker 29.4.3 ✅ / Java 17 ⚠️ 需装 21 / Node v23 + nvm ✅ / npm 10.9.2 ✅ / Maven 3.9.16 ✅ / MySQL 客户端 ✅ / Redis CLI ✅

---

## 五、后端、前端、部署目录设计

### 5.1 后端目录

```text
backend/
├── pom.xml
├── mvnw / mvnw.cmd
├── .mvn/wrapper/
│   ├── maven-wrapper.jar
│   └── maven-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/com/mrp/
│   │   │   ├── MrpApiApplication.java          # API profile 启动
│   │   │   ├── MrpWorkerApplication.java       # Worker profile 启动
│   │   │   ├── common/                          # 公共：异常、响应、工具
│   │   │   │   ├── exception/
│   │   │   │   ├── response/
│   │   │   │   └── util/
│   │   │   ├── config/                          # Spring 配置
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── BatchConfig.java
│   │   │   │   ├── RedisConfig.java
│   │   │   │   └── WebMvcConfig.java
│   │   │   ├── auth/                            # 认证与权限
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── domain/
│   │   │   │   └── repository/
│   │   │   ├── masterdata/                      # 基础数据（工厂、仓库、物料）
│   │   │   ├── forecast/                        # 经营计划
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── domain/
│   │   │   │   └── repository/
│   │   │   ├── inventory/                       # 库存快照
│   │   │   ├── shipment/                        # 已出货
│   │   │   ├── capacity/                        # 产能配置
│   │   │   ├── planning/                        # 排产核心
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── domain/                      # 领域模型（公式、拆周）
│   │   │   │   ├── batch/                       # Spring Batch Job/Step
│   │   │   │   └── repository/
│   │   │   ├── versioning/                      # 版本管理
│   │   │   ├── importexport/                    # 导入导出
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── excel/                       # POI SAX 解析
│   │   │   │   └── repository/
│   │   │   ├── audit/                           # 审计日志
│   │   │   └── task/                            # 异步任务管理
│   │   │       ├── controller/
│   │   │       ├── service/                     # 任务认领、心跳
│   │   │       └── repository/
│   │   ├── resources/
│   │   │   ├── application.yml
│   │   │   ├── application-api.yml
│   │   │   ├── application-worker.yml
│   │   │   ├── application-local.yml
│   │   │   ├── db/migration/                    # Flyway 迁移
│   │   │   │   ├── V001__init_schema.sql
│   │   │   │   └── ...
│   │   │   ├── mapper/                          # MyBatis XML
│   │   │   │   ├── forecast/
│   │   │   │   ├── inventory/
│   │   │   │   ├── shipment/
│   │   │   │   ├── capacity/
│   │   │   │   ├── planning/
│   │   │   │   ├── versioning/
│   │   │   │   ├── importexport/
│   │   │   │   ├── audit/
│   │   │   │   └── task/
│   │   │   └── static/                          # Vue 构建产物（部署时复制）
│   │   └── ...
│   └── test/
│       ├── java/com/mrp/
│       │   ├── planning/domain/                 # 规则单测（纯 Java）
│       │   ├── forecast/                        # 服务集成测试
│       │   ├── inventory/
│       │   ├── planning/
│       │   └── importexport/excel/              # Excel 解析测试
│       └── resources/
│           └── test-data/                       # 测试用 Excel 和数据
├── .env.example
└── Dockerfile
```

### 5.2 前端目录

```text
frontend/
├── package.json
├── .nvmrc                                      # 固定 Node.js LTS 主版本
├── tsconfig.json
├── vite.config.ts
├── index.html
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── router/
│   │   └── index.ts
│   ├── api/                                    # fetch 封装
│   │   ├── client.ts
│   │   ├── types.ts
│   │   ├── auth.ts
│   │   ├── forecast.ts
│   │   ├── inventory.ts
│   │   ├── shipment.ts
│   │   ├── capacity.ts
│   │   ├── planning.ts
│   │   └── export.ts
│   ├── views/
│   │   ├── Login.vue
│   │   ├── planning/
│   │   │   ├── PlanningGrid.vue                # 12 周排产宽表
│   │   │   ├── OverrideDrawer.vue              # 人工调整抽屉
│   │   │   ├── VersionCompare.vue              # 版本对比
│   │   │   └── RecalculationPanel.vue          # 重算与设置
│   │   ├── forecast/
│   │   │   ├── ForecastImport.vue
│   │   │   └── ForecastVersions.vue
│   │   ├── inventory/
│   │   │   └── InventoryImport.vue
│   │   ├── capacity/
│   │   │   └── CapacityConfig.vue
│   │   └── export/
│   │       └── ExportConfig.vue
│   ├── composables/                            # 组合式函数
│   │   ├── useAuth.ts
│   │   ├── useTaskPolling.ts
│   │   └── usePlanningGrid.ts
│   ├── components/                             # 通用组件
│   │   ├── Layout.vue
│   │   ├── AppHeader.vue
│   │   ├── WeekCell.vue
│   │   └── StatusBadge.vue
│   └── styles/
│       └── variables.scss
├── .env.example
├── eslint.config.ts
└── Dockerfile                                  # 构建阶段产物复制到 backend static
```

### 5.3 部署目录

```text
deploy/
├── docker-compose.yml                          # mysql + redis + api + worker
├── docker-compose.local.yml                    # 本地开发覆盖
├── .env.example
├── mysql/
│   └── init.sql                                # 首次初始化（可选）
└── scripts/
    ├── start-api.sh
    └── start-worker.sh
```

---

## 六、实施排期、风险和门禁

### 6.1 排期总览

```
00-discovery ──→ 01-backend-bootstrap ──→ 02-schema ──→ 03-import ──→ 04-calculation
                                                                    │
                                                                    ▼
09-docker-uat ←── 08-test ←── 07-integration ←── 06-frontend ←── 05-planning-api
```

### 6.2 各阶段详情

#### 01-backend-bootstrap（后端骨架）

| 项 | 内容 |
|---|---|
| 目标 | 建立 Spring Boot 工程、Maven Wrapper、多 profile、健康检查、Docker 基础 |
| 前置 | 00 PASS |
| 将创建 | `backend/` 完整目录结构、pom.xml、Application 类、配置文件、Dockerfile、.env.example |
| 风险 | Maven Wrapper 版本兼容性 |
| 门禁 | `./mvnw compile` 通过、`/actuator/health` 可访问、配置分环境、无真实密钥 |
| 预计复杂度 | 低 |

#### 02-schema-and-migrations（数据库迁移）

| 项 | 内容 |
|---|---|
| 目标 | 建立全部 MySQL 表、索引、Flyway 迁移脚本 |
| 前置 | 01 PASS |
| 将创建 | `V001__init_schema.sql` 等迁移文件 |
| 风险 | 索引设计影响后续查询性能 |
| 门禁 | 空库迁移成功、重复执行幂等、索引存在、`./mvnw test` 通过 |
| 预计复杂度 | 中 |

#### 03-import-and-masterdata（导入与基础数据）

| 项 | 内容 |
|---|---|
| 目标 | 经营计划、库存、已出货、产能的导入和 CRUD |
| 前置 | 02 PASS |
| 将涉及 | forecast、inventory、shipment、capacity、masterdata 模块 |
| 风险 | Excel 日期序列值 46388 识别、跨年月份、料号文本 |
| 门禁 | 三类模板解析测试通过、跨年和文本料号测试通过、`./mvnw test` 通过 |
| 预计复杂度 | 高（POI SAX + 日期处理） |

#### 04-calculation-engine（排产计算引擎）

| 项 | 内容 |
|---|---|
| 目标 | Spring Batch 排产计算、月可排量、拆周、提前量、12 周窗口、可重复性 |
| 前置 | 03 PASS |
| 将涉及 | planning/domain、planning/batch、planning/service |
| 风险 | 拆周余数前置、跨月提前量、月中锁定逻辑复杂 |
| 门禁 | 黄金数据测试通过、守恒校验、可重复性 checksum 一致、月中锁定正确 |
| 预计复杂度 | 高（核心算法） |

#### 05-planning-api（排产 API）

| 项 | 内容 |
|---|---|
| 目标 | 排产查询、人工调整、重算、发布、版本对比、导出 API |
| 前置 | 04 PASS |
| 将涉及 | planning/controller、versioning、importexport |
| 风险 | 幂等、事务边界、长任务 202 |
| 门禁 | 权限、幂等、事务、长任务状态通过 |
| 预计复杂度 | 中 |

#### 06-frontend（前端页面）

| 项 | 内容 |
|---|---|
| 目标 | Vue 3 排产宽表、导入、产能配置、版本对比、导出页面 |
| 前置 | 05 PASS |
| 将涉及 | frontend/ 完整目录 |
| 风险 | 12 周宽表虚拟化、横向滚动体验 |
| 门禁 | 核心页面操作闭环、无控制台错误、Vue 构建产物由 Spring Boot 正确托管 |
| 预计复杂度 | 高（宽表 + 交互） |

#### 07-integration-and-security（集成与安全）

| 项 | 内容 |
|---|---|
| 目标 | JWT 认证、角色权限、工厂数据域、前后端联调 |
| 前置 | 05 + 06 PASS |
| 将涉及 | auth 模块、全局安全配置、前端路由守卫 |
| 风险 | 工厂数据域过滤遗漏 |
| 门禁 | 权限绕过测试、工厂隔离测试、前后端联调通过 |
| 预计复杂度 | 中 |

#### 08-test-performance（测试与性能）

| 项 | 内容 |
|---|---|
| 目标 | 规则测试补全、集成测试、并发测试、数据量基线 |
| 前置 | 07 PASS |
| 风险 | 10 万整机性能基线未验证 |
| 门禁 | 规则测试覆盖 17 条、集成测试通过、checksum 可重复、任务失败无半版本 |
| 预计复杂度 | 中 |

#### 09-docker-uat（容器化与 UAT）

| 项 | 内容 |
|---|---|
| 目标 | Docker Compose 从空库启动、UAT 案例全部执行、交付文档 |
| 前置 | 08 PASS |
| 风险 | 部署环境 TLS 入口未确认 |
| 门禁 | Docker 从空库启动成功、UAT 案例库全部有结果、阻断项清零 |
| 预计复杂度 | 中 |

### 6.3 关键风险汇总

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| Excel 日期序列值识别 | 03 阶段导入失败 | POI SAX + 真实样本测试覆盖 46388 |
| 拆周余数和提前量算法 | 04 阶段核心逻辑错误 | 黄金数据测试 + UAT TC005 |
| 月中锁定范围判断 | 04 阶段重算结果错误 | 规则单测 + 集成测试 |
| 12 周宽表性能 | 06 阶段前端卡顿 | Element Plus Table V2 虚拟化 |
| CRM 接口不可用 | 03 阶段已出货数据缺失 | 先用 Excel 导入模拟（待确认 U002） |
| 部署环境无 TLS 入口 | 09 阶段安全风险 | Prompt 09 中提出受控方案 |

---

## 七、待确认问题清单（本次新增）

| ID | 问题 | 优先级 | 影响阶段 | 处理方式 |
|---|---|---|---|---|
| U001 | 超产能是否硬阻断发布 | P1 | 05 | 按 source-precedence 未确认边界执行 |
| U002 | CRM 数据同步方式 | P1 | 03 | 先实现 Excel 导入通道，CRM 接口作为增强 |
| U003 | 用户/权限初始化方式 | P2 | 07 | 种子 SQL 或注册接口 |
| U004 | 部署环境 TLS 入口 | P2 | 09 | 在 09 阶段前确认 |

---

## 八、业务不变量

- 数量使用 BIGINT/long，禁止 float/double 做业务计算
- 所有正式计算绑定输入快照和规则版本
- 自动值、人工覆盖值、最终生效值分开保存
- 排产计算可重复：相同输入+规则+当前周 → 相同结果
- 大任务不在同步 HTTP 中等待
- 所有导入先进 staging，校验通过后才生成正式版本
- 所有写操作有审计记录
- 人工修改不生成版本，重算才生成新版本
- 系统和产能统一按周，不做日能力到周能力换算
- Phase 1 不做 BOM 缺料和 PCBA 排产

---

## 九、依赖变化

- 无新增依赖。全部使用 project.md 冻结技术栈。

---

## 十、验证命令与结果

```text
# 本次为发现阶段，无业务代码可验证
# 验证点：
1. .harness/workflow.yaml 00 状态已从 TODO → IN_PROGRESS → REVIEW  ✅
2. .harness/reports/00-discovery-report.md 已创建                    ✅
3. .harness/traceability-matrix.md TBD 已替换为具体计划映射          ✅
4. 未修改任何业务代码文件                                            ✅
```

---

## 十一、门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| 需求、规则、待确认项和技术栈冲突清单 | PASS | 本报告 §一~§四 |
| 追踪矩阵 17 条规则有计划映射 | PASS | 本报告 §三 + traceability-matrix.md |
| 目录结构设计完成 | PASS | 本报告 §五 |
| 实施排期和风险识别完成 | PASS | 本报告 §六 |
| 未引入未冻结中间件 | PASS | 全文无新依赖 |
| 未把 Phase 2 做进 Phase 1 | PASS | §二排除项明确 |

---

## 十二、未完成项与风险

- 文档过时点（§4.2）可在后续阶段作为 A 类变更更新，不阻塞
- U001-U004 待用户确认，不阻塞 01 阶段启动
- prototype/ 中 4 份 HTML 原型需在 06 阶段进一步比对页面需求

---

## 十三、决策或待确认

- 无新增决策。已裁决项按 source-precedence.md 和 decision-log.md 执行。
- 待确认问题见 §七。

---

## 下一步

- 等待用户确认本报告
- 确认后执行 `.harness/prompts/01-backend-bootstrap.md`
