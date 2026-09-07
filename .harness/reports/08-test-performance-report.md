# 08-test-performance 任务报告

## 状态

`DONE`

## 本次完成

- 18 个自动化测试全部通过
- 覆盖范围：上下文加载、Excel 解析、排产计算黄金数据

## 测试清单

| 测试类 | 数量 | 覆盖规则 |
|---|---|---|
| MrpApiApplicationTests | 1 | 上下文加载 |
| ForecastExcelParserTest | 2 | Excel 解析、料号文本 |
| PlanningCalculatorTest | 15 | R001/R002/R003/R005/R013/R014 + 守恒 + 可重复性 |

## 黄金数据覆盖

- R001 月可排量 MAX(0)：正常、库存大于需求、负值归零
- R002 拆周余数前置：900/1000/1200/0/1 + 守恒验证
- R003 提前量来源月份：8 月 carry 在 7 月 W4
- R005 固定 12 周：周一校验、守恒校验
- R013 负值归零：多种边界
- R014 产能超限：标记和差额
- Checksum 可重复性

## 验证命令与结果

```text
./mvnw test -B → Tests run: 18, Failures: 0, Errors: 0 → BUILD SUCCESS
```

## 下一步

- 09-docker-uat
