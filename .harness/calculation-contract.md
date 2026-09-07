# MRP 排产计算契约

本文件优先级高于示例代码。业务规则冲突时暂停实现并记录决策，不自行“优化”规则。

## 1. 输入

对每个整机料号、工厂和来源月份读取：

```text
forecast
inventory
shipped
```

全部来自同一组输入版本。产能来自同一 `capacity_version`。

## 2. 月可排量

```text
available = MAX(0, forecast - inventory - shipped)
```

库存按工厂分开汇总；同一工厂有效仓库汇总；不良品仓不计入。计算结果必须非负，数据库和服务层都要校验。

## 3. 月份拆分和提前量

依据当前项目已确认的滚动排产契约：

```text
carry = ceil(available / 3)
rest = available - carry
base = floor(rest / 3)
remainder = rest % 3
ordinary = [base + (remainder >= 1 ? 1 : 0),
            base + (remainder >= 2 ? 1 : 0),
            base]
```

`carry` 放在上月物理第 4 周，来源月份仍是本月；`ordinary` 放在本月 W1、W2、W3；本月物理 W4 承接下一个来源月份的 `carry`。例如 1200 -> 400 + 267 + 267 + 266。

周记录必须同时保存：

```text
physical_month  周所在月份
source_month    数量消耗的 Forecast 月份
slot            0=提前量, 1..3=本月普通周
is_carry        是否提前量
```

来源月份合计必须按 `source_month` 计算，不能按物理月份简单求和。

## 4. 12 周窗口

- 当前周是运行日期所在周的周一；
- 从当前周起输出固定连续 12 周；
- 前端横向查看，不提供“滚动一周”按钮；
- “定位当前周”只改变滚动位置，不改变数据、窗口、版本或状态；
- 当前周之前的已执行周在月中重算中锁定。

## 5. 月中重算

月中 Forecast 变化时：

1. 读取旧计划的执行锁定范围；
2. 已执行数量保留；
3. 新的月可排量减去已执行数量；
4. 只在未执行周重新拆分；
5. 人工覆盖值按规则保留；
6. 若守恒或产能校验失败，版本不能进入 `READY`，必须返回明确差异。

## 6. 人工覆盖和重算

- `system_quantity`：本次输入计算出的自动值；
- `manual_quantity`：计划员覆盖值，可为空；
- `effective_quantity`：当前生效值，优先人工值；
- 人工编辑不生成版本号，只写 override 和审计；
- 重算生成新 `plan_version`，保留允许保留的人工覆盖；
- 恢复自动值只删除指定周覆盖，不影响其他周。

## 7. 产能

产能配置和校验统一使用 `weekly_capacity`。真实日排产 Excel 只参考布局和字段，不做日能力到周能力换算。超限只产生可解释的异常和人工调整入口；系统不能自动替计划员搬移数量。

## 8. 可重复性和校验

排序必须稳定：工厂编码、料号、周开始日期固定排序。计算结果要校验：

```text
sum(source_month quantities) == available
every quantity >= 0
every week_start_date is Monday
same input_checksum + rule_version => same result_checksum
```

所有规则示例必须有黄金数据测试，禁止只验证页面文本。
