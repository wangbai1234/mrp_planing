# Phase 1 历史契约索引

Phase 1 的 `plan_version`、`plan_detail`、`plan_override`、W1-W3/W4 carry、周产能门禁和旧接口只作为历史只读兼容依据，不得证明 Phase 2 已完成，也不得被改字段语义承载日排产。

本仓库原有 Phase 1 契约和证据保留在 Git 基线、`docs/MRP需求文档v3.0（Phase 1）.md` 以及 `.harness/reports/00-*` 至 `.harness/reports/09-*`。Phase 2 文档不复制一份可被误认为当前规则的旧内容；发生冲突时以 `.harness/source-precedence.md`、Phase 2 规则库和 v4.0 需求为准。

Phase 2 的唯一计划身份是 `mrp_calc_batch`，对外 `planId = batchId`。旧 Phase 1 计划仅允许历史查询，写入入口必须返回明确的兼容/停用状态，不得静默写入新日表。
