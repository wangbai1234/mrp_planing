# MRP Phase 2 多层净需求与日排产 - SDD 进度台账

**项目**: MRP Phase 2
**启动日期**: 2026-09-22
**Coordinator**: Mimo (mimo-v2.5-pro)
**仓库**: /Users/wanglixun/Project/MRP新版

---

## 阶段状态

| 阶段 | 名称 | 状态 | 开始时间 | 完成时间 | 报告 |
|---|---|---|---|---|---|
| 00-09 | Phase 1 历史 | DONE | - | - | 历史报告 |
| 10 | 契约迁移 | REVIEW | 2026-09-22 | - | task-10-report.md |
| 11 | Phase 2 Schema | REVIEW | 2026-09-22 | - | 11-phase2-schema-report.md |
| 12 | 主数据与决策 | REVIEW | 2026-09-22 | - | 12-masterdata-and-decisions-report.md |
| 13 | 库存规范化 | DONE | 2026-09-22 | 2026-09-22 | 13-inventory-normalization-report.md |
| 14 | BOM 快照与净需求 | DONE | 2026-09-22 | 2026-09-22 | 14-bom-snapshot-and-net-requirement-report.md |
| 15 | 日排产与汇总 | DONE | 2026-09-22 | 2026-09-22 | 15-production-calendar-and-daily-planning-report.md |
| 16 | API 与任务流 | DONE | 2026-09-22 | 2026-09-22 | 16-phase2-api-and-task-flow-report.md |
| 17 | 前端 | DONE | 2026-09-22 | 2026-09-22 | 17-phase2-frontend-report.md |
| 18 | 集成/性能/安全 | REVIEW | 2026-09-22 | 2026-09-22 | 18-integration-performance-security-report.md |
| 19 | Docker/UAT | REVIEW | 2026-09-22 | 2026-09-22 | 19-docker-uat-final-audit-report.md |

---

## Agent 能力枚举

| 能力 | 可用性 | 说明 |
|---|---|---|
| Agent spawn (task tool) | ✓ | OpenCode task tool 可用 |
| 独立 Reviewer | ✗ | 无独立会话/人工 Reviewer，使用 Harness 等价闭环 |
| 并行 Agent | ✓ | task tool 支持并行调用 |
| Superpowers CLI | ✗ | 仓库无已验证的 Superpowers 技能/CLI |

**降级模式**: 使用 Harness REVIEW-STAGE / REPAIR-GATE / FINAL-AUDIT 完成等价闭环。同一会话自审不伪装独立审查。

---

## 文件所有权表（阶段 11-19）

### 共享契约（Coordinator 独占）

| 文件/资源 | 所有者 | 说明 |
|---|---|---|
| `.harness/workflow.yaml` | Coordinator | 状态更新 |
| `.harness/decision-log.md` | Coordinator | 决策记录 |
| `.harness/issue-log.md` | Coordinator | 问题台账 |
| `backend/src/main/resources/application*.yml` | Coordinator | 配置合并 |

### 阶段 11: Phase 2 Schema

| 文件 | Agent | 操作 |
|---|---|---|
| `backend/src/main/resources/db/migration/V021*.sql` | A-契约 | 新建迁移 |
| `backend/src/main/java/com/mrp/mrp/domain/*.java` | A-契约 | 新建领域实体 |
| `backend/src/main/resources/mapper/mrp/*.xml` | A-契约 | 新建 Mapper XML |

### 阶段 12: 主数据与决策

| 文件 | Agent | 操作 |
|---|---|---|
| `backend/src/main/java/com/mrp/mrp/service/MrpConfigurationService.java` | C-主数据 | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/DecisionService.java` | C-主数据 | 新建 |
| `backend/src/main/java/com/mrp/mrp/controller/MrpConfigController.java` | C-主数据 | 新建 |

### 阶段 13: 库存规范化

| 文件 | Agent | 操作 |
|---|---|---|
| `backend/src/main/java/com/mrp/inventory/service/InventoryNormalizationService.java` | C-主数据 | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/InventoryAllocationService.java` | C-主数据 | 新建 |

