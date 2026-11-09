# 排产发布门禁与生产日历完整性

## 状态
REVIEW（代码、自测、全量回归、重启和真实页面核验已完成，等待独立阶段复核）

## 原因与依据
- 用户授权：2026-10-22「好的执行」。Prompt 16 第6项发布校验、Prompt17工作台、C017默认下钻、C018/C019冻结输入、C022目标业务周、C024本次裁决。
- 原链路：发布任务提交只验证批次状态；正式发布检查OPEN问题、PENDING待分配及守恒/产能，缺少策略确认、库存风险及没有排产事实的日期完整性门禁。产能预警允许确认，因此无法代替缺日历硬校验。
- 发布原来未复制库存风险；旧transition可直接写PUBLISHED，绕过完整发布链路。

## 本次完成
- 新增按相关工厂、正数经营计划来源月及规则所需日期检查的服务：v5检查上月最后周及本月除末周，v4检查上月最后周及本月W1/W2，旧规则覆盖来源月与提前7日范围。明确配置为休息日有效，未配置单独报错。
- 修订前按当前日历预检，可警告后继续生成草稿；冻结日历缺失计入异常红点，禁止发布。补齐主数据后需重新生成草稿采用完整输入，不原地覆盖冻结输入。
- 前端发布按钮与确认函数拦截所有未处理红点；校验加载失败时禁止发布。新增日历检查菜单、日期/工厂明细及维护入口。
- 发布提交前及执行锁内双重检查：待处理异常、待分配需求、默认下钻待确认、库存风险、日历/业务周配置。一般产能预警仍可明确确认，缺日历不能用产能确认绕过。
- 发布复制库存风险及处理人/时间/原因，禁止transition直接进入PUBLISHED。

## 修改文件
- backend/src/main/java/com/mrp/mrp/service/PlanningCalendarValidationService.java
- backend/src/main/java/com/mrp/mrp/service/PlanningReleaseValidationService.java
- backend/src/main/java/com/mrp/mrp/service/{BatchOrchestratorService,MrpLifecycleTaskService,MaterialPolicyNoticeService}.java
- backend/src/main/java/com/mrp/mrp/controller/MrpBatchLifecycleController.java
- backend/src/main/java/com/mrp/mrp/repository/MrpStockRiskMapper.java
- backend/src/main/resources/mapper/mrp/MrpStockRiskMapper.xml
- frontend/src/api/mrp.ts、frontend/src/views/mrp/MrpWorkspace.vue
- 对应单元测试、新增PlanningReleaseIntegrationTest、RollingPlanningIntegrationTest发布测试日历fixture。

## 业务不变量
- 不修改物料策略主数据，不凭空填写供应方式或下钻策略；保留仅本批次默认继续下钻和库存阈值为0的确认入口。
- 已发布排产使用冻结输入，当前日历事后删除不改写历史事实；只检查本批次相关工厂，不因另一工厂配置缺失错误阻断。
- 日事实、来源月守恒、产能指纹、发布事务回滚和历史导航行为保留；无迁移、依赖或技术栈版本变更。

## 验证命令与结果
- backend ./mvnw compile：PASS，/tmp/mrp-release-compile.log。
- frontend npm run build：PASS，/tmp/mrp-release-frontend-build.log；仅既有包体积提示。
- frontend npm test：12文件99测试全部通过，/tmp/mrp-release-frontend-test.log。
- 后端首次全量594测试有3个旧发布fixture失败：仅两日日历被新门禁提前拒绝。补全仅发布用例的日历，23个RollingPlanningIntegrationTest全部通过；保留原守恒、并发和千行事务回滚断言。/tmp/mrp-release-targeted-test.log。
- 新增HTTP集成3测试全部通过，/tmp/mrp-release-http-test.log：缺2026-10-01返回400并给出工厂日期；明确休息日通过；策略/库存待处理返回400且无任务创建；处理后门禁通过；工厂范围403；库存风险SQL复制数量与处理元数据一致。
- 最新全量 backend ./mvnw test：598测试全部通过，Failures 0、Errors 0、Skipped 0，BUILD SUCCESS，耗时10分44秒；/tmp/mrp-release-backend-test-final.log。
- 前后端已重启至最新代码：后端8080 /tmp/mrp-release-backend-runtime.log，前端5180 /tmp/mrp-release-frontend-runtime.log。
- 真实HTTP：当前115（永惠）冻结和当前日历检查total=0，历史111检查total=0，capacity-review返回187条日期负荷。修复前后currentBatch及summary完整一致，115/run67、3288条日事实、checksum一致。
- 真实浏览器：刷新planning，日历检查显示完整；修订前确认弹窗打开并取消；历史111菜单分别显示物料策略24、库存风险25，已处理缺库存记录不计红点；已返回当前115。
- 证据：test-evidence/2026-10-22/planning-release-runtime-{before,after}.json、planning-release-runtime-preservation.txt、planning-calendar-check.png、planning-exception-menu-badges.png。

## 未完成与风险
- 当前状态REVIEW，仍需独立阶段复核，不宣称DONE或阶段18/19总体通过。
- 既有历史发布版本遗漏的库存风险不追溯回填；新发布保留完整记录。本次未重算/修订/发布用户当前115。

## 决策与下一步
- 无待用户确认的业务选择。全量测试和实际联调已通过，交独立复核。
