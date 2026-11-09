# 排产页面运行入口修复报告

## 状态
DONE

## 本次完成

- 验收真实入口 5180 → 8080，定位旧后端进程、静态资源未同步、旧规则版本被分流到旧组件三个原因。
- 日、周、月统一使用新版 RollingPlanningGrid；旧规则计划只读展示，避免误用整机联动调整。
- 历史 TERMINAL 口径不返回/展示整机产能负荷，避免将末级数量认作整机台数。
- 同步构建资源到 Spring 静态目录，优雅重启实际 8080，实际页面引用 index-C99ciRMX.js。

## 修改文件

- frontend/src/views/mrp/components/{DailyPlanningGrid,WeeklySummary,MonthlySummary,RollingPlanningGrid}.vue
- frontend/src/views/mrp/__tests__/{RollingPlanningGrid,PlanningSummary}.test.ts
- backend/src/main/java/com/mrp/mrp/service/RollingPlanningQueryService.java
- backend/src/main/resources/static/（同步 frontend/dist 构建产物）

## 业务不变量

- 已发布 #86 / v2 / activeRun 32 不自动重算；修改前后批次 data 相同。
- 日事实 302 行、系统数量合计 6532，修改前后事实 TSV 完全相同。
- 新规则三份滚动及整机联动需新建 v4 排产，旧计划不伪造整机产出。

## 验证命令与结果

```text
backend: ./mvnw compile -q               PASS
backend: ./mvnw test -q                  PASS, 355 tests
frontend: npm run build                 PASS
frontend: npm test                      PASS, 61 tests / 10 files
GET /api/v1/mrp/batches/current          200, #86 / v2 / PUBLISHED
GET /api/v1/mrp/batches/86/rolling-grid   200, 189 days / 27 weeks / 5 rows
GET /api/v1/mrp/factories                200, 爱培科 / 永惠
Browser localhost:5180/planning         PASS, 日/周/月新版布局
Browser localhost:8080/planning         PASS, 日/周/月新版布局
```

日期范围 2026-07-27 至 2027-01-31，无 28 天截断；周按冻结业务周展开，月按实际生产自然月展开。

证据：test-evidence/planning-runtime-20261015/（六张浏览器截图、接口 JSON、前后事实、构建测试日志）。独立复核 PASS（仅本报告范围），见同目录 independent-review.md。

## 未完成与风险

- 本报告完成范围为运行入口与旧版本新版布局兼容。整体滚动改版仍保持 REVIEW，真实 v4 HTTP 创建与联动调整完整 UAT 尚待业务主数据验收。
- 当前历史 #86 仅含永惠数据；刷新布局不会补造爱培科历史排产。
- 旧规则多工厂批次若工厂分散于不同分页，工厂下拉选项可能仅含当前页工厂；不影响当前单厂 #86，v4 使用全厂负荷提供选项。
- 前端测试有既有 legacy 组件未声明 change 事件的 Vue 警告；测试无失败。

## 决策/待确认

无新增业务决策。

## 下一步

用户刷新排产页即可检查新版；新规则排产通过“生成排产”创建。
