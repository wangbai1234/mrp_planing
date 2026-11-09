# 阶段 11：Phase 2 Schema 与持久化骨架

## 目标

只新增 Phase 2 表、索引、约束、实体和 MyBatis XML 骨架，不实现算法或页面。

## 必读

`data-contract.md`、`architecture.md`、开发手册第 5/9 节、`assets/db-design-rules.md`、现有全部迁移和 Mapper。

## 执行

1. 重新扫描最高迁移号；V001 到执行时最高已存在版本均不可修改。
2. 单一 Schema Agent 独占新迁移序号，创建配置版本、BOM 快照、快照仓库范围、工厂仓库映射、安全/预警库存、业务周期间、DECIMAL 库存余额、批次工厂范围、issue、库存池/分配、逐层需求、日计划、覆盖、已执行/锁定事实、待分配和异常表。
3. 经营计划明细增加 `source_row_no/priority_seq`。库存原始行和规范化余额增加 `inventory_quality`；库存按快照+仓库+物料+品质唯一，不跨仓合并。
4. 数量使用 DECIMAL(18,6)，建立唯一键、revision、状态字段、审计字段和查询索引。
5. 新增 Entity/DTO/Mapper 接口与 XML；禁止 N+1 设计。
6. 保留旧周表和 API 结构，不做破坏性回填/清理。

## 测试与门禁

- 后端 compile/test；
- MySQL 空库 V001->最新；
- 从 V020 真实结构升级；
- 重启重复迁移；
- 核对旧迁移 checksum 未变；
- 实际启动 API 并验证 Flyway/health。

完成后置 REVIEW；Schema Reviewer 检查约束、索引、兼容和 rollback 风险，PASS 后进入 12。
