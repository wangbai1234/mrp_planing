# 排产发布、卡顿与导出核验报告

## 状态

REVIEW

## 本次完成

- 发布确认前重新读取批次状态；批次已从 READY 变为 SUPERSEDED 时刷新工作台，不再继续提交发布。成功发布会复制出 PUBLISHED 批次并将原工作批次标为 SUPERSEDED。
- 当前 v4 工作台日表按服务端物料行分页读取，计算追溯、库存池、库存风险、待处理异常和日单元格明细均使用服务端分页；计算进度使用工作台状态栏中的紧凑进度条。
- 排产导出改为只遍历一次所有分页，并将类型和供应方式映射为中文。仅导出真实排产事实，不输出页面合成的“整机负荷”汇总行。
- 对照用户提供的“2026年8月生产计划”工作表，采用固定信息列、合计列和连续日期列的结构。MRP 当前没有机型版本、PO、历史已领料及翻新字段，不生成虚构值。
- 业务周 W3/W4 无量符合已冻结 v4 规则：净需求只分配到上月最后业务周、本月 W1 和 W2；有第五周时上月末份落在 W5。

## 修改文件

- `backend/src/main/java/com/mrp/mrp/service/MrpDailyPlanExportService.java`
- `backend/src/test/java/com/mrp/mrp/service/MrpDailyPlanExportServiceTest.java`

发布前刷新、紧凑进度条及各侧栏分页实现已在本轮从当前代码链路核验；它们由本轮前的工作修改，本轮未再改写。

## 业务不变量

- 发布仅允许 READY 批次；SUPERSEDED 是成功发布后原工作批次的终态，重复发布不得改变已发布事实。
- 页面整机负荷是全厂预警汇总，不是物料排产行，不进入导出；实际整机/物料排产数量保持原值。
- 导出行合计等于该行全部连续日期列的数量之和。

## 验证命令与结果

```text
cd backend && ./mvnw -q -Dmaven.compiler.useIncrementalCompilation=false compile
通过

cd backend && ./mvnw -q -Dmaven.compiler.useIncrementalCompilation=false -Dtest=MrpDailyPlanExportServiceTest test
通过

cd backend && ./mvnw -q -Dmaven.compiler.useIncrementalCompilation=false test
通过：73 个测试报告，541 项，0 failures、0 errors、0 skipped

curl http://localhost:8080/actuator/health
HTTP 200，status=UP

浏览器工作台 /planning/93 导出 + XLSX 结构校验
1,577 条数据，184 个日期列，日期范围 2026-10-29 至 2027-04-30；逐行合计与日数量和完全相等；内部枚举 0；“整机负荷”标签 0。
```

浏览器下载事件监听器等待超时，但 Chrome 已生成下载文件 `/Users/wanglixun/Downloads/排产计划_93 (1).xlsx`，并对该文件完成结构及数量校验；页面控制台无错误，页面仍可正常显示。

## 未完成与风险

- 旧版兼容 API `/api/v1/mrp/plans/{id}/daily-grid` 仍会先整批加载日计划，再执行过滤和分页。当前 v4 工作台调用 `/rolling-grid`，不经过该旧路径；大量旧批次仍可能有较高内存和响应耗时。
- 参考工作簿中的机型、版本、PO、历史已领料和翻新字段当前不在 MRP 排产行数据中，导出不填充这些列。
- 前端构建、单测和类型检查在本轮前的交接摘要中记录为通过；本轮未重复运行前端套件。真实浏览器已验证当前工作台页面和导出结果。

## 决策/待确认

- 无。若要求 Excel 必须包含参考表中所有历史补充字段，需先确定这些字段的数据源与排产行的关联规则。

## 下一步

- 独立复核后关闭本任务；如仍需支持旧批次的大规模日表查询，再单独优化兼容 API 的 SQL 分页。
