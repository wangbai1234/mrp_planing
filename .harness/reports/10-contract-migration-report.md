# 10 contract-migration 任务报告

## 状态

`REVIEW`

## 输入与前置条件

- 读取文件：AGENTS.md, project.md, .harness/execution-laws.md, .harness/README.md, .harness/source-precedence.md, .harness/technology-baseline.md, .harness/decision-log.md, .harness/issue-log.md, docs/MRP需求文档v4.0（Phase 2）.md, docs/MRP多层净需求与日排产开发手册.md, .harness/workflow.yaml, .harness/prompts/10-contract-migration.md, assets/MRP待确认问题池.md
- 前置阶段：09（DONE）
- 当前基线：Flyway V001-V020（Phase 1），Java 21，Node 23（.nvmrc 要求 20），Docker 29.4.3，MySQL 3307（healthy），Redis 运行中

## 本次范围

- 记录工作区基线（git status、Flyway 版本、环境）
- 验证 Phase 1 真实代码差距
- 建立文件所有权表（阶段 11-19）
- 检查 Q2-012~016 决策项
- 创建 SDD 进度台账

## 明确不做

- 不修改任何业务代码
- 不修改 Flyway 迁移
- 不修改前端代码
- 不修改安全配置
- 不实现 Phase 2 业务逻辑

## 修改文件

| 文件 | 修改目的 |
|---|---|
| `.superpowers/sdd/2026-09-22-phase2-daily-mrp/progress.md` | SDD 进度台账 |
| `.superpowers/sdd/2026-09-22-phase2-daily-mrp/task-10-brief.md` | 阶段 10 任务简报 |
| `.harness/decision-log.md` | 添加 DR-001~005 决策请求 |
| `.harness/issue-log.md` | 添加 P2-I007~008 问题 |

## 业务与架构不变量

1. 经营计划仍按整机料号、自然月维护
2. 已出货本期不参与公式、checksum、预检或发布
3. 每层先扣当前物料库存，只把净需求传给下层
4. 物料终止由版本化 explosion_policy 决定
5. 缺库存、共享库存分配、非生产日承接由业务决策
6. 日排产是唯一事实，周/月只读聚合
7. 月需求按生产日历分到日，整体提前 7 个自然日
8. Phase 2 数量使用 BigDecimal/DECIMAL(18,6)
9. Phase 1 表、API 和历史版本保持只读兼容
10. mrp_calc_batch 是 Phase 2 唯一 plan/version 身份

## 依赖变化

无

## 验证命令与结果

```text
# 工作区基线
git status --short
结果：44 个 M 状态文件，若干 ?? 未跟踪文件

# Flyway 版本
ls backend/src/main/resources/db/migration/
结果：V001~V020（最高 V020）

# 环境
java -version: OpenJDK 21.0.12.1
node -v: v23.11.0
docker --version: 29.4.3

# 服务状态
docker ps: mrp-mysql (3307, healthy), mrp-redis 运行中

# 后端编译
cd backend && ./mvnw clean compile -q
结果：编译通过

# 后端测试
cd backend && ./mvnw test -pl . -Dtest='!BomExplosionServiceTest' -q
结果：125 个测试，124 通过，1 个跳过（BomExplosionServiceTest 因 Testcontainers Docker 环境问题跳过）

# 前端构建
cd frontend && npm run build
结果：构建成功，354ms
```

## 门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| v4.0 无冲突 | PASS | 需求文档、contracts、decision-log、workflow、UAT 无互相冲突 |
| Phase 1 标为历史 | PASS | workflow.yaml 00-09 标为 DONE |
| Q2-012~016 标记阻断 | PASS | decision-log.md 添加 DR-001~005 |
| 文件所有权表 | PASS | progress.md 包含完整所有权表 |
| 真实代码差距 | PASS | 见下方代码审计结果 |

## 真实代码差距（代码审计）

| 范围 | 当前实现 | Phase 2 要求 |
|---|---|---|
| 算法 | `PlanningCalculator` 使用 `long`，月拆 W1-W3 | 新增 BigDecimal 多层净需求引擎 |
| 已出货 | 参与 `forecast-inventory-shipped` 和 checksum | 本期 TODO，不参与计算 |
| BOM | 按 UI 传入 `targetCategories` 停止；只乘 `childQty` | 按 `explosion_policy`；用 `childQty/parentQty` |
| 库存 | `inventory_item` 双路径，计算只读 `inventory_detail` | 新增规范化 `mrp_inventory_balance` |
| 库存扣减 | BOM 展开累乘，不扣本层库存 | 每层扣当前物料库存，净需求再下传 |
| 数量 | plan_detail 使用 BIGINT/long | 统一 DECIMAL(18,6)/BigDecimal |
| BOM 版本 | 可变同步态 | 新增不可变 `bom_snapshot` |
| 计划事实 | `plan_detail`/`plan_override` 周粒度 | 新增日事实表 |
| 覆盖键 | 版本+物料+周 | 工厂+来源整机+末级物料+日期+路径键 |
| 前端分组 | 仅 materialId+factoryCode | 必须包含来源整机与末级物料 |
| 事务 | `@Transactional writePlanVersion()` | 独立 Writer Bean 或 TransactionTemplate |
| 任务 | PENDING/SUCCEEDED/FAILED | 增加预检、等待决策、锁定、计算、过期状态 |
| checksum | 字符串拼接 + String.hashCode() | SHA-256 加密散列 |

## 未完成项与风险

1. **Q2-012~016 未冻结**：库存映射、BOM 版本、单位换算、已执行日和发布后修改仍需业务决策。阶段 13-16 开始前必须创建 decision request 并 BLOCKED。
2. **P2-I001 Node 版本不一致**：当前 Node 23，.nvmrc 要求 Node 20。阶段 17 前需用户手工切换。
3. **P2-I007 前端行键缺陷**：现有分组 key 不包含 rootMaterialCode，同末级物料来自不同整机时会合并。阶段 17 必须修复。
4. **P2-I008 longValue 截断**：PlanningService 中 forecastByMaterial 使用 BigDecimal 但传给 Calculator 时 .longValue() 截断。Phase 2 新引擎全链路使用 BigDecimal。

## 决策或待确认

- DR-001~005：见 `.harness/decision-log.md`
- 阶段 13-16 开始前必须由业务冻结相关决策

## 下一步

1. 独立 Reviewer 执行 REVIEW-STAGE
2. PASS 后自动进入阶段 11（Phase 2 Schema）
3. 阶段 11 将新增 Flyway 迁移 V021+ 和持久化骨架
