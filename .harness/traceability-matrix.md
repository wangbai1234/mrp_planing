# Phase 2 规则追踪矩阵

状态说明：`SPECIFIED` 表示规则已冻结但尚无完整实现证据；`IMPLEMENTED` 表示代码和定向测试已存在；`VERIFIED` 表示代码、自动化测试、真实 API/UI 和运行证据全部存在。2026-09-29 的验收已证明多数实现边界，但由于工厂/仓库主数据、库存策略和业务周版本未准备，涉及真实计算结果的规则仍不得标为 `VERIFIED`。

| ID | 规则 | 主要实现边界 | 最低验收证据 | 当前状态 |
|---|---|---|---|---|
| P2-R001 | 经营计划按整机+自然月 | forecast version/detail | 跨年导入与不可变版本 | SPECIFIED |
| P2-R002 | 输入和业务决策绑定冻结 | mrp_calc_batch/checksum | 输入变化 STALE、重复计算一致 | SPECIFIED |
| P2-R003 | 已出货 TODO，不参与公式/checksum/发布 | shipment nullable 预留 | 出货变化结果不变 | SPECIFIED |
| P2-R004 | 分类角色版本化维护 | category role version/detail | 缺角色阻断、版本不可变 | IMPLEMENTED |
| P2-R005 | 供应方式与下钻策略独立 | material policy version/detail | STOP/EXPLODE 与自制/外购/委外矩阵 | IMPLEMENTED |
| P2-R006 | 不确定项由业务前置决策 | issue/decision + 分析波次 | WAITING_DECISION/REANALYZING 闭环 | IMPLEMENTED |
| P2-R007 | 库存规范化为单一真源；按快照+仓库+物料+GOOD/DEFECTIVE 汇总 | inventory_snapshot_scope + mrp_inventory_balance | 三仓隔离、良品参与、不良品排除且不重复 | IMPLEMENTED |
| P2-R008 | 缺库存不默认0，明确0合法 | INVENTORY_MISSING issue | 缺行确认、快照修正、0行区分 | IMPLEMENTED |
| P2-R009 | 共享库存按来源月+经营计划顺序自动分配，支持人工覆盖 | forecast priority + pool/allocation | 稳定顺序、守恒、冲突409、上游失效 | IMPLEMENTED |
| P2-R009A | 一厂多仓、良品仓优先级扣减、工厂隔离与安全/预警库存 | factory_warehouse_mapping/冻结明细/control policy/pool | 同厂多良品仓按数字优先级再按首次维护顺序扣减；不良品仓排除、跨厂不互借、安全库存硬保底、预警库存只提示；见 2026-10-28 需求核验稿与测试报告 | IMPLEMENTED（工程复核 PASS；真实计算 UAT 待业务数据） |
| P2-R010 | 每层扣本节点库存，只传净需求 | NetRequirementExplosionEngine | A/B/C/D 黄金值和逐层守恒 | SPECIFIED |
| P2-R011 | childQty/parentQty，异常显式阻断 | BOM snapshot/validation | parent/child <=0、循环、无BOM、EXCLUDED | SPECIFIED |
| P2-R012 | BOM 按库存组织+父料号+子料号选择最高有效版本后固化 | bom_snapshot/detail | 重复最高版本、有效期重叠、组织不明阻断与 checksum | IMPLEMENTED |
| P2-R013 | 日事实是唯一排产事实 | daily detail + override | 周/月只读 SUM，批次唯一身份 | IMPLEMENTED |
| P2-R014 | 新规则有效生产日等权分配，历史冻结权重兼容 | calendar + DailyDistributionEngine | 节假日/调休/无生产日；新规则31日真实计算 | VERIFIED（本次补充范围） |
| P2-R015 | 每个来源日提前7个自然日 | source_date/schedule_date | 跨月/跨年/闰年 | IMPLEMENTED |
| P2-R016 | 非生产目标日进入待分配 | unallocated quantity | 未归零禁止发布、业务落点 | IMPLEMENTED |
| P2-R017 | 整数最大余数，小数精度明确 | BigDecimal distribution | 守恒、非终止除法、溢出阻断 | IMPLEMENTED |
| P2-R018 | 页面固定来源整机/末级需求物料两列 | daily grid rowKey | 同末级不同整机行隔离 | IMPLEMENTED |
| P2-R019 | 页面只展示最终日期，不展示来源/实际周 | daily/weekly/monthly query | 84日视图和业务 W1-Wn 聚合 | IMPLEMENTED |
| P2-R020 | 人工只做守恒重分配 | daily-reallocations | 跨周原因、409、恢复自动 | IMPLEMENTED |
| P2-R021 | run 分块写入，active 指针短事务切换 | mrp_calc_run + Writer | 中途失败无READY半版本 | IMPLEMENTED |
| P2-R022 | 发布门禁、已执行日保护、修订版本和 Phase1 只读兼容 | publish/revision/execution facade | 阻断/锁定日/修订/权限/旧API/空库升级证据 | IMPLEMENTED |
| C025-补充 | 整机排产与物料缺料分岗；共享 active run，分配变更后物料确认失效，发布须双岗确认 | `/planning`、`/shortage`、MaterialAnalysisStageService、MrpWorkbenchReviewService、MaterialTaskLeaseRecoveryService | MySQL 分配与事实替换 1/1、过期租约独立事务 2/2；Testcontainers MySQL + MockMvc + 真实引擎/双 Worker 的 v5 双岗发布闭环 1/1，后端全量 647/647；5180 历史业务页浏览器 8/8；专用 v5 浏览器写入演练及整体阶段复核待完成 | IMPLEMENTED（专项门禁 REVIEW） |

主矩阵 ID 与 `assets/MRP业务规则库.md` 的 P2-R001..P2-R022 一一对应；C025 为新确认的补充追踪行。页面截图不能替代算法测试，单元测试不能替代真实 MySQL/API/浏览器联调。

2026-10-14 P2-R014A 日产能补充范围已验证：304后端测试、41前端测试、隔离MySQL/API和真实浏览器；证据见 `reports/2026-10-14-daily-capacity-report.md`。此补充验收不覆盖既有工厂数据域隔离债务，不改变整体阶段状态。

2026-10-19 实际计算进度补充范围 REVIEW：对应阶段16/17/18，修复大载荷任务排序导致进度查询500，新增独立提交且节流的真实阶段/完成数进度；真实浏览器38,850/219,450日事实计算、事务可见性、过期写入保护、32KB排序缓冲及修复前后四表精确等价已验证。详见 `reports/2026-10-19-actual-calculation-progress-report.md`。不改变业务公式或整体阶段状态，等待独立复核。
