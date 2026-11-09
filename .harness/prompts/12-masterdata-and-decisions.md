# 阶段 12：主数据、生产日历与决策框架

## 目标

实现分类角色、物料 MRP 属性、生产日历、工厂仓库映射、库存控制策略和业务周期间的版本化后端，以及批次预检 issue/decision 状态框架。

## 必读

v4.0 第 4/8/9 节、`api-contract.md`、`security-and-ops.md`、`list-rules.md`。

## 执行

1. 实现角色枚举、供应方式、EXPLODE/STOP，禁止按物料名称猜测。
2. 实现配置草稿、批量明细、发布、历史和 checksum；发布后不可编辑。
3. 实现生产日历按工厂/日期维护、批量区间操作和版本发布。
4. 实现工厂仓库一对一映射版本、仓库+物料安全/预警库存版本和按月 W1-Wn 日期范围的业务周版本；发布时校验唯一性、日期重叠/空档和 `0<=safety<=warning`。
5. 冻结并建立完整批次状态枚举与迁移：`DRAFT -> CHECKING -> ANALYZING -> WAITING_DECISION -> REANALYZING -> READY_TO_CALCULATE -> CALCULATING -> WRITING -> READY -> PUBLISHED`，并支持 `STALE/FAILED/CANCELLED`。
6. 实现稳定 issue_key、单条/批量 decision、expectedRevision、审计和工厂数据域。
7. 缺角色、日历覆盖、仓库映射、库存控制策略或业务周覆盖必须形成明确阻断 issue。物料供应/下钻策略缺失按 2026-10-14 用户确认例外处理：不阻断，缺下钻策略默认继续至叶子，完成后保存并展示缺失清单；有效 STOP 独立生效。新增/修改/导入必须选择真实有效物料，供应仅 SELF_MADE/PURCHASED/OUTSOURCED，下钻仅 EXPLODE/STOP。

## 门禁

- 单元/集成测试覆盖发布不可变、缺配置、幂等 issue、409 和 STALE；
- 权限码、后端鉴权、审计存在；
- API 实际启动后 curl 验证成功、空、400、401、403、409；
- 不实现库存分配算法或排产。

完成后 REVIEW，独立 Reviewer PASS 后进入 13。
