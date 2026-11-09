# 生产日历与日产能改版任务报告

日期：2026-10-14。

## 状态

DONE：实现、编译、测试、真实 API/浏览器验收与最终独立复核均通过。此补充任务不变更 Phase 2 整体阶段门禁。

## 本次完成

- 按用户确认的 `docs/MRP生产日历与日产能改版设计方案.md` 第 3–8 节实施：爱培科、永惠独立按月维护生产日、日产能及单位；支持单日修改、日期区间/星期批量预览、保留特例、覆盖、工厂间复制、列表分页和备注搜索。
- 当前周产能维护入口退出，`/capacity` 重定向生产日历；工厂来源不依赖旧产线，创建新批次不要求周产能配置。保留 Phase 1 历史表/API，未改写历史批次。
- 新规则 `v2-daily-capacity` 按来源月份有效生产日等量初排，取消权重配置；最终排产日期使用冻结日产能检查完整工厂日负荷。外购/委外排除，供应方式或单位缺失/不匹配明确标为未评估。
- 工厂日汇总与参与超限的非零自制单元格标红；跨日转移预览两端负荷，事务保存两端调整、审计和版本，不改变需求总量。支持向空日期转入以及再次转出。
- 锁定/执行冻结有效排产量，包括空目标的人工投影；解锁后再次调整再锁定不会沿用旧数量。发布复制独立有效事实，修订采用新日历且保留执行保护。
- 发布仍有产能异常时必须确认当前告警；排产变化后旧确认指纹失效。单日修订、批量指纹、转移运行/数量/版本及发布确认的并发冲突返回 HTTP 409。
- 日历导入模板改为工厂编码、日期、生产日、日产能、单位、备注六列；全文件预检失败时不部分写入，错误原因在末列。已有原始数据、审计和错误处理机制保留。

## 修改文件

本工作区包含大量任务前已有变更，下列为本任务主要涉及文件，不代表整个 `git status` 都属于本次改版。

- 前端：`ProductionCalendarConfig.vue`、`components/DailyPlanningGrid.vue`、`MrpWorkspace.vue`、`components/BatchCreateDialog.vue`、`FactoryWarehouseMappingConfig.vue`、`frontend/src/api/mrp.ts`、路由/菜单配置及对应组件测试；更新 `prototype/MRP日排产Phase2可交互原型.html`。
- 后端：`ConfigMasterService/Controller/Mapper`、`ConfigSnapshotService`、`DailyDistributionEngine`、`DailyCapacityService/Mapper`、`DailyPlanningService`、`MrpDailyPlanningController`、批次/执行服务与事实/覆盖/执行 Mapper、领域模型、Excel 解析/模板、功能权限规则及并发异常处理。
- 数据库：仅新增 `backend/src/main/resources/db/migration/V044__daily_capacity_calendar.sql`，扩展能力单位、主数据 revision 和冻结物料单位；未修改已应用迁移。
- 测试：日历维护、负荷评估、跨日事务/执行/发布/修订、有效计划校验和、并发 HTTP 响应、初排规则与前端组件；`BaseIntegrationTest` 类结束清理 Spring 上下文，避免复用已停止容器的连接。
- 契约：需求 v4.0、业务规则库、设计方案及 `.harness` 计算/数据/API契约、优先级、决策、缺陷与追踪记录。

## 业务不变量

- 日产能按自制末级物料有效数量、相同单位评估；不把采购/委外或异单位直接相加。
- 负荷按完整批次工厂/日计算，搜索、筛选、分页不改变总负荷；等于能力正常，超过才超限。
- 数量采用 BigDecimal/DECIMAL(18,6)，接口保留 decimal 字符串，前端以六位定点 BigInt 汇总与预览。
- 日期保留“来源日减 7 个自然日”，预警取最终排产日的冻结能力；不自动移动停产目标。
- 同来源需求转移两端同时提交、数量守恒；锁定/已执行日期禁止调整，发布计划通过修订调整。
- 日历与物料单位冻结于批次，修改当前配置不改写历史；新批次/修订采用明确的新快照。
- 新批次不再使用周产能和分配权重；历史规则与历史快照保持原语义。

## 验证命令与结果

```text
backend: ./mvnw compile -q
PASS

backend: ./mvnw test -q
PASS：304 tests，0 failures，0 errors，0 skipped

frontend: npm run build
PASS

frontend: npm test
PASS：7 files，41 tests
```