### 阶段 14: BOM 快照与净需求

| 文件 | Agent | 操作 |
|---|---|---|
| `backend/src/main/java/com/mrp/bom/service/BomSnapshotService.java` | B-算法 | 新建 |
| `backend/src/main/java/com/mrp/mrp/engine/NetRequirementExplosionEngine.java` | B-算法 | 新建 |
| `backend/src/test/java/com/mrp/mrp/engine/NetRequirementExplosionEngineTest.java` | B-算法 | 新建 |

### 阶段 15: 日排产与汇总

| 文件 | Agent | 操作 |
|---|---|---|
| `backend/src/main/java/com/mrp/mrp/engine/DailyDistributionEngine.java` | B-算法 | 新建 |
| `backend/src/test/java/com/mrp/mrp/engine/DailyDistributionEngineTest.java` | B-算法 | 新建 |

### 阶段 16: API 与任务流

| 文件 | Agent | 操作 |
|---|---|---|
| `backend/src/main/java/com/mrp/mrp/controller/MrpBatchController.java` | D-API | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/MrpCalculationOrchestrator.java` | D-API | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/MrpResultWriter.java` | D-API | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/MrpPublishService.java` | D-API | 新建 |

### 阶段 17: 前端

| 文件 | Agent | 操作 |
|---|---|---|
| `frontend/src/views/mrp/*.vue` | E-前端 | 新建 |
| `frontend/src/api/mrp.ts` | E-前端 | 新建 |

### 阶段 18-19: 集成/UAT

| 文件 | Agent | 操作 |
|---|---|---|
| `deploy/docker-compose.yml` | F-验证 | 可能更新 |
| `.superpowers/sdd/2026-09-22-phase2-daily-mrp/*-report.md` | F-验证 | 新建 |

---

## 阻断问题

| ID | 问题 | 影响阶段 | 状态 |
|---|---|---|---|
| Q2-012 | 库存组织与可用量映射 | 13 | FROZEN (已冻结) |
| Q2-013 | BOM 有效版本裁决 | 14 | FROZEN (已冻结) |
| Q2-014 | 计量单位换算 | 14 | FROZEN (已冻结) |
| Q2-015 | 已执行日与月中重算 | 15 | PARTIALLY_FROZEN (仅库存分配保留/重新分配未决) |
| Q2-016 | 发布后修改 | 16 | FROZEN (已冻结) |
| P2-I001 | Node 版本不一致 | 17 | WAITING_DECISION |
| P2-I002 | 配置存在 secret-like 默认值 | 18 | OPEN |
| P2-I003 | Playwright 硬编码路径 | 17 | OPEN |
| P2-I004 | Docker/UAT 脚本错误 | 19 | OPEN |

---

## 验证矩阵

| 验证层 | 命令 | 频率 |
|---|---|---|
| 后端编译 | `cd backend && ./mvnw compile` | 每次改动 |
| 后端测试 | `cd backend && ./mvnw test` | 每次改动 |
| 前端构建 | `cd frontend && npm run build` | 每次改动 |
| 前端类型检查 | `cd frontend && npx vue-tsc --noEmit` | 每次改动 |
| 集成联调 | 启动服务 + curl + 浏览器 | 每阶段完成 |

---

*最后更新: 2026-09-22*

## 2026-10-16 续修交接（以此段覆盖过期进度摘要）

- 当前阶段18/19：REVIEW。补充任务planning-factory-access-distribution-conservation-2026-10-16：DONE（限定范围原Reviewer PASS）；报告test-kit/2026-10-16/排产权限与负荷接口续修报告.md。
- 已限定独立PASS：批次/间接资源工厂域、整机负荷SQL、发布投影复用及1000行分块、直接日分配冻结/防重复、v4来源月守恒SQL。最后扩大回归360项通过，主/隔离HTTP前后响应一致，最新大表浏览器通过。
- 最后准确性补丁：孤立非零日事实/PENDING组原来漏检。修改前失败及HTTP证据保留；52项专项、361项限定回归、真实after发布阻断及重启浏览器全部通过，原Reviewer最终限定PASS。DailyPlanningService与RollingPlanningIntegrationTest由主Agent维护；原Reviewer /root/test_audit_oct16 只写continuation-independent-review.md，已结束。
- 主库91/run37、224100事实、0override、历史快照33、物料/分类保护。最终17:40:28保护指纹PASS；禁止对91重算/调整/发布/取消。物料、分类、BOM管理页不测试。
- 服务：主8080/5180、隔离8180/5181；MySQL主3307、隔离3317，隔离Redis6391。隔离大发布10/13保留供核验；临时HTTPfixture测试后清理。私有重启参数在/tmp，不得打印或写入报告。
- 未完成：CAP-1014-03全链路、同步发布及capacity-review全量物化、bulk/全量明细、三轮checksum、并发写、低内存恢复、1万/10万实际导入、导出、V020升级和完整交互门禁。
- 恢复先读新报告及最新证据，再检查服务；勿覆盖原失败日志。现有dirty workspace包含多轮授权修改，禁止reset。

## 2026-10-16 20:03 后续修复状态

- 当前工厂映射/生产日历HTTP工厂域：DONE，原Reviewer限定PASS。49专项、394限定回归、19真实HTTP、双环境浏览器通过；主91/run37/224100事实/0override/快照33保护SHA不变。报告：test-kit/2026-10-16/工厂映射与生产日历工厂域续修报告.md。
- 17:40后还已限定DONE：自动明细索引V047、无override投影、库存快照域、用户scope删除、指纹join内存、旧Phase1导出任务归属。逐项证据见workflow对应review，不代表整个阶段完成。
- 阶段18/19仍REVIEW，CAP-1014-03、TESTKIT-PERF-01整体OPEN。当前审计接口尚无工厂过滤，下一步修复列表/count及配置历史日志读取；capacity-review全量物化/排序、已有override分页、同步发布、大导入导出/并发恢复/V020升级仍未关闭。
- 主API8080/UI5180、隔离API8180/UI5181已加载日历scope补丁。禁止改主batch91/run37和隔离保留大数据10/13；物料/分类/BOM页面不测试。私有启动参数仅由restart_verified_backend.py读取，不输出。

### 2026-10-16 20:23 审计列表工厂域限定DONE
原Reviewer限定PASS：55专项、401限定回归、17真实HTTP、双环境浏览器各1；20:19主库保护SHA不变。报告test-kit/2026-10-16/审计日志工厂域续修报告.md、evidence/audit-scope-independent-review.md。阶段18/19仍REVIEW，权限及性能整体OPEN。后续推进无覆盖capacity-review性能，保留所有大批次。

- 2026-10-16 20:37 无override整机capacity-review流式窄投影补丁REVIEW：82专项、409限定回归PASS；双环境重启后完整336项负荷/指纹不变，主中位9347→1587ms、隔离9389→1669ms；排产页面浏览器各1PASS；主库保护SHA不变。见无覆盖产能确认性能续修报告.md，待原Reviewer。仍保留每条checksum字符串并排序，非恒定内存；override路径/发布/bulk及TESTKIT-PERF-01整体OPEN，阶段18/19 REVIEW。

- 无override整机产能确认流式投影原Reviewer限定PASS，Coordinator登记DONE。证据evidence/capacity-projection-independent-review.md；整体性能/阶段门禁不关闭。

- 2026-10-16 库存策略当前HTTP工厂域补丁REVIEW：30专项、416限定回归、17隔离真实HTTP、双环境浏览器及主只读HTTP通过；主保护SHA不变。混厂提交整份拒绝、原记录仓库授权、列表/count先过滤、未知仅ALL、新审计scope。见库存策略工厂域续修报告.md；等待原Reviewer。CAP-1014-03整体OPEN，旧版本配置域/并发时序及性能总门禁未关闭。

- 2026-10-16 库存策略审计分隔符修复获原Reviewer限定PASS，Coordinator登记当前库存策略HTTP工厂域DONE。复审报告evidence/stock-scope-delimiter-independent-review.md；32专项、418限定回归、14新增HTTP及原17HTTP/双环境浏览器通过，主保护SHA不变。原FAIL保留，阶段18/19 REVIEW及CAP-1014-03/TESTKIT-PERF-01 OPEN。继续历史生产日历版本HTTP工厂域修复，before13项真实HTTP已留证。

- 2026-10-16 历史生产日历版本HTTP域补丁REVIEW：38专项、428限定回归、25隔离真实HTTP及双环境浏览器各1PASS，临时夹具清理0残留；主只读4HTTP、21:20:58保护SHA不变。报告历史生产日历版本工厂域续修报告.md，等待原Reviewer；CAP-1014-03整体OPEN，阶段18/19 REVIEW。旧映射/库存策略版本域及性能剩余任务未关闭。

- 历史生产日历版本HTTP域获原Reviewer限定PASS，Coordinator登记DONE。独立报告evidence/legacy-calendar-scope-independent-review.md；428限定回归、25隔离HTTP与双环境浏览器及主保护SHA通过。仅关闭此子任务，阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。

- 2026-10-16 库存内部复合键获原Reviewer限定PASS，Coordinator登记DONE。21专项、434限定回归、真实MySQL净需求70/125及安全预留20/5、前后HTTP和双环境浏览器通过；主保护SHA不变。报告库存复合键续修报告.md，独立证据evidence/inventory-key-independent-review.md。仅关闭归一化/安全策略内部键；阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。

- 稀疏人工调整明细分页补丁REVIEW：74专项/436限定回归、21万事实逐字段HTTP等价、双环境浏览器、主保护SHA及临时夹具清理通过；等待原独立Reviewer。完整报告见test-kit/2026-10-16/人工调整明细分页性能续修报告.md。整体阶段18/19 REVIEW、安全性能总缺陷OPEN。

- 稀疏人工调整明细分页获原Reviewer限定PASS，Coordinator登记该子任务DONE。独立证据evidence/override-window-independent-review.md；阶段18/19 REVIEW、CAP-1014-03与TESTKIT-PERF-01 OPEN。

- 2026-10-16 历史映射/库存策略版本HTTP工厂域补丁REVIEW：43专项、447限定回归、重启后真实HTTP及双环境大表浏览器通过；主91/run37/224100事实/0override/历史快照33保护SHA不变，临时版本清理0残留。库存按全部引用批次的冻结映射授权，未知/无绑定只ALL，历史库存全局写只ALL。报告历史映射与库存策略版本工厂域续修报告.md，等待原Reviewer。原stockVisible括号导致6项FAIL日志保留，修正后全通过；CAP-1014-03/TESTKIT-PERF-01整体OPEN、阶段18/19 REVIEW。

- Worker失败状态CAS获原Reviewer限定PASS，WORKER-FAIL-CAS-01限定RESOLVED，Coordinator登记子项DONE。11定向、452限定回归、真实FAILED/v2和双浏览器及主保护SHA通过。隔离三轮217275日事实计算均与冻结基准业务SHA一致，35.317–43.820秒，Java峰值RSS约2.10GiB；三轮证据待补独立复核。WORKER-LEASE-RECOVERY-01及整体安全/性能OPEN，阶段18/19 REVIEW。继续全量INSERT分块专项。报告任务失败CAS与大数据重复计算续修报告.md。

- 三轮真实大数据重复计算获原Reviewer限定PASS，见test-kit/2026-10-16/evidence/repeated-calculation-independent-review.md。仅证明冻结输入重复性、所检查不变量与汇总总量；旧清理未逐表确认，主保护早于三轮结束，低内存/独立逐格准确性不在结论中。阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。


### 2026-10-16 续修：计算SQL分块 REVIEW / 库存风险 IN_PROGRESS
计算五处1000行写入保留原事务，7项MySQL专项与459限定回归通过；三轮217275日事实全字段SHA、所检不变量、13表精确清理通过。主浏览器PASS；隔离两次初始加载超时保留，原门限下空闲重验PASS（ready2369ms、grid1168ms、120帧无longtask）。并发计算/清理时页面延迟仍OPEN。详见test-kit/2026-10-16/计算写入分块续修报告.md，等待独立Review。库存风险修复结构化键、重复全池扫描、全量INSERT；修复前5项中3失败，修复后7专项PASS，后续回归/运行验证进行中。主库91/run37/33及隔离10/13只读；阶段18/19 REVIEW与整体问题OPEN不变。

- 计算SQL五处1000行分块获原Reviewer限定PASS，Coordinator登记DONE；evidence/calculation-chunk-independent-review.md。459限定回归、三轮217275事实、13表清理和23:08:53最终主保护通过。并发加载超时、完整物化/长事务/低内存及整体阶段门禁仍开放。

- 库存风险生成子项REVIEW：结构化键/单次池索引/1000行写入；7专项、465限定回归、双后端真实重启、217275事实与1502风险HTTP核对、双浏览器及23:19:53主保护PASS，夹具13表0残留，等待原Reviewer。见库存风险生成续修报告.md。整体安全/性能OPEN、阶段18/19 REVIEW不变。

- 库存风险生成子项获原Reviewer限定PASS，Coordinator登记DONE；证据test-kit/2026-10-16/evidence/stock-risk-independent-review.md。仅关闭结构化键、单次池索引及分块写入；7专项、465限定回归和真实运行/保护通过。大数据阈值、有库存池、风险抽屉UAT及整体安全性能仍OPEN，阶段18/19 REVIEW。

- 2026-10-17 高密度人工调整明细分页子项REVIEW：动态实际权重排序保留PAD SPACE语义，26专项、472限定回归、双后端重启、6种筛选各5次真实HTTP全响应等价、双环境浏览器及00:34:41最终主保护PASS；临时33/run27六表0残留，隔离来源13事实SHA不变。HTTP首页/中/尾中位2278/3176/3157→1221/1373/1440ms。报告test-kit/2026-10-16/高密度人工调整明细分页续修报告.md；等待原Reviewer。MySQL升级须复跑WEIGHT_STRING等价/长权重测试；阶段18/19 REVIEW，CAP-1014-03与TESTKIT-PERF-01整体OPEN。

- 高密度人工调整明细分页获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/dense-override-independent-review.md。26专项、472限定回归、完整HTTP等价/双浏览器/清理及最终主保护通过。隔离浏览器初载10.4秒及WEIGHT_STRING升级复验风险保留，阶段18/19 REVIEW、CAP-1014-03与TESTKIT-PERF-01整体OPEN。

- 2026-10-17 Worker心跳调度子项REVIEW：独立单线程续租，默认计算并发不变；12专项、473限定回归、双后端真实重启、34秒隔离阻塞计算租约增长、双浏览器与00:52:23主保护PASS，六表0残留。报告test-kit/2026-10-16/Worker心跳调度续修报告.md。等待原Reviewer；完整WORKER-LEASE-RECOVERY-01、失败清理批次状态、CAP/PERF整体OPEN及阶段18/19 REVIEW不变。

- Worker心跳调度获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/worker-heartbeat-independent-review.md。只关闭同步计算阻塞调度根因，完整恢复/fencing、阶段18/19 REVIEW及CAP/PERF整体OPEN不变。继续失败清理批次状态保护，原before复现证据保留。

- 2026-10-17 Worker失败批次状态保护子项REVIEW：16专项/477限定回归、双重启、8例真实Worker/active事实/六表0残留、双环境浏览器及01:09:55主保护通过。Chrome启动首败保留，原配置重验PASS。报告test-kit/2026-10-16/Worker失败批次状态保护续修报告.md，待原Reviewer；读取前后继claim和删除/activation竞态仍OPEN，CAP/PERF整体OPEN、阶段18/19 REVIEW。

- Worker失败批次状态保护获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/worker-batch-state-independent-review.md。16专项/477限定回归/真实Worker与双浏览器/主保护通过；读取前后继claim和删除/activation竞态仍OPEN，阶段18/19 REVIEW，CAP/PERF整体OPEN。

- 2026-10-17 导入确认幂等/回滚子项REVIEW：30专项、498限定回归、双重启health、33隔离真实确认HTTP、双环境排产浏览器及01:25:32最终主保护PASS；临时数据精确清理，主91/run37/224100事实/0override/历史快照33不变。报告test-kit/2026-10-16/导入确认幂等与回滚续修报告.md，待原Reviewer。只限同一任务确认；跨任务同文件、forecast不同任务versionNo并发、upload连接级LAST_INSERT_ID、staging重启恢复仍OPEN。阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 导入确认幂等/回滚获原Reviewer限定PASS，Coordinator登记同一任务确认子项DONE；evidence/import-confirmation-independent-review.md。30专项/498限定回归/33真实HTTP/双浏览器及主保护通过；跨任务/versionNo/upload连接级ID/缓存恢复仍OPEN，阶段18/19 REVIEW，CAP/PERF整体OPEN。

- 2026-10-17 上传任务身份/首次并发子项REVIEW：ImportTaskCreation按唯一requestKey读取真实任务，避免连接局部LAST_INSERT_ID，唯一键并发只复用匹配owner/type/checksum/key的胜出任务；三upload替换原重建。36专项、504限定回归、42有效上传/18确认HTTP、双重启/双浏览器及01:41:54主保护PASS，临时0残留。报告test-kit/2026-10-16/上传任务身份与并发续修报告.md，待原Reviewer。文件同路径并发、跨任务、版本号并发、重启缓存/大导入仍OPEN；阶段18/19 REVIEW，CAP/PERF整体OPEN。

- 上传任务身份/首次并发获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/import-upload-identity-independent-review.md。36专项/504限定回归/42上传18确认及双浏览器/主保护通过。后续一万行真实forecast预览202/10000成功、确认500暴露源spec_model256到forecast platform128截断，失败已回滚/精确清理且隔离保护通过，进入独立字段容量修复；阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 2026-10-17 经营计划规格字段容量子项REVIEW：新增V048将platform128扩至256，2项before真实MySQL过长失败、38专项/506限定回归PASS；双重启48迁移及原47checksum/平台值保护通过。四组真实大导入10000/100000库存与60000/100002经营计划月份明细逐行/总量PASS，峰值RSS442.4MiB，无重启；临时正式行清理及隔离保护通过。双浏览器和02:03:41主保护PASS。旧local测试4条夹具精确清理后主经营计划SHA恢复，另开测试隔离修复。阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 规格字段V048及四组真实大导入分别获原Reviewer限定PASS，Coordinator登记两个子项DONE，evidence/forecast-platform-independent-review.md与large-import-independent-review.md。仅字段完整容量和本次四规模精确导入；同步慢/N+1、任意低内存/重启恢复、新输入完整排产及整体阶段18/19/CAP/PERF仍OPEN。旧local测试隔离72专项PASS，最终限定回归进行中。

- 2026-10-17 旧测试MySQL隔离子项REVIEW：10类local测试继承现有BaseIntegrationTest，72专项与506限定回归PASS；两库forecast行数/SHA不再变化、health200/UP、双浏览器和最终主保护PASS。报告旧测试数据库隔离续修报告.md；原local副作用4版本/4旧计划/1终态任务已精确清理且原SHA恢复，失败/清理证据保留。仅测试代码；阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 2026-10-17 旧测试MySQL隔离子项获原Reviewer限定PASS，Coordinator登记DONE，见evidence/legacy-isolation-independent-review.md；仅关闭10类local测试数据库隔离，MaterialApiTest、Redis共享、其他local测试和并行全局隔离保持OPEN。
