# C022 业务周均分与整体前滚独立代码复核

## 结论

PASS（代码复核）。未发现本次变更引入的阻断缺陷。此结论只覆盖以下实际代码链路及测试设计；编译、全量测试、服务重启、真实 API 与浏览器验收由实施报告记录，全部通过前不得将任务标记 DONE。

## 复核依据与边界

已读取 project.md、execution-laws、Harness README、15/16/17 prompts、task-state、C022 决策与最新计算契约，并对照滚动排产原型及前端规范。本次按照用户确认的“来源月实际 N 个业务周均分，余数归第一份，整体提前一个业务周”复核，历史固定三份原型只作为 v4 布局与历史规则依据。没有把工作区大量已有修改计入本次变更；没有修改业务代码、数据库、迁移或测试。

## 实际链路复核

- MrpBatchController.create → ConfigSnapshotService.rollingBusinessMonths/snapshot：新建批次设置 ProductionCapacityRules.VERSION（v5），冻结来源月及其上月业务周与工厂生产日历；后续分配不读取当前业务周主数据。
- DailyPlanningService.distribute → MrpCalcBatchMapper.selectBusinessWeeksByBatchAndRange：SQL 按 batch scope 连接 business_week_detail，以 scope 的 business_month 恢复所属月；服务按工厂隔离调用分配引擎，并仅为 v5 传入 shiftedBusinessWeeks=true。
- RollingBusinessWeekDistributionEngine：v5 使用当前来源月冻结周条数 N；base 按整数或 6 位精度向下取整，第一份为 Q-base×(N−1)。目标依次为上月最后一周和本月前 N−1 周。当前月全部周先做合法性检查，避免无效末周仍参与分母。目标周按本厂生产日均分，日余数按日期从早到晚分配；无额外日期减 7。
- 日事实保留 sourceMonth，sourceDate=scheduleDate；周汇总来自日事实和冻结业务周；v5 月汇总经统一 rolling 判定按实际生产自然月。人工 override 的虚拟日事实 SQL 亦通过 rolling 参数保留相同日期语义。
- ProductionCapacityRules 与 RollingPlanningQueryService 的 rolling 判定同时包含 v4/v5；wholeMachine/dailyCapacity 继续识别两版本，联动调整、守恒、周/月查询、产能负荷沿用正确分支。全量扫描主代码与前端未发现残留 v4-only 的业务版本门禁。
- v4 使用原五参数构造器默认 false，仍固定三份和 W1/W2；发布复制规则版本，修订沿用来源规则版本。新规则不会自动改写历史批次。
- MrpWorkspace、DailyPlanningGrid、RollingPlanningGrid 均允许 v5 使用日联动调整，v3/v2 等旧版本的只读提示保持识别。

## 测试设计复核

新增引擎用例覆盖四周月、五周月、连续月份承接、末月末周空白、全部周余数归第一份、生产日余数、跨年、乱序输入、整数小量、小数六位守恒、缺来源月/上月、停产周、日历缺失及非目标末周非法。原 v4 用例保留。新增前端参数化用例同时验证 v4/v5 可编辑且无旧版本提示。

服务实际路由仍需真实 v5 HTTP 分配验收证明，不能仅凭引擎 boolean 单测推断已联通；实施者正在执行该项及完整三层验证，本 Reviewer 不重复运行以避免干扰。

## 业务不变量

BigDecimal 数量和来源月可追溯；合法目标的日数量与待分配数量之和等于来源需求；工厂隔离；生产日历缺失/整周停产不自动跨周补排；冻结输入与历史规则版本不被改写。