后端完整最后一轮执行为 `./mvnw compile -q && ./mvnw test -q > /tmp/mrp-calendar-full-test-r6.log 2>&1`，退出码 0；日志已归档。此前失败日志保留于 `/tmp`，最终轮确认已重新编译保存的测试源码。没有通过删除数据库或重置工作区绕过失败。

真实联调使用独立 schema `mrp_calendar_uat_1014_r1`、后端 `http://127.0.0.1:8082`、前端 `http://127.0.0.1:5182`。未占用已有 8080/8081 服务与 5180 前端，也未向原业务 schema 写入验收数据。独立库 Flyway 应用至 V044。

- 日历 API：15 项通过，包含两厂独立、未配置月、批量预览/提交/保留/复制、分页、单日及批量过期冲突、六列模板、错误整份回滚、错误末列、有效导入与导出。
- 停产日补验：实际 PUT 保存、GET 回读为停产/能力 0，revision 递增；另一工厂同日不变，最后恢复验收数据。
- 日排产 API：15 项通过，包含工厂完整负荷、转移守恒、旧运行/版本冲突、告警确认、空目标回转、锁定保护、重锁有效量、日周月守恒、快照冻结、带确认发布、无需周产能创建以及修订采用新能力。
- 功能权限：匿名 401；独立无权限用户成功登录后，工厂、日历读取、批量预览、跨日调整均返回 403。工厂数据域隔离不在此证明范围内。
- 真实完整计算：批次 5，无周产能行，预检 READY_TO_CALCULATE 且无配置事项，异步计算至 READY；31 条日事实，总量 1900，全部满足提前 7 个自然日及来源月份守恒，规则为 v2-daily-capacity。
- 真实浏览器：月历维护/批量/单日/复制/分页/空数据、精确六位小数、预览失效、模板下载、旧入口重定向、真实后端中断后的错误与重试、超产红格、转移解除红格、发布确认及空目标再次转出均通过；1440/1920 完整页面截图和 DOM 无页面横向溢出。

证据目录：`test-evidence/daily-capacity-2026-10-14/`。包含 `backend-test.log`、`backend-test-summary.json`、Surefire XML、前端 build/test 日志、`calendar-api-results.json`、`rest-day-api-results.json`、`daily-api-results.json`、`security-api-results.json`、`real-calculation-results.json`、验证脚本、`browser-report.md` 和 JPG 截图。独立复核归档为 `independent-review.md`。

最终独立 Reviewer 实际读取代码、SQL、组件测试及当前 39 个 Surefire 套件，核对 API 断言、浏览器记录与截图，结论 PASS；没有额外重复执行运行验收。Coordinator 据此将本补充任务更新为 DONE。

## 未完成与风险

- CAP-1014-03：仓库既有工厂数据域未全链路强制过滤，P1 OPEN。本次功能权限已验证，但不代表 Phase 2 数据域隔离或发布安全总门禁通过；整体阶段 REVIEW 与已有业务数据阻断保持原状态。
- 本版通过日期区间与星期批量设置；没有增加月历任意多个日期勾选或列表任意日期范围筛选。核心维护按月份进行。
- 首版为等量初排加预警/人工调整，不提供自动产能平衡、跨单位换算或多单位能力模型。
- 浏览器未执行 Excel 文件上传；真实 API 已完成有效/无效上传、整份回滚及导出验收。浏览器截图 API 曾裁剪 viewport，已补充实际 1440/1920 完整页面截图。
- 前端既有 Element Plus datepicker emits 提示及 bundle 大小提示，不影响构建/测试；未通过调整框架版本规避提示。

## 决策/待确认

用户已确认按同单位自制末级数量执行本方案；无阻断本次补充功能的待确认问题。权重仅保留历史兼容，当前表单、列表和导入移除，历史审计不改写。

## 下一步

本地代码与隔离环境验收交付；未提交 Git、部署或迁移原业务库。工厂数据域债务通过独立权限任务处理。

2026-10-14 后续运行修复：用户反馈原 5180 页面工厂接口不存在，已更新并重启其代理的 8080 主服务，真实 API 与两个页面恢复。主库此前已应用 V044，本次未执行新增迁移或写入验收业务数据；详见 [工厂接口运行环境修复报告](2026-10-14-factories-runtime-fix-report.md)。以上原隔离验收记录保留为当时状态。
