# 整机日产能口径更正报告

## 状态
DONE。代码、自测、运行联调和主服务回归完成；独立复核 PASS。

## 本次完成
- 生产日历口径更正为整机产量台/日；爱培科、永惠独立配置生产日及正整数日产能，停产为0。日历、批量维护和六列导入均校验；旧非台配置保留并提示重新维护。
- 新规则 v3-whole-machine-capacity 从根 level0 已扣整机库存的 netQty 建立独立 MACHINE_OUTPUT；整数日分配，不被末级库存、BOM倍数或末级供应方式改变。
- 整机与末级日/周/月视图隔离。全计划有效整机数量决定工厂当天负荷，分页/筛选不缩小负荷；超限标红，人工整数跨日转移后消警。
- 人工覆盖、两端revision、执行锁、发布指纹、独立发布及修订均覆盖整机事实；末级仍保持原精度，整机调整不自动移动备料。
- 工厂接口加载失败的根因是5180代理的8080旧进程未加载实现；已同步前端静态资源并以原环境重启主服务，配置页面实际恢复。
- 联调修复提前7天跨月周快照遗漏、部分重叠业务周漏尾段，以及非法整机调产数量误返回500。

## 修改文件
- 设计：docs/MRP生产日历与日产能改版设计方案.md、v4需求/开发手册、原型、C019、计算/API/数据契约、业务规则。
- 后端：ProductionCapacityRules、MrpCalculationService、DailyDistributionEngine、DailyPlanningService、DailyCapacityService、ConfigSnapshotService、BatchOrchestratorService、相关Controller/XML Mapper及GlobalExceptionHandler与回归测试。
- 前端：ProductionCalendarConfig、DailyPlanningGrid、WeeklySummary、MonthlySummary、API类型及组件测试。
- 验收证据：test-evidence/whole-machine-capacity-2026-10-14/，独立复核见independent-review.md。

## 业务不变量
- 日期仍sourceDate−7个自然日；整机与备料分别守恒，不修改已应用迁移或技术栈。
- 历史v2口径及冻结快照兼容，不把历史末级数据改名成整机。
- 新建冻结来源月及提前7天涉及的已维护月业务周；修订保留旧scope，仅补缺失快照，版本进入checksum。部分重叠周保留未覆盖尾段，不修改冻结定义。
- 不自动平衡能力；异常发布需绑定当前完整结果确认，结果变化使旧确认409。
- 所有验收夹具只写mrp_machine_capacity_uat_1014，主库只读回验证。

## 验证命令与结果
- backend ./mvnw compile：PASS；./mvnw test：330 tests，0失败/错误/跳过，43 suites。见backend-compile.log/backend-test.log。
- frontend npm run build：PASS；npm test：8 suites、48 tests PASS。见frontend-build.log/frontend-test.log。
- python3 verify-api.py：13项真实API PASS；1900经营量−100根库存=1800台，BOM2=3600件；部件全库存仍1800台、根库存全覆盖为0。整数分配、日期、完整负荷、调产消警、锁、400/409、历史兼容和快照冻结通过。
- 实际浏览器：58台/40台超18标红；末级不误标红；转18后40台满产、次日76台正常。周汇总408+232+406+406+348=1800台，月汇总1800台/3600件分别显示。截图与machine-normal-ax.txt已归档。
- python3 verify-publish-revision.py：工作26→独立发布27→修订28，发布冻结有效事实；修订新能力20台、锁定40台保留，真实重算及日/周守恒通过。见publish-revision.log/results。
- 主服务8080和代理5180：工厂/映射/日历200，未登录401；31天主日历与main-before.json完全一致，服务静态index与最终frontend/dist一致。两个主页面已用真实浏览器AX确认数据；main-calendar-loaded.png/main-mapping-loaded.png为窄视口截图，仅作加载证据，数据以AX/API读回为准。
- 独立calendar_review复核PASS，见independent-review.md。

## 失败现场与修复
保留api-initial/second/third/fourth/fifth失败日志：前三次揭示跨月快照遗漏，第四次非法数量500，第五次验收轮询把WAITING_DAILY_DISTRIBUTION瞬时态误判终态。真实缺陷修复并回归，脚本按worker真实状态链修正；未删除库或重置工作区掩盖失败。

## 未完成与风险
- 历史单位非台或缺失的能力必须人工按整机重新维护；旧批次保留原口径，新建/修订使用新口径。
- CAP-1014-03既有工厂数据域隔离债务仍OPEN，Phase2整体REVIEW不变；本任务PASS不等于Phase2总验收完成。
- 构建体积及Vue组件stub既有警告不阻断本次。

## 决策/待确认
C019已明确整机台/日，无待确认；日期模型未变。

## 下一步
本任务已归档DONE。业务按台/日维护原单位缺失配置后创建新计划。
