# 任务报告：取消修订版后返回当前排产

## 状态
REVIEW（实现与自测完成，未宣称独立阶段复核 PASS）

## 代码验收与根因
- `PlanningAccessMapper.xml:38` 的当前批次查询排除 CANCELLED；取消 #110 后，当前批次为 PUBLISHED #109。
- #109 的旧修订任务 #182 仍为 SUCCEEDED，结果指向 #110。原工作台把历史任务成功直接用于自动跳转，造成返回当前后再次进入历史取消批次。
- `MrpLifecycleTaskService.submit` 原来无条件复用 SUCCEEDED 任务，导致放弃修订后再次生成也得到旧任务。
- 契约：阶段17执行3/10与原型第26/43行规定单一当前版本、历史只读、发布后通过独立修订修改。

## 本次完成
- 自动跳转只跟随本页面已提交或观察到等待中的同一任务；任务完成后重新查询服务端当前批次，确认结果仍为当前版本才跳转。
- 加载旧成功任务或主动查看历史时保持所选版本，返回当前不会重新打开取消修订版。
- 已取消且归属于当前来源批次的修订结果允许显式重新提交任务；PENDING/RUNNING和有效成功结果继续幂等复用。
- 增加取消返回、正常发布/修订完成（包括当前查询先于任务提交完成的时序）、历史主动查看、结果被提前取消、再次修订与重复提交回归。

## 修改文件
- frontend/src/views/mrp/MrpWorkspace.vue
- frontend/src/views/mrp/__tests__/MrpWorkspaceProgress.test.ts
- backend/src/main/java/com/mrp/mrp/service/MrpLifecycleTaskService.java
- backend/src/test/java/com/mrp/mrp/service/MrpLifecycleTaskServiceTest.java
- .harness/workflow.yaml

## 业务不变量
- 历史操作成功不代表结果批次仍是当前版本。
- 取消修订保留历史，已发布原版本继续有效；原排产事实和迁移文件不修改。
- 重复提交正在进行的任务不产生重复任务；发布与修订保持异步执行。

## 验证命令与结果
```text
cd frontend && npm run build
通过，既有 chunk size 提示。
cd frontend && npm test
最终通过：12 files / 90 tests。
cd backend && ./mvnw compile
最终通过：BUILD SUCCESS。
cd backend && ./mvnw test
首次修复验证全量通过：583 tests，0 failures/errors/skipped；耗时10:09。
（该全量执行早于随后补充的后端取消修订重提修复。）
cd backend && ./mvnw test -Dtest=MrpLifecycleTaskServiceTest,MrpLifecycleWorkerServiceTest
后端补充修复后通过：6 tests，0 failures/errors/skipped。
```

## 本地集成验证
- 重启后端8080和前端5180到最新业务代码；前后端均实际启动。
- 真实HTTP：current=109/PUBLISHED；110=CANCELLED且revisionOfBatchId=109；109修订任务182=SUCCEEDED/resultBatchId110；109 summary日事实3288，接口均200。
- Chrome：历史版本选择#110，看到已取消/历史只读；点击返回当前版本，URL为/planning，显示#109/PUBLISHED和当前排产，返回按钮消失。稍后再次读取页面，仍保持#109，日表共23条。
- 截图：test-evidence/2026-10-22/cancelled-revision-return-current.png。

## 未完成与风险
- 没有在用户现有库新增或作废排产；新任务提交与正常发布/修订自动跳转由自动化测试验证。
- 独立阶段复核未执行，任务保持REVIEW；本次不改变整体阶段18/19状态。

## 决策/待确认
无。

## 下一步
按项目流程进行独立复核。
