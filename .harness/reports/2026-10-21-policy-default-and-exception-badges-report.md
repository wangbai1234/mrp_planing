# 本次默认下钻确认与异常红点报告

## 状态
REVIEW；已完成实现和三层验证，待独立阶段复核。批次 #106 的日历待分配仍需业务处理。

## 本次完成
- 缺下钻策略按现有计算规则继续下钻到结构叶子；新增本次计算 run 的批量确认记录、审计与待确认数量。确认不写物料策略，不推断供应方式，不改变已算结果或有效 STOP。
- 工作台顶部异常数按侧窗待处理异常、待分配需求、物料策略提示、库存风险四项求和。已确认的策略提示从红点消失，历史提示仍可查询。
- 待分配需求改为 active run 的服务端分页，仅展示 PENDING；支持需求标识或原因代码搜索。保留旧全量接口以兼容已有调用。
- 管理员已在批次 #106、run #58 对 24 条默认下钻提示执行本次批量确认；待确认数由 24 归零，未给对应物料新增主策略。

## 修改文件
- 后端：V053 新迁移、MaterialPolicyNoticeService/Controller/Mapper、DailyPlanningService/Controller、MrpUnallocatedQuantityMapper 及 XML、相关领域模型与安全配置。
- 前端：MrpWorkspace.vue、MaterialPolicyNoticePanel.vue、PendingUnallocatedPanel.vue、mrp.ts 及相应测试。
- 测试：DailyPlanningServiceTest、MaterialPolicyNoticeServiceTest、PlanningFactoryAccessIntegrationTest、MrpWorkspaceProgress.test.ts、mrp-query.test.ts。

## 业务不变量
- 默认下钻仅为缺策略时该次计算的行为；确认只更新提示的确认人和时间，不创建物料策略版本。
- 所有提示、待分配和库存风险只读取被选中批次的 active run；已处理项不再贡献待处理红点。
- 日历待分配仍单独阻断发布，不因策略提示确认而消除。

## 验证命令与结果
```text
cd backend && ./mvnw compile -q                 PASS
cd backend && ./mvnw test -q                    PASS；测试库迁移至 V053
cd frontend && npm run build                    PASS
cd frontend && npm test -- --run                PASS；12 组、85 项
重启本地 8080/5180                            PASS；Tomcat、Vite 均监听
管理员真实 API：#106 pending-default-count      0
管理员真实 API：#106 unallocated/page           total=24；第 1 页 20 条、第 2 页 4 条
管理员真实 API：#106 stock-risks/page            total=25
Playwright 浏览器：侧窗待分配 24、库存风险 25；顶部异常 49；分页 20/24；无页面异常
```

## 未完成与风险
- 批次 #106 仍为 WAITING_DECISION，openIssues=0、pendingUnallocated=24；该 24 条为 CALENDAR_INCOMPLETE，与 24 条已确认策略提示不是同一问题。未擅自修改生产日历或排产结果。
- /actuator/health 的外部 Oracle 检查存在既有慢/失败；本次相关业务 API 及页面验证成功。
- 独立阶段复核尚未完成，本报告保持 REVIEW。

## 决策/待确认
- 无新的策略业务决策。若要使批次进入可发布状态，需处理对应生产日历覆盖并重新计算。

## 下一步
- 对 24 条日历待分配逐项核对来源月份和目标日，修复日历覆盖后重新计算，再验证守恒与发布门禁。

## 2026-10-21 库存风险与状态刷新补充

### 本次补充
- 库存风险页面新增“本次排产安全库存与预警线设为 0”操作，按批次和 active run 处理当前风险并记录审计；不会修改库存策略或库存快照。
- 风险处理后刷新本页数据和工作台菜单计数；库存风险计数及子菜单红点以当前 run 未处理风险为准。
- 已处理的缺库存决策仍可通过状态筛选查看；默认列表目前保留“全部”视图，待处理红点只统计 OPEN。
- 待分配数量、策略提示和库存风险均按当前批次/active run 计算；修订并重新计算生成新结果后，旧结果仍作为历史数据保留。

### 验证命令与结果
```text
cd frontend && npm run build                 PASS
cd frontend && npm test -- --run             PASS；12 组、85 项
cd backend && ./mvnw test                    PASS；583 项，0 失败，Flyway 验证 54 个迁移
git diff --check                             PASS
重启本地后端与前端                          PASS；后端 profile=local、端口 8080，前端端口 5180
Flyway 本地数据库版本                       054
浏览器访问 http://localhost:5180/planning   PASS；planning 页面及异常侧栏加载，显示批次数据
```

### 未完成与限制
- `/actuator/health` 聚合检查因 Oracle 数据源连接失败而超时；日志为 `ORA-17800`。浏览器已加载 planning 页面和业务数据，但 health 聚合检查未通过，不能据此报告该检查正常。
- 当前批次仍有 24 条 CALENDAR_INCOMPLETE 待分配需求；按业务语义需要修订生产日历并重新计算，不通过过滤来隐藏。
- 库存风险按钮已提供操作入口；本次没有在用户的当前排产上代为提交该业务操作。
- 阶段独立 Reviewer 尚未执行，状态保持 REVIEW。
