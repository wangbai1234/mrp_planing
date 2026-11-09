# 阶段 10: 契约迁移与真实代码基线 - 任务简报

**阶段**: 10
**名称**: contract-migration
**状态**: IN_PROGRESS
**开始时间**: 2026-09-22
**Coordinator**: Mimo (mimo-v2.5-pro)

---

## 目标

冻结 Phase 2 的需求、数据、算法、API、页面和兼容策略，确认真实代码差距。此阶段原则上不写业务实现。

---

## 执行计划

### 1. 工作区基线记录

- [x] `git status --short` 记录现有改动
- [x] Flyway 最高版本: V020
- [x] Java 版本: OpenJDK 21.0.12.1
- [x] Node 版本: v23.11.0（.nvmrc 要求 Node 20，P2-I001）
- [x] Docker: 29.4.3
- [x] MySQL: mrp-mysql (3307, healthy)
- [x] Redis: mrp-redis

### 2. 现有代码验证

- [ ] PlanningCalculator.java - 使用 long，月拆 W1-W3
- [ ] inventory_item 双路径现状
- [ ] BOM 展开逻辑现状
- [ ] 事务边界现状
- [ ] 前端数据流现状

### 3. 文件所有权表

- [ ] 阶段 11-19 文件所有权分配
- [ ] 共享契约锁定

### 4. 决策项检查

- [ ] Q2-012 库存映射 - 阶段 13 阻断
- [ ] Q2-013 BOM 版本 - 阶段 14 阻断
- [ ] Q2-014 单位换算 - 阶段 14 阻断
- [ ] Q2-015 已执行日 - 阶段 15 阻断
- [ ] Q2-016 发布后修改 - 阶段 16 阻断

### 5. 文档冲突检查

- [ ] v4.0 vs contracts vs decision-log vs workflow vs UAT

### 6. 输出物

- [ ] `.superpowers/sdd/2026-09-22-phase2-daily-mrp/progress.md`
- [ ] `.superpowers/sdd/2026-09-22-phase2-daily-mrp/task-10-report.md`
- [ ] `.harness/reports/10-contract-migration-report.md`

---

## 不变量

1. 不修改任何业务代码
2. 不修改 Flyway 迁移
3. 不修改前端代码
4. 不修改安全配置
5. 保护所有用户现有改动

---

## 验证方法

1. 确认文档无互相冲突
2. 确认 Phase 1 标为历史
3. 确认 Q2-012~016 标记为阻断决策
4. 确认文件所有权表完整
5. 编译验证: `cd backend && ./mvnw compile`
