# 整机负荷日格溢出修复

## 状态
REVIEW。真实浏览器布局复测和后端全量回归通过；独立阶段复核待完成。

## 本次完成
- 核对 5180 Vite 代理指向 8180；8180 后端此前已重启。用户截图对应隔离库 #73/run #67，为已发布 v4 历史批次，不把它的来源月分行误判为 v5 合行未生效。
- 在真实 Chromium 复现：首个负荷按钮展示 `已排 11,255 台 / 日产能 10 台 / 超出 11,245 台`，旧内容高度 64px、可视高度 59px，且日期列只有 104px。
- 扩大日列至 128px、表格行高至 72px；同步修正日期列虚拟滚动、月份定位和今天定位的宽度计算。Element Plus 按钮内部按指标纵向排列，长值限定在单元格内并保留悬停标题。
- 重新启动 5180，维持代理目标 8180；未修改后端业务代码、迁移或历史批次。
- 新增 Playwright 几何回归，检查负荷格三行文字的左右/上下边界和横向 scrollWidth，不再只断言页面出现。

## 修改文件
- `frontend/src/views/mrp/components/RollingPlanningGrid.vue`
- `frontend/src/views/mrp/__tests__/RollingPlanningGrid.test.ts`
- `frontend/e2e/two-workbench-compact.spec.ts`
- `.harness/issue-log.md`、本报告

## 业务不变量
- 原始负荷、冻结日产能及超出量的计算与发布事实不变；v4 历史来源月行保持兼容，v5 合行规则不变。
- 日期列虚拟宽度与滚动定位使用相同宽度，整机负荷仍可打开来源明细。

## 验证命令与结果
```text
frontend: npm run build => 0
frontend: npm test -- --run => 104/104 PASS
frontend: npx playwright test e2e/phase2-mrp.spec.ts e2e/two-workbench-compact.spec.ts --project=chromium --workers=1 --reporter=line => 9/9 PASS（5180 重启后，含新几何断言）
frontend: npx playwright test e2e/two-workbench-compact.spec.ts --project=chromium --workers=1 --reporter=line => 2/2 PASS（2026-10-30 再次使用运行中的 5180 复测）
5180: /planning/73 => HTTP 200；代理 /api/v1/mrp/batches/73 与 rolling-grid => HTTP 200，ruleVersion=v4-rolling-business-weeks、activeRunId=67、368 条容量日数据
Chromium: 1440x900 与 1920x1080，各检查 12 个可见整机负荷格的子文本包围盒和 scrollWidth；0 个水平/垂直溢出，行高 72px
backend: ./mvnw -q test => 退出码 0；Surefire 93 套、647/647 通过，失败/错误/跳过均为 0
service: 5180 /planning/73 与 8180 /actuator/health 均 HTTP 200；git diff --check 退出码 0
```

## 未完成与风险
- 当前浏览器业务样例是历史 v4；v5 专用批次浏览器写入验收仍属于双工作台总任务 REVIEW 范围。
- 极端超长数量会以省略号留在格内，完整内容需通过悬停标题或明细查看。

## 下一步
- 保持总任务 REVIEW，等待独立阶段复核；新建 v5 批次后继续验证合行和完整交接流程。
