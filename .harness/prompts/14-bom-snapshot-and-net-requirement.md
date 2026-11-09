# 阶段 14：BOM 快照与多层净需求

## 目标

历史阶段目标为不可变 BOM 快照和纯 Java 多层净需求引擎。新批次 BOM 输入已由 2026-10-20 C023 改为当前同步数据；本文件原快照步骤仅保留为历史实现说明。

## 必读

`calculation-contract.md`、开发手册第 4-6 节、现有 BOM 同步/查询代码和 BOM 数据量报告。

## 执行

1. NC 同步按库存组织+父料号+子料号选择最高有效版本；快照按 asOfDate 和组织/body scope 固化同步结果。校验重复最高版本、有效期重叠、库存组织不明确、缺物料、循环、非法数量并计算稳定 checksum。
2. 实现纯 Java Engine，输入 DTO 一次性加载；Mapper 不得出现在递归/遍历中。
3. 使用 `childQty / parentQty`，每层扣当前节点已锁定库存，只传递净需求。
4. 实现 STOP/EXPLODE、结构叶子、逐层解释明细、末级需求聚合和稳定排序。
5. 分析波次按来源月+经营计划 `priority_seq` 生成稳定 `AUTO_PRIORITY` 共享库存分配；多路径先汇总需求后按比例回分。缺决策或人工覆盖时进入 WAITING_DECISION，不使用 Java/SQL/前端的隐式顺序兜底。
6. 使用 BigDecimal，禁止 longValue 截断；MVP 不做单位换算，按现有数字直接计算。

## 门禁

- A/B/C/D 黄金结果 C1500/D500；
- parentQty非1、小数、循环、0/负、最大深度/节点、无BOM；
- 外购/委外 STOP 和 EXPLODE，根/中间 EXCLUDED 行为；
- 多路径、共享库存、逐层守恒、稳定 checksum；
- compile/test + MySQL 集成 + 实际 API 查询追溯。

完成后 REVIEW，算法 Reviewer 独立重算黄金数据，PASS 后进入 15。
