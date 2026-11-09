# Harness 问题台账

用于记录尚未上升为业务决策、但会影响执行的问题。关闭问题时保留记录，不删除历史。

## 2026-11-09 验收流程耗时问题

| ID | 级别 | 状态 | 原因与后续动作 |
| --- | --- | --- | --- |
| ACCEPTANCE-LOOP-1109 | P1 | REVIEW | 阶段 18/19 原规则把每次微改全量三层验证、119 项重复手工 UAT 和旧夹具失败重跑叠加，造成约 50 小时循环。已将执行入口改为 `reports/stage18-19-five-hour-acceptance.md` 的 300 分钟单轮流程：批内定向、收口一次全量、按指纹复用、逐 ID 证据映射；仍须实际执行一次并记录耗时/覆盖后才能关闭本流程问题。P0/P1、CALC-002 原 FAIL、PERF-003、P2-I002 和独立复核未被豁免。 |

## 2026-10-29 双工作台体验与数据口径增量（实施复核中）

| ID | 级别 | 状态 | 已核实问题／后续动作 |
| --- | --- | --- | --- |
| SPLIT-UX-01 | P1 | RESOLVED | 已按来源月过滤日格明细并保留事实键；旧版详情回归和 5180 浏览器验收通过。见 `RollingPlanningGrid.vue`、`RollingPlanningQueryService.java`。 |
| SPLIT-UX-02 | P1 | RESOLVED | v5 发布版复制原双确认操作者/时间/指纹；历史无快照显示不可追溯。当前隔离库 #73 是旧 v4 发布版，按原规则回显，不冒充 v5 验收。见 `MrpWorkbenchReviewService.java`、`ShortageSummaryIntegrationTest.java`。 |
| SPLIT-UX-03 | P1 | REVIEW | v5 整机与缺料合行、多仓库存池下钻已实现；独立首轮 FAIL 的跨厂待办串计/去重键冲突、升级前风险 NULL 归属、多仓局部缺策略漏报已用 V062 与代码/测试修复，全后端 646/646、5180 浏览器 8/8；仍待原 Reviewer 复核及专用 v5 全闭环。旧 v4 保留原行粒度。 |
| SPLIT-UX-04 | P2 | REVIEW | 隔离库历史批次 #60/run #54（v4）已按原始日事实复核：2026-10-29 两厂各有 67 个整机、各 11,255 台，无人工 override；冻结日历版本 #30 两厂各为 10 台/日，超出 11,245 台。来源月均为 2026-11，两厂各有 67 条经营计划、总计各 67,262 台；此证据只解释历史 #60，不等于用户截图对应批次。两厂同量且配置为 10 台/日，需业务核对输入与日历。 |
| SPLIT-UX-05 | P2 | RESOLVED | 缺料页工厂选项来自批次范围 API，受影响整机服务端分页；页头说明改帮助弹窗、策略入口收纳到待办选择器，保留确认门禁。5180 浏览器用例通过。 |
| SPLIT-UX-06 | P1 | REVIEW | 8180 健康检查已限定本地主 MySQL，重启后 200/UP；独立只读 JDBC 验证发现 Oracle TCP 1521 可连，但登录在协议握手时返回 `ORA-17800`，NC/BOM 同步源仍不可证实可用。后端全量 647/647、5180 浏览器 8/8，本轮后台发布修复获限定 PASS；需运维恢复 Oracle 源并单独核验 BOM 读取，不能把健康 200 当成 Oracle 可用。 |
| SPLIT-UX-07 | P1 | REVIEW | 2026-10-30 用户截图指出整机负荷日格内容溢出；真实 5180 浏览器复现首格内容高度 64px、按钮可视高度 59px。日列从 104px 调为 128px、行高从 60px 调为 72px，并修正 Element Plus 按钮内部纵向排列；重启 5180 后在 #73 历史 v4 批次的 1440/1920 视口各测 12 个可见负荷格，0 个水平或垂直溢出。新增浏览器几何回归，双工作台 E2E 9/9、本轮复测 2/2，后端 647/647；待独立复核，见 `reports/2026-10-30-machine-load-cell-layout.md`。 |

| ID | 日期 | 阶段 | 级别 | 问题 | 状态 | 负责人/下一动作 | 证据 |
|---|---|---|---|---|---|---|---|
| ROLE-DELETE-REF-1109 | 2026-11-09 | 18/19 | P1 | 删除仍被用户引用的角色返回数据库 500 | REVIEW | 角色服务先锁角色行，再检查 `user_role`；有引用返回明确 400 且关联不变，解除引用后可删。隔离原 FAIL、最终 14/14 API 与 Chrome 1/1、后端 725/725、夹具 0 残留，待独立阶段复核。 | `admin002-role-before-20261109/result.json`、`admin002-role-final-20261109/result.json`、阶段 18/19 报告 |
| PERF-FRONTIER-1109 | 2026-11-09 | 18/19 | P1 | 当前 BOM 大负载输入缺库存/物料，资源采样对象错误，PERF-003 无完整基线 | OPEN | 临时 #150/run #130 完成双厂 8,466 根、106,782 逐层、55,512 终点、1,202,760 日事实，逐键与逐层守恒均 0 差异；但库存快照两厂各仅 1,556 个物料编码，持久化缺库存 6,326、缺主数据 57，状态 `MATERIAL_REVIEW`，非无阻断闭环。探针采空闲 Worker，实际计算在 API；需补合法全覆盖输入、独立 Decimal oracle、API 资源/SQL 及故障基线，原 PERF-003 继续 NOT_RUN。 | `test-kit/2026-10-15/evidence/runs/current-frontier-material-20261109/result.json`、`current-frontier-sql-verify-20261109/result.json`、阶段 18 报告 |
| MATERIAL-ALIGN-SCAN-1109 | 2026-11-09 | 18 | P1 | 物料日期对齐每个物料组扫描完整整机日事实 | REVIEW | 已按工厂/整机/来源月预分组；新 jar 独立 8184 从 #7 复算 #151，三类事实 380/360/2,820 行 SHA 相同、守恒 0、清理 0，当前前端 5181/新 API 定向 Chrome 1/1 数据载入。Surefire 100/724 XML 全绿但包装丢 Maven 退出码，不计全量总门禁 PASS；正确 API 大负载资源/SQL 仍未复核收益。 | `DailyPlanningService.java`、`frontier-new-code-small-recalc-20261109/result.json`、`e2e-frontier-new-code-data-ready-20261109/`、阶段 18 报告 |
| TEST-EXIT-WRAPPER-1109 | 2026-11-09 | 18 | P1 | 全量测试启动包装误用 zsh 只读变量导致 Maven 退出码丢失 | RESOLVED | 本次用非保留变量 `run_rc` 保存真实退出码；最终独立 schema/Redis 6391 全量 100 套件/725 项退出 0、0 失败/错误/跳过。先前 724 项 XML 与本次首轮断言误读的 1 FAIL 日志均保留，不当作最终 PASS。 | `/tmp/mrp-admin002-fulltest-final-20261109.log`、`backend/target/surefire-reports/`、阶段 18 报告 |
| ROLE-CONFIG-POSITIVE-1109 | 2026-11-09 | 18/19 | P1 | 角色配置正向授权、工厂范围与旧 JWT 时序证据不足 | REVIEW | 隔离 8183 一次性探针 25/25 PASS：仅 `role:assign_permission` 可保存目标角色权限与范围，非法工厂配置回滚，同一旧 JWT 对授权、指定/全部/无工厂范围和撤权即时生效。临时用户/角色/审计 0 残留、发布 #2 不变。仅关闭限定子项；ADMIN-003 全接口有权操作及独立阶段复核仍缺。 | `test-kit/2026-10-15/evidence/runs/role-configuration-positive-20261109/result.json`、阶段 18/19 报告 |
| P2-I001 | 2026-09-22 | 10/17 | P1 | 初始仓库 Vite 8、TypeScript 6、`@types/node` 24 与 `.nvmrc` Node 20 不一致，本机 Node 23 又是非 LTS | RESOLVED | 2026-09-29 经用户授权统一为 Node 24 LTS，并同步 `.nvmrc`、`package.json`、项目契约和技术基线 | 环境审计 |
| EXPORT-VIEW-AUTH-1106 | 2026-11-06 | 18/19 | P1 | 仅有 `mrp_shortage:view` 的账号可用默认 `MACHINE_OUTPUT` 视图导出整机排产 XLSX | REVIEW | `SecurityConfig` 允许物料查看进入共用导出端点，`MrpDailyPlanExportService` 只校验 `TERMINAL`，默认整机视图漏验 `mrp_plan:export`。隔离临时角色复现整机/物料均 200；服务分视图校验后整机 403、物料 200，临时用户/角色 0 残留、发布 #2 不变。33 组路由矩阵、后端全量 100 套件/719 项、隔离角色页和发布页 Playwright 各 1/1 通过。仍待独立复核，ADMIN-003 全接口矩阵未完成。 | `MrpDailyPlanExportService.java`、`MrpDailyPlanExportServiceTest.java`、`EndpointPermissionRegressionTest.java`、`test-kit/2026-10-15/evidence/runs/export-view-permission-20261106/result.json` |
| INVENTORY-CONFIRM-AUTH-1106 | 2026-11-06 | 18/19 | P1 | 库存确认导入未使用角色配置页的 `inventory:import_confirm` 权限 | REVIEW | 旧隔离镜像仅上传权限两条确认路径返回业务 400/404、仅确认均 403；路由精确授权与两页确认按钮/处理函数已修。新镜像仅上传 403/403、仅确认进入业务层 400/404；后端全量 100 套件/721 项、前端构建/111 单测、隔离 Chrome 两页 1/1 PASS，临时用户/角色 0 残留、#2 不变。旧 FAIL 原件保留；限定自测 PASS，待独立复核及 ADMIN-003 全接口矩阵。 | `SecurityConfig.java`、`EndpointPermissionRegressionTest.java`、`InventoryImport.vue`、`ForecastImport.vue`、`test-kit/2026-10-15/evidence/runs/inventory-confirm-permission-before-20261106/result.json`、`test-kit/2026-10-15/evidence/runs/inventory-confirm-permission-after-20261106/result.json` |
| USER-DIRECT-PERM-1106 | 2026-11-06 | 18/19 | P0 | 仅 `user:update` 可给自己直授 `ALLOW`，并可重置密码、启停账号 | REVIEW | 旧镜像 `PUT /users/{id}/permissions` 实证 200 且落库 1 条。V065 新增 `user:assign_permission`、`user:reset_password`，启停分别校验既有 `user:enable`/`user:disable`；角色权限树可见。新镜像仅编辑账号直授、重置、停用均 403，仅启用也不能停用；授直授权限后 200/落库 1 条。后端最终 100 套件/724 项全过，隔离浏览器定向 2/2，临时夹具 0 残留。限定自测 PASS，待独立复核；ADMIN-003 总项仍 NOT_RUN。 | `user-direct-permission-before-20261106/result.json`、`user-security-after-20261106/result.json`、`V065__split_user_security_permissions.sql`、阶段 18/19 报告 |
| USER-CREATE-ROLE-1106 | 2026-11-06 | 18/19 | P0 | 仅 `user:create` 可在创建账号请求中夹带 ADMIN `roleIds`，绕过 `user:assign_role` | REVIEW | 旧隔离镜像真实 API 创建 200，SQL 存在 ADMIN 角色关联 1 条；临时夹具 0 残留。`UserController.create` 对非空 `roleIds` 增加 `user:assign_role` 校验；新镜像无角色分配权限时 403/无落库，授角色分配权限后 200/落库 1 条。独立测试库集成断言和最终全量 724/724 PASS，限定自测通过，待独立复核。 | `user-create-role-before-20261106/result.json`、`user-security-after-20261106/result.json`、`UserController.java`、`RoleConfigurationIntegrationTest.java` |
| E2E-EVIDENCE-1106 | 2026-11-06 | 18/19 | P1 | 本轮复测沿用既有 Playwright `outputDir`，早期角色页失败 trace 被测试运行清理 | OPEN | 11-05 失败原因和修复过程仍在阶段报告，但原 trace 文件在现有证据目录未找到，不能宣称原始失败 trace 完整保留。11-06 已用独立目录重跑角色配置和发布页，trace/截图保存为新证据，两项各 1/1 PASS。后续每次浏览器运行指定新的仓库内输出目录，独立 Reviewer 审查此证据缺口。 | `test-kit/2026-10-15/evidence/e2e-role-export-20261106/`、`test-kit/2026-10-15/evidence/e2e-published-export-20261106/`、阶段 18/19 报告 |
| P2-I002 | 2026-09-22 | 18 | P0 | 后端/正式 Compose 曾存在 secret-like 默认值；V001 已应用迁移仍含固定管理员播种哈希 | IN_PROGRESS | 11-05 已修复管理员建号/重置随机一次性密码、首次改密、旧 JWT 改密后失效、飞书新用户无固定本地密码、登录页无预填。隔离管理员已一次性引导及改密，旧固定口令拒绝；V001 不可改。正式现有凭据轮换须由授权运维在维护窗口执行，正式资源保持只读；完整秘密扫描与独立安全复核未完成，总项仍 FAIL | `LegacyAdminBootstrap.java`、`UserService.java`、`JwtService.java`、`LoginView.vue`、阶段 18 续验报告 |
| P2-I002-BOOTSTRAP | 2026-11-05 | 18 | P0 | V001 已应用的固定管理员播种哈希在隔离库曾可直接登录 | REVIEW | 启动引导仅精确替换播种哈希，缺少强随机引导口令时拒绝启动；登录/JWT 拒绝旧哈希。隔离 8183 真正引导并首次改密，原固定口令、引导口令和旧 JWT 拒绝，新口令 API/Chrome PASS；重启后 API/Worker 引导变量均为空，V064/64、#2/run #2 PUBLISHED。最终后端 99 套件/684 项通过；正式凭据轮换和独立安全复核未做，P2-I002 总项仍 FAIL。 | `LegacyAdminBootstrap.java`、`LegacyAdminBootstrapTest.java`、`/tmp/mrp-stage18-bootstrap-fulltest-script-final-20261105.log`、阶段 18 报告 |
| ROLE-CONFIG-1105 | 2026-11-05 | 18/19 | P1 | 角色权限页不能配置工厂范围；角色启停/编辑遗漏字段写 NULL，停用角色仍可能经有效权限缓存授权，刷新显示旧菜单；复制角色丢失工厂域 | REVIEW | 新增单事务配置、范围校验和页面控件；有效权限 SQL 仅取 ACTIVE 角色/权限，刷新读 `/auth/me`。补查到事务提交前清缓存可竞态回填旧授权，小团队改为每次从 MySQL 取有效权限；故意回填旧缓存的集成断言 PASS。最终后端 100 套件/685 项、隔离新镜像 Chrome 1/1 与真实 API 落库 PASS，#2 未变。仅限定自测，完整角色/工厂接口矩阵和独立复核仍缺。 | `UserService.java`、`RoleService.java`、`PermissionConfig.vue`、`RoleConfigurationIntegrationTest.java`、`role-configuration-acceptance.spec.ts`、阶段 18/19 报告 |
| ROLE-MATRIX-1105 | 2026-11-05 | 18/19 | P1 | 七个非管理员角色缺真实 API 权限/工厂域运行矩阵 | REVIEW | 11-05 选定端点 37/37；11-06 扩展到 138/138、375/375、382/382 真实 API 断言 PASS。七角色更多无权写路由；临时仅爱培科角色的工厂/主数据/仓库映射各 1 行、日历 212 行，仓库映射分页 1/1、库存策略 1,564 条均归本厂、跨厂日历月份 403。旧版日历版本 0 行无正向证据；临时用户/角色/审计 0 残留、#2 不变。ADMIN-003 有权操作及其余跨厂内容与独立复核仍缺，早期探针误报 FAIL 原件保留。 | `test-kit/2026-10-15/evidence/runs/role-matrix-final-20261105/result.json`、`role-matrix-expanded-content-20261106/result.json`、`role-matrix-single-factory-20261106/result.json`、`role-matrix-scope-pages-20261106/result.json` |
| BIGCALC-1105 | 2026-11-05 | 18/19 | P0 | 当前镜像大批次重算与逐键守恒缺运行证据 | REVIEW | 8184 当前 API/Worker 复跑 #144/task121/run124 45.286 秒，9,174 逐层、17,382 终点、217,275 日事实业务字段 SHA 与发布来源 #13 一致，守恒差异 0，精确清理 0 残留。Worker 最高采样 892.6 MiB，SQL 为含后台/探针的全局增量；原始 224,250/456,600 键、独立 oracle、低堆与故障压力仍缺，不关闭 TESTKIT-PERF-01。首次 checksum 断言误报证据保留。 | `test-kit/2026-10-15/evidence/runs/current-large-calculation-monitored-20261105/result.json` |
| CALC-003-1106 | 2026-11-06 | 18/19 | P0 | 原诊断 accuracy-distinct 需关联当前镜像与独立 oracle | REVIEW | 早期真实导入 #2 的 4,194 日键完整 HTTP 对独立 Decimal oracle 0 差异，#2/#3 三类事实 SHA 相同；当前 #146/task123/run126 复制 #3 冻结输入与原 #2 的 176 池，515 逐层/终点、4,194 日事实 SHA 与 #3 相同、守恒 0 差异、临时数据 0 残留。首次 #145 因探针漏复制池而库存使用 0，FAIL 原件保留并精确清理。CALC-003 本轮证据 PASS，完整阶段独立 Reviewer 仍缺；CALC-002 仍 NOT_RUN。 | `test-kit/2026-10-15/evidence/runs/current-accuracy-distinct-with-pools-20261106/result.json`、原 `oracle-comparison.json`、阶段 18 报告 |
| CALC-002-FIXTURE-1106 | 2026-11-06 | 18/19 | P0 | 原始 accuracy-stock 8,436 日键断言与 C021/C023 上海 BOM 规则冲突 | OPEN | 原快照 #33 有上海 119,595、无锡 14,450 边；隔离 #147/task124 复现两组织相同物料路径撞逐层事实唯一键、任务 `CALC_ERROR`，临时 0 残留。现行规则仅允许混合组织历史快照只读追溯，不得用来新算或修改引擎迁就原断言。上海 #34 同一原始经营计划/库存导入 #7 为 v4 历史批次，2,820 键独立 oracle 0 差异；当前 #149、#151 重算三类系统事实完整 SHA 与 #7 相同、守恒 0、清理 0，限定通过，但不能替代 C023 当前同步 BOM 新批次准确性。原 CALC-002 仍 FAIL；修订执行口径为冻结当前上海 BOM checksum 与合法计划/库存输入，新批次完整 HTTP 日事实对独立 Decimal oracle 逐键比对，并核对三类事实和日周月守恒。旧失败现场保留。 | `test-kit/2026-10-15/evidence/runs/current-accuracy-stock-20261106/result.json`、`current-accuracy-stock-original-system-20261106/result.json`、`frontier-new-code-small-recalc-20261109/result.json`、阶段 18/19 报告 |
| SHORTAGE-LEGACY-REVIEW-1105 | 2026-11-05 | 18/19 | P1 | 历史已发布 v4 缺料页阻塞在无用双岗确认指纹计算 | REVIEW | #13 Chrome 无竞争约 14.6 秒，`/reviews` 约 11.6 秒；前端仅对已发布非 v5 跳过该请求，5181/8184 实测约 1.029 秒、1,459 汇总物料不变、0 页面错误。v5 #2 在 5182/8183 仍请求 `/reviews` 200、6 条缺料正常。新增测试后前端 build/111 项、后端 100 套件/685 项全过；待独立复核，PERF 总项开放。 | `ShortageWorkspace.vue`、`ShortageWorkspace.test.ts`、阶段 18 报告 |
| ACCEPTANCE-SCRIPT-CREDENTIAL-1105 | 2026-11-05 | 19 | P1 | 隔离成功链脚本两处固定管理员口令已失效，影响后续可复现执行 | REVIEW | `docker_acceptance_chain.mjs` 改为要求 `MRP_ACCEPTANCE_ADMIN_PASSWORD` 且缺失即失败，不输出口令；`node --check`、后端全量 684/684、前端 build/110、隔离 API 与 Chrome 1/1 均通过。现存 #2 保留，未重复写入成功链；待独立复核。 | `test-kit/2026-10-15/tools/docker_acceptance_chain.mjs`、阶段 18/19 报告 |
| ACCEPTANCE-LEGACY-SCRIPT-1105 | 2026-11-05 | 18/19 | P1 | 旧接口隔离安全探针仍用已轮换的固定管理员口令，无法复验 | REVIEW | 改为显式验收环境变量；当前 8183 综合探针 24/24 断言 PASS，夹具及审计 0 残留、#2 保留；后端全量 684/684、前端 build/110、Chrome 1/1。旧 8182 工具未批量改写，完整安全扫描及独立复核仍缺。 | `test-kit/2026-10-15/tools/legacy_phase1_gate.mjs`、`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261105080353273/result.json`、`/tmp/mrp-stage18-legacy-bootstrap-fulltest-20261105.log` |
| ADMIN-005-SOURCE-SCAN-1105 | 2026-11-05 | 18 | P1 | 有限扫描命中历史登录 bundle 和旧验收工具的固定示例口令，其他打包/归档未审计 | OPEN | 命中 15 个历史 `LoginView` bundle、38 个旧 test-kit 脚本；当前 Dockerfile 构建时清空并重建静态目录，但未证明本地打包、日志、审计和归档均无敏感值。按真实入口和打包路径分批核对，保留失败证据；ADMIN-005 和 P2-I002 总门禁不关闭。 | `backend/Dockerfile`、`backend/src/main/resources/static/assets/`、`test-kit/2026-10-15/tools/`、阶段 18 报告 |
| P2-I003 | 2026-09-22 | 17 | P1 | Playwright 配置硬编码本机 Chromium 路径 | RESOLVED | 已改为 Playwright 标准 `Desktop Chrome` 设备配置，不再绑定本机路径 | `frontend/playwright.config.ts` |
| P2-I004 | 2026-09-22 | 19 | P0 | 旧 Docker/UAT 脚本有错误拼写、虚假 PASS 和主卷删除风险 | PARTIAL_REVIEW | 破坏性 Compose 操作及固定批次写入已移除；临时 MySQL 迁移 2/2、隔离 Docker API/Worker/前端成功链 #2/run #2、重启持久性和本轮浏览器 1/1 均有证据。完整 119 项 UAT、备份恢复、外部飞书回调及独立 Reviewer 未过，阶段 19 仍 REVIEW | `deploy/test-docker-acceptance.sh`、`deploy/run-uat-tests.sh`、阶段 19 报告 |
| LEGACY-1104-WRITE | 2026-11-04 | 18 | P1 | Phase 1 旧重算、周 override、发布写入口仍能进入服务层 | REVIEW | 安全规则已拒绝旧写路径、保留历史导出任务；旧镜像真实 HTTP 500/404，隔离最终镜像四个旧写入口均 403、导出校验 400；后端 675/675、浏览器 1/1。待独立阶段复核 | `SecurityConfig.java`、`PlanningApiTest.java`、`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104115334130/result.json` |
| LEGACY-1104-SCOPE | 2026-11-04 | 18 | P1 | Phase 1 旧列表、当前版本、网格、对比及重算任务未校验工厂域；旧任务入口可读取 Phase 2 任务 | REVIEW | Controller/SQL 链路根因已修复；隔离双厂旧版本/单厂账号旧镜像 FAIL、最终镜像全部断言 PASS，夹具 0 残留，发布版 #2 保留；后端 675/675、浏览器 1/1。v1/v2/v3 真实历史回放和独立复核仍缺 | `PlanningController.java`、`PlanningFactoryAccessService.java`、`SecurityConfig.java`、同上新镜像证据 |
| AUTH-LOG-1104 | 2026-11-04 | 18 | P1 | API 启动日志打印 Spring 自动生成的密码 | REVIEW | 前镜像日志复现，HTTP Basic 探针 401；API 入口排除未使用的内存用户自动配置后，隔离新容器启动日志无生成密码/内存用户提示，后端 675/675、真实登录与浏览器通过。待独立安全复核；完整审计敏感信息扫描另属阶段总门禁 | `MrpApiApplication.java`、`PlanningApiTest.java`、阶段 18 报告 |
| AUTH-009-1104 | 2026-11-04 | 18 | P1 | 账号停用后原 JWT 仍可读取受限 API | REVIEW | 原镜像停用后旧 token GET `/plans` 200，失败证据保留。JWT 过滤器增加当前用户 ID/用户名/启用状态 SQL 检查，最终隔离镜像权限 API 撤销 `schedule:view` 后旧 token 403、停用后 401；后端 675/675、Playwright 1/1、夹具与审计 0 残留。待独立阶段复核 | `JwtAuthenticationFilter.java`、`UserService.java`、`UserMapper.xml`、`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104122135986/result.json` |
| EXPORT-DOWNLOAD-1104 | 2026-11-04 | 18/19 | P1 | 旧导出任务元数据暴露 `filePath`，下载端点直接读取数据库路径，隔离 API 可下载导出目录外文件 | REVIEW | `ExportTask` API 序列化隐藏路径，`ExportService` 先校验所有权/工厂域，再只允许导出目录内真实 `.xlsx` 普通文件；目录外、缺失和符号链接返回无路径的 404。原镜像 HTTP 200/路径泄露失败证据及最终真实 XLSX、同权限跨账号 403、目录外/缺失 404 均保留；编译、97 套件/676 项、隔离 Chrome 1/1、夹具 0 残留。LIFE-009 时间到期口径及独立安全复核仍缺 | `ExportService.java`、`ExportTask.java`、`ExportTaskOwnershipIntegrationTest.java`、`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104122903394/result.json`、`.../legacy-phase1-gate-20261104130228745/result.json` |
| AUTH-005-UI-1104 | 2026-11-04 | 18/19 | P1 | READONLY 可访问的经营计划、库存、物料、分类、BOM 页面仍显示写按钮 | REVIEW | 对照后端权限码，经营计划/库存导入、物料增删改及导入、分类增删改/启停、BOM 同步均按相应管理权限隐藏。修复前隔离 Chrome 经营计划和库存导入各显示 1；最终六个 READONLY 页面写按钮计数 0、管理员导入计数 1，API 五类写请求 403；前端构建、110/110、后端 97 套件/676 项、隔离 Playwright 1/1，夹具 0 残留。待独立阶段复核，全接口权限矩阵仍 OPEN | `ForecastImport.vue`、`InventoryImport.vue`、`MaterialView.vue`、`MaterialCategoryView.vue`、`BomView.vue`、`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104133304962/result.json` |
| LIFE-010-1105 | 2026-11-05 | 18/19 | P1 | 完整公式前缀输入到导出 UAT 尚无真实可编辑入口证据 | OPEN | 已追踪 `PlanningController -> ExportService -> PlanMapper` 及 Phase 2 两个 POI XLSX 导出；隔离 SQL 夹具的四种前缀文本经真实 API 下载后为 11 个字符串单元格、0 公式节点，临时数据/文件清零。此为限定导出安全子项，仍需按 LIFE-010 原用例从可编辑文本入口进入并复核生成版本；阶段安全总门禁不关闭。首次探针共享字符串去重误报保留。 | `test-kit/2026-10-15/tools/life010_formula_gate.mjs`、`test-kit/2026-10-15/evidence/runs/life010-formula-3115b413e75842fa/result.json`、`.../life010-formula-b9c5d38764cd4782/result.json` |
| AUTH-WAIT-COPY-1105 | 2026-11-05 | 19 | P2 | 零权限账号进入等待权限页时误显示“密码修改成功” | REVIEW | 标题已改“等待权限分配”，隔离 Chrome 首次改密后实际进入该页并确认文案；前端 build/110 单测通过。待独立复核，原截图保留。 | `frontend/src/views/login/WaitingPermission.vue`、`test-kit/2026-10-15/evidence/runs/auth004-no-permission-20261105/waiting-permission.png` |
| FEISHU-DISABLED-1105 | 2026-11-05 | 18 | P1 | 飞书按邮箱绑定现有账号分支未检查 `is_active`，可为停用账号签发 JWT | REVIEW | 已在绑定前拒绝停用账号；独立 MySQL 集成测试覆盖停用绑定拒绝、飞书新用户无本地密码及固定口令登录拒绝，最终后端全量 97 套件/678 项通过。外部飞书回调及独立安全复核仍未完成。 | `backend/src/main/java/com/mrp/auth/service/FeishuService.java`、`backend/src/test/java/com/mrp/api/AuthApiTest.java`、`/tmp/mrp-stage18-fulltest-final-20261105.log` |
| AUTH-ERROR-DISCLOSURE-1105 | 2026-11-05 | 18 | P1 | 飞书令牌/用户信息失败时记录上游响应体和异常；飞书回调及改密错误响应拼接异常详情，可泄漏令牌、连接信息 | REVIEW | 固定日志和业务错误文案已修复，复核时又清除改密异常堆栈；含敏感标记的定向回归、最终后端 98 套件/681 项、隔离镜像真实飞书失败 API/日志、Docker 浏览器 1/1 均 PASS。外部真实授权成功回调、完整日志/审计扫描和独立安全复核未完成，不关闭 ADMIN-005 总项。 | `FeishuService.java`、`AuthController.java`、`FeishuErrorDisclosureTest.java`、`/tmp/mrp-stage18-feishu-security-fulltest-final-20261105.log`、阶段 18 报告 |
| P2-I005 | 2026-09-22 | 10/17 | P2 | 前端 `package.json` 无 lint script，旧门禁不可直接执行 | OPEN | 记录是否补 lint；未补前不得虚报 lint PASS | `frontend/package.json` |
| P2-I006 | 2026-09-22 | 15/16 | P0 | Q2-012、Q2-013、Q2-014、Q2-016 已冻结；Q2-015 月中重算库存分配策略由业务在每次重算时选择 | RESOLVED | 产品规则已确定为“保留原分配/重新分配”显式二选一；旧分配不得被静默覆盖，完整重算交互仍需随真实业务数据验收 | `assets/MRP待确认问题池.md` Q2-015、用户确认记录 |
| P2-I007 | 2026-09-22 | 10 | P1 | 前端分组 key 不包含 rootMaterialCode，同末级物料来自不同整机时会合并到同一行 | RESOLVED | 日计划行键已包含工厂、来源整机、末级物料、类型、供应方式和路径组 | `DailyDistributionEngine.java`, `MrpDailyPlanningController.java`, `DailyPlanningGrid.vue` |
| P2-I008 | 2026-09-22 | 10 | P1 | PlanningService 中 forecastByMaterial 使用 BigDecimal 但传给 Calculator 时 .longValue() 截断 | OPEN | Phase 2 新引擎全链路使用 BigDecimal，不修补旧 Calculator | 阶段 10 代码审计 |
| P2-I009 | 2026-09-23 | 18 | P0 | BomExplosionServiceTest 依赖 Testcontainers 但 Docker 环境不可用 | RESOLVED | Docker Desktop 已恢复，Testcontainers 从空库执行 V001-V034；后端全量 221 项测试通过 | 2026-09-29 `./mvnw test` |
| P2-I010 | 2026-09-23 | 16 | P1 | lock-and-calculate 端点已移除但 SecurityConfig 仍引用 | RESOLVED | 流程改为 precheck→resolve→calculate(async)，不再需要 lock-and-calculate | SecurityConfig 已更新 |
| P2-I011 | 2026-09-23 | 16 | P1 | DecisionService 不更新 batch unresolved_count | RESOLVED | recordDecision 后更新 batch unresolved_count 和状态 | DecisionService.java 已修复 |
| P2-I012 | 2026-09-23 | 16 | P1 | MrpDecisionIssueMapper.insertBatch 使用 INSERT 而非 INSERT IGNORE | RESOLVED | 改为 INSERT IGNORE 防止重复 key 冲突 | MrpDecisionIssueMapper.xml 已修复 |
| P2-I013 | 2026-09-23 | 16 | P1 | MrpTaskWorkerService updateStatus version 错误 | RESOLVED | 使用 claimedTask.getVersion() 而非 +1 | MrpTaskWorkerService.java 已修复 |
| P0-301 | 2026-09-23 | 16 | P0 | 计算写入 BLOCKING issue 后仍强制 READY | RESOLVED | 检查 issue severity，BLOCKING/ERROR 时 batch→WAITING_DECISION，run→COMPLETED_WITH_ISSUES | MrpCalculationService.java, MrpTaskWorkerService.java |
| P0-302 | 2026-09-23 | 16 | P0 | 计算 run 没有隔离，查询按 batch_id 混读 | RESOLVED | 所有结果查询使用 active_run_id，DailyPlanningService 使用 selectByRunId | DailyPlanningService.java, BatchOrchestratorService.java |
| P0-303 | 2026-09-23 | 16 | P0 | Worker 没有真正的 lease/heartbeat/retry | RESOLVED | V028 添加 worker_id/lease_until/heartbeat_at/started_at；claimTask CAS；heartbeatForWorker | V028, CalcTaskMapper.xml, MrpTaskWorkerService.java |
| P0-304 | 2026-09-23 | 16 | P0 | 失败清理可能删除上一次成功的 active run | RESOLVED | 存储 runId 到 task payload；清理只删除 task 的 runId，不删除 active_run_id | MrpTaskWorkerService.java |
| P0-305 | 2026-09-23 | 16 | P0 | 任务输入仍可被请求参数替换 | RESOLVED | calculate API 只接收 batchId；Worker 从 batch 固化版本加载 root demand | MrpBatchLifecycleController.java, BatchOrchestratorService.java |
| P0-306 | 2026-09-23 | 16 | P0 | 库存缺记录在空快照时仍可能静默按0 | RESOLVED | inventory key 包含 factory+org+material+quality；materialsWithInventory 始终填充 | NetRequirementEngine.java, MrpCalculationService.java |
| P0-307 | 2026-09-23 | 16 | P0 | 执行确认没有绑定真实日计划 rowKey | RESOLVED | confirm API 接收真实 rowKey；校验属于 active run；数量差异持久化 EXECUTION_QUANTITY_MISMATCH | MrpExecutionService.java, MrpExecutionController.java |
| P0-308 | 2026-09-23 | 16 | P0 | 发布和修订共享/复制错误的 run | RESOLVED | 发布创建独立 batch+run+日事实；修订只复制 LOCKED/EXECUTED 日 | BatchOrchestratorService.java |
| P0-309 | 2026-09-23 | 16 | P0 | BOM 快照裁决仍不能证明最高有效版本唯一 | RESOLVED | 已有 org+parent+child 分组、版本值排序、重复最高版本检测、有效期重叠检测 | BomSnapshotService.java |
| P1-301 | 2026-09-23 | 18 | P1 | 权限和操作者身份仍不完整 | RESOLVED | 添加 mrp_config:manage, mrp_calendar:manage, mrp_execution:confirm, mrp_inventory:allocate 权限 | SecurityConfig.java |
| P1-302 | 2026-09-23 | 17 | P1 | 前端不是 Phase 2 工作台 | RESOLVED | 已实现单一当前排产入口、日排产主网格、周/月汇总、决策中心、库存分配和执行确认 | `MrpBatchList.vue`, `MrpWorkspace.vue`, `frontend/src/views/mrp/components/` |
| P1-303 | 2026-09-23 | 19 | P1 | Playwright 与 UAT 不是 Phase 2 验收证据 | BLOCKED_ENVIRONMENT | Phase 2 用例已补充，但本机缺 Playwright 管理的 Chromium 二进制；重点路径已用真实 Chrome 人工验收，自动化不得虚报通过 | `frontend/e2e/phase2-mrp.spec.ts`, Playwright 启动错误 |
| P0-401 | 2026-09-23 | 16 | P0 | 新增批次前后端契约断裂，页面必现500 | RESOLVED | 前端加载版本列表让用户选择；后端接收全部版本ID | MrpBatchController.java, MrpBatchList.vue, mrp.ts |
| P0-402 | 2026-09-23 | 16 | P0 | READY 状态与待分配/守恒结果矛盾 | RESOLVED | READY门禁检查：无OPEN issues、无pending unallocated、守恒通过 | MrpTaskWorkerService.java |
| P0-403 | 2026-09-23 | 16 | P0 | 日/月守恒键不一致，跨日期计划被误判或漏判 | RESOLVED | 统一 sourceMonth 格式为 yyyy-MM | DailyPlanningService.java |
| P0-404 | 2026-09-23 | 16 | P0 | active run 隔离仍不完整 | RESOLVED | getUnallocated 使用 runId；所有查询默认解析 active run | DailyPlanningService.java |
| P1-401 | 2026-09-23 | 17 | P1 | 主业务入口和页面信息架构仍不符合 Phase 2 | RESOLVED | 新增 MrpWorkspace 页面，包含日排产网格、周/月汇总、决策中心、库存分配、执行确认 | MrpWorkspace.vue, DailyPlanningGrid.vue |
| P1-402 | 2026-09-23 | 17 | P1 | 列表违反服务端分页和筛选契约 | RESOLVED | 添加分页查询端点和前端分页组件 | MrpBatchController.java, MrpBatchList.vue |
| P1-403 | 2026-09-23 | 17 | P1 | 执行/锁定与修订链路尚未形成可验收闭环 | RESOLVED | 前端集成执行确认API，显示执行状态 | ExecutionConfirm.vue |
| P1-404 | 2026-09-23 | 19 | P1 | 任务 lease/失败清理和 CAS 仍缺真实证明 | RESOLVED | 添加 MrpTaskWorkerServiceTest 测试 READY 门禁和失败清理 | MrpTaskWorkerServiceTest.java |
| P1-405 | 2026-09-23 | 16 | P1 | 库存/BOM确定性边界未全部闭合 | RESOLVED | 已有 GOOD/DEFECTIVE 质量追踪和 BOM 快照裁决逻辑 | InventoryNormalizationService.java, BomSnapshotService.java |
| P2-I014 | 2026-09-29 | 19 | P0 | 产能配置工厂编码为“永惠/爱培科”，经营计划为 `F001/F002`，生产日历仅有 `F001`，主数据编码体系不一致 | WAITING_DECISION | 业务/主数据负责人确认统一工厂编码及迁移口径后，再执行完整新批次 UAT | 数据库只读核查：`capacity_line`、经营计划版本 1、生产日历发布版本 |
| P2-I015 | 2026-09-29 | 13/19 | P0 | 现有库存快照 `warehouse_code` 为空，且尚无已发布工厂仓库映射、库存控制策略 | WAITING_DATA | 按已确认的一厂一仓规则发布映射和库存策略，并使用新版五列表格重新导入库存快照 | 配置 API 与库存快照数据库核查 |
| P2-I016 | 2026-09-29 | 15/19 | P0 | 尚无已发布业务周版本，无法证明 W1-Wn 周汇总符合业务定义 | WAITING_DATA | 业务按月份维护并发布 W1-Wn 日期范围后完成日排产、周汇总全流程 UAT | `/api/v1/mrp/business-weeks` |
| P2-I017 | 2026-09-29 | 19 | P1 | 本机未安装 Playwright 管理的 Chromium Headless Shell | BLOCKED_ENVIRONMENT | 按项目环境规则由用户/环境管理员准备 Playwright 浏览器；不得修改技术栈或虚报 40 项 E2E 通过 | `npx playwright test` 启动错误 |

级别：

- P0：数据错误、规则错误、安全绕过、不可恢复迁移；阻断阶段；
- P1：核心流程不可用、性能或幂等严重问题；阻断发布；
- P2：非核心缺陷或可接受限制；必须在最终交付说明；
- P3：建议优化，不影响当前完成定义。

状态：`OPEN`、`IN_PROGRESS`、`WAITING_DECISION`、`WAITING_DATA`、`BLOCKED_ENVIRONMENT`、`RESOLVED`、`CLOSED`。

## 2026-10-14 日产能改版补充

| ID | 日期 | 范围 | 级别 | 问题 | 状态 | 处置/证据 |
|---|---|---|---|---|---|---|
| CAP-1014-01 | 2026-10-14 | 日调整/执行 | P1 | 转移后锁定读取系统量，空日期不能冻结；解锁再调整后重锁仍冻结旧量 | RESOLVED | 有效投影读取 + freezeStatus同步plannedQuantity；真实MySQL14条，覆盖发布/修订 |
| CAP-1014-02 | 2026-10-14 | 日历/调整API | P2 | 过期revision/预览指纹冲突返回400 | RESOLVED | 专用ConcurrentChangeException→409；单日/批量/转移/发布真实API通过 |
| CAP-1014-03 | 2026-10-14 | 既有工厂数据域权限 | P1 | MRP已有功能权限检查，但工厂域尚未全链路强制过滤；本次新增接口沿用既有机制 | OPEN | 属于既有债务；本次功能验收不代表Phase2权限/发布安全总门禁通过，后续独立补工厂域 |
| CAP-1014-04 | 2026-10-14 | 集成测试基础类 | P2 | 不同子类停用MySQL容器后仍复用Spring连接上下文 | RESOLVED | BaseIntegrationTest使用AFTER_CLASS清理上下文，全量真实MySQL测试通过 |

| CAP-1014-05 | 2026-10-14 | 空目标再次转出 | P1 | 空日期接收调整量后，编辑依赖原始系统行，无法再次转出 | RESOLVED | 有效人工投影回退系统量0；组件回归及真实浏览器16→15回转100通过 |
| CAP-1014-06 | 2026-10-14 | 主服务运行版本 | P1 | 5180 代理的旧 8080 进程未加载工厂接口，两个配置页面加载失败 | RESOLVED | 同环境编译/静态资源同步并重启；主服务和代理 9 项 API 200、实际浏览器恢复，304 测试及独立复核 PASS；见 factories-runtime-fix-2026-10-14 证据 |

| CAP-1014-07 | 2026-10-14 | 跨月周汇总 | P1 | 提前7天落到上月的日事实缺冻结周scope，部分重叠周被整周丢弃 | RESOLVED | 新建补已维护覆盖月、修订补缺失快照、归一化保留未覆盖尾段；330测试与真实API/浏览器周1800通过 |
| CAP-1014-08 | 2026-10-14 | 调产参数错误 | P2 | 非整数整机调产IAE被通用handler转为500 | RESOLVED | IAE→400可操作业务文案，MVC及真实API复验通过，409冲突独立 |

## 2026-10-15 排产改版设计审计（正式实现待执行）

| ID | 日期 | 范围 | 级别 | 问题 / 新需求差异 | 状态 | 处置/证据 |
|---|---|---|---|---|---|---|
| PLAN-1015-01 | 2026-10-15 | 两厂需求隔离 | P1 | 单工厂批次加载经营需求时只按版本查询，按物料与来源月聚合，未保留工厂维度；两厂同表前必须修正 | RESOLVED | v4 计算按工厂隔离，统一批次冻结双厂输入；见 2026-10-15 实施报告 |
| PLAN-1015-02 | 2026-10-15 | 月汇总新口径 | P2 | 当前按sourceMonth汇总，新需求要求按实际scheduleDate自然月汇总，来源月仅追溯 | RESOLVED | 新版页面统一使用 rolling-grid 按实际日期自然月汇总；5180/8080 真实浏览器复验。旧 legacy 月汇总接口保留兼容，未修改历史结果 |
| PLAN-1015-03 | 2026-10-15 | 整机联动调日 | P1 | 既有独立单物料转移不能满足整机与对应末级原子联动的新要求 | RESOLVED | LinkedDailyTransferService + RollingPlanningIntegrationTest 覆盖整数、原子提交和守恒 |
| PLAN-1015-04 | 2026-10-15 | 滚动排产新口径 | P1 | 当前整月分配并减7自然日，与上月末业务周＋本月W1/W2的新需求不符 | RESOLVED | v4-rolling-business-weeks 与独立 MySQL 六个月轴测试 |

| PLAN-1015-05 | 2026-10-15 | 排产页面真实运行入口 | P1 | 8080 旧进程及静态资源未更新，且 v2 计划分流旧日/周/月组件，导致新版不可见 | RESOLVED | 统一新版布局、历史只读保护、同步构建产物并重启实际主服务；两个入口浏览器及 API 实测，见 planning-runtime-20261015 证据 |
| PLAN-1015-06 | 2026-10-15 | 旧版多工厂筛选边界 | P2 | 旧规则无整机负荷，工厂下拉取当前分页行，多工厂历史批次可能漏其他页工厂选项 | OPEN | 当前 #86 单厂不受影响；v4 取全厂负荷选项；若需兼容多厂历史数据，补独立工厂列表，见 runtime 独立复核 |

## 2026-10-15 测试交接包诊断

- TESTKIT-BUG-01 / P1 / RESOLVED（2026-10-16）：新增V045及导入/详情查询工厂维度修复。隔离双厂同5SKU确认和计算成功；原失败证据保留。见accuracy-stock批次7及修复独立复核。
- TESTKIT-BUG-02 / P1 / RESOLVED（2026-10-16，限定已定位根因）：用户确认C021，NC同步/新快照只取上海，新批次拒绝混合历史快照；未改pathHash/净需求公式。上海快照下批次9与11计算及独立Decimal核对通过；历史快照33、批次91未改。通用重复边容错不在此结论内。
- TESTKIT-PERF-01 / P2 / OPEN：224100日事实大批summary约4.24秒，日表约6.5秒；rolling-details全量分页持续高开销，中止采集器，服务/计算成功。需SQL/GC和三轮负载。
- 原始证据/复现：test-kit/2026-10-15/已知问题.md、执行结果.md。原失败记录保留，修复后证据见test-kit/2026-10-16/修复完成报告.md。阶段18/19未结项；十万行生成工具OOM不等于应用崩溃，该导入未执行。

## 2026-10-16 已审核修复回归

- 详情权限、密码hash返回、权限目录遗漏/选择、错误映射模板与启用枚举、坏JSON/方法不支持均已定向修复，82项API、7项补充安全断言、权限实际保存浏览器交互通过。该结论不关闭CAP-1014-03工厂数据域。
- TESTKIT-PERF-01继续OPEN：上海大批次217275日事实计算42.432秒成功；bulk HTTP约114.5MB/10.163秒，明细逐页采集仍超预算；主页面summary一次约11.434秒。隔离发布请求约100秒同步等待，待专项性能分析；本轮未优化。
- 正确映射模板下任意非空工厂/仓库值的目录合法性校验、三轮重复checksum、故障恢复/低内存、十万行导入、V020升级与工厂数据域全链路仍待验收。不能以本轮定向修复通过替代全部119项或阶段18/19总门禁。

## 2026-10-16 排产首屏与滚动续修

- TESTKIT-PERF-01继续OPEN，首屏/滚动子问题已定向修复并经独立PASS：摘要与全厂整机负荷改SQL聚合，详情条件下推，新增V046索引；前端日期列视窗、精确数量缓存、日周月复用grid。主库91实测首屏7570ms降至1644/2118ms，120帧滚动中位间隔约123ms降至17ms，完整184日日期轴保留。独立capacity-loads仍5948ms；bulk114.5MB、发布约100秒、全量采集未关闭。
- 工厂映射正确模板合法性缺口已定向解决：新增/更新/导入限制爱培科、永惠，非法工厂预览及提交拒绝且不写入。仓库无主数据目录，继续自由维护，不以不存在的仓库白名单作为未修复项。
- CAP-1014-03工厂数据域保持OPEN；阶段18/19保持REVIEW。主库91/run37/快照33/物料/分类保护指纹未变。报告与证据：`test-kit/2026-10-16/排产性能与续修报告.md`、`evidence/performance-independent-review.md`。后端384项为限定排除回归，前端65项及真实双环境浏览器通过，不替代全部门禁。

## 2026-10-16 批次权限、日分配和守恒续修

- CAP-1014-03整体OPEN，已独立限定PASS的子范围：冻结批次全部工厂授权、列表/count/current过滤、pool/allocation/issue/override间接归属和run归属；38项真实HTTP通过。库存原始snapshot、normalize/balances、导入任务、导出与共享配置的数据域未全链路完成，不将其关闭。
- TESTKIT-PERF-01整体OPEN：capacity-loads从5948ms降至464ms；v4守恒主库三次1539/1472/1407ms、隔离1613/1373/1312ms，修复前后响应相同。隔离217275事实发布从约100秒降至38.523秒，三表业务SHA不变，仍同步且全量物化；capacity-review约8秒、bulk约114.5MB等边界继续保留。
- PLAN-1016-01 / P1 / RESOLVED（独立限定PASS）：直接distribute可绕冻结日历/策略并重复append；新增批次锁/状态/activeRun/冻结版本/已有结果门禁，completeDistribution复用结果。359项限定回归和6项真实HTTP通过，首次不完整日历fixture失败保留。
- PLAN-1016-02 / P1 / RESOLVED（独立限定PASS）：v4守恒只遍历需求组，多出的非零事实/PENDING组漏检；原失败单测与隔离HTTP保留。补丁追加期望0的异常；52项专项及361项限定回归PASS，重启HTTP精确识别0.000001孤立量并发布400且无新版本，正常大批响应、浏览器及17:40:28保护SHA通过。
- 保护核验17:31:38全部PASS：主库91/run37/224100事实/0override/快照33/物料/分类SHA不变。报告：`test-kit/2026-10-16/排产权限与负荷接口续修报告.md`；独立报告：`evidence/continuation-independent-review.md`。阶段18/19保持REVIEW。

## 2026-10-16 明细分页与导入任务归属续修

- 分页正确性原Reviewer限定PASS，但全范围性能FAIL：最终宽投影排序较原采样退化18%–50%，TESTKIT-PERF-01继续OPEN。保留八字段确定性排序；隔离交错COUNT对比没有稳定回退依据，继续定位排序成本。新增未调整批次索引分页子任务IN_PROGRESS，尚未验收。
- 导入暂存任务归属已原Reviewer限定PASS：经营计划/新旧库存三个入口的预览、错误报告、确认均先校验created_by和入口；去重键限当前用户。同文件跨用户独立任务；5专项、367限定回归、29HTTP、2浏览器和18:26主保护SHA通过。见导入任务归属续修报告.md及evidence/import-owner-independent-review.md。CAP-1014-03整体OPEN；并发确认/确认后幂等/缓存恢复不在本补丁范围。

## 2026-10-16 自动明细索引续修

- 未人工调整批次的明细分页已原Reviewer限定PASS：新增V047覆盖索引、先选页id再回表，RR内存在性/COUNT/page保持同快照。55专项、369限定回归、双环境12组完整响应不变、2浏览器、18:40保护SHA通过；主库前三采样页HTTP中位1447/2787/2587ms降至196/229/290ms。证据见排产自动明细索引续修报告.md及details-index-independent-review.md。已有override分页、索引写放大、同步发布/capacity-review/bulk和总门禁仍OPEN。

## 2026-10-16 无覆盖投影与库存快照工厂域续修

- 无override投影已原Reviewer限定PASS：跳过cells/groups身份Map，保持独立列表和精确生效值。75专项、370限定回归、双环境capacity-review完整data一致、2浏览器与保护SHA通过；未证明稳定延迟改善，不宣传定量性能收益。报告无人工调整投影续修报告.md。
- 库存快照列表/详情/balances/HTTP normalize已原Reviewer限定PASS：全部实际来源逐一授权，SQL先过滤后分页，同事务授权读取；受限用户混合/未知/空快照拒绝，后台归一化不依赖HTTP身份。20专项、378限定回归、before-after各17HTTP、3浏览器及19:02保护SHA通过；当前映射、可选header元数据和精确并发注入边界见库存快照工厂域续修报告.md及inventory-scope-independent-review.md。CAP-1014-03整体仍OPEN。
- AUTH-1016-01 / P2 / RESOLVED（限定）：同事务精确清理user_factory_scope后删除用户；380项限定回归、before/after真实HTTP、浏览器删除闭环通过，原Reviewer限定PASS。报告：test-kit/2026-10-16/用户工厂授权删除续修报告.md。并发管理员保护、JWT失效和Redis跨资源一致性未纳入此限定结论。

## 2026-10-16 指纹输入内存续修

- 指纹join临时大对象优化已原Reviewer限定PASS，58专项、382限定回归、双环境完整336项负荷/指纹一致、大表及只读发布预览通过。224100条固定随机事实下线程累计分配减少约41.1MiB，不代表服务峰值堆/稳定延迟改善。见排产指纹内存续修报告.md及checksum-stream-independent-review.md。TESTKIT-PERF-01及阶段18/19保持OPEN/REVIEW。

- 旧Phase1导出任务归属已原Reviewer限定PASS：created_by/当前工厂/任务类型、创建schedule:export及用户选项字段去重，30专项、388限定回归、before-after各18检查、双环境浏览器及19:47保护SHA通过。见导出任务归属续修报告.md和export-owner-independent-review.md。Phase2完整导出、并发状态机和大导出仍OPEN，CAP-1014-03不整体关闭。
- 当前工厂映射/生产日历HTTP工厂域已原Reviewer限定PASS：工厂目录和列表过滤、原记录归属授权、复制双厂授权、混厂提交整份拒绝；49专项、394限定回归、19真实HTTP及双环境浏览器通过。主保护SHA不变，见工厂映射与生产日历工厂域续修报告.md，独立报告evidence/calendar-scope-independent-review.md。CAP-1014-03仍OPEN；审计日志实际只有audit:view，无工厂域过滤，应单独修复，不在此补丁关闭范围。

- 审计列表工厂域补丁REVIEW：55专项、401限定回归、17真实HTTP及双环境浏览器PASS，20:19保护SHA不变。列表/count共用过滤；历史映射/日历从业务键恢复归属，未知与多厂ALL仅ALL域可见；新日志写scope。等待原Reviewer，CAP-1014-03整体OPEN。见审计日志工厂域续修报告.md。

- 审计日志工厂域已原Reviewer限定PASS，Coordinator标DONE，evidence/audit-scope-independent-review.md。仅关闭本补丁范围，CAP-1014-03保持OPEN。

- 2026-10-16 20:37 无override整机capacity-review流式窄投影补丁REVIEW：82专项、409限定回归PASS；双环境重启后完整336项负荷/指纹不变，主中位9347→1587ms、隔离9389→1669ms；排产页面浏览器各1PASS；主库保护SHA不变。见无覆盖产能确认性能续修报告.md，待原Reviewer。仍保留每条checksum字符串并排序，非恒定内存；override路径/发布/bulk及TESTKIT-PERF-01整体OPEN，阶段18/19 REVIEW。

- 无override整机产能确认流式投影原Reviewer限定PASS，Coordinator登记DONE。证据evidence/capacity-projection-independent-review.md；整体性能/阶段门禁不关闭。

- 2026-10-16 库存策略当前HTTP工厂域补丁REVIEW：30专项、416限定回归、17隔离真实HTTP、双环境浏览器及主只读HTTP通过；主保护SHA不变。混厂提交整份拒绝、原记录仓库授权、列表/count先过滤、未知仅ALL、新审计scope。见库存策略工厂域续修报告.md；等待原Reviewer。CAP-1014-03整体OPEN，旧版本配置域/并发时序及性能总门禁未关闭。

- 库存策略原Reviewer FAIL（stock-scope-independent-review.md）：含分隔符仓库导致审计错厂。已改从完整warehouseCode显式写scope，32专项、418限定回归、分隔符before4/after14HTTP、原17HTTP及双环境浏览器PASS，主保护SHA不变；等待原Reviewer复审。原失败证据保留，CAP-1014-03及阶段18/19不关闭。

- 2026-10-16 库存策略审计分隔符修复获原Reviewer限定PASS，Coordinator登记当前库存策略HTTP工厂域DONE。复审报告evidence/stock-scope-delimiter-independent-review.md；32专项、418限定回归、14新增HTTP及原17HTTP/双环境浏览器通过，主保护SHA不变。原FAIL保留，阶段18/19 REVIEW及CAP-1014-03/TESTKIT-PERF-01 OPEN。继续历史生产日历版本HTTP工厂域修复，before13项真实HTTP已留证。

- 2026-10-16 历史生产日历版本HTTP域补丁REVIEW：38专项、428限定回归、25隔离真实HTTP及双环境浏览器各1PASS，临时夹具清理0残留；主只读4HTTP、21:20:58保护SHA不变。报告历史生产日历版本工厂域续修报告.md，等待原Reviewer；CAP-1014-03整体OPEN，阶段18/19 REVIEW。旧映射/库存策略版本域及性能剩余任务未关闭。

- 历史生产日历版本HTTP域获原Reviewer限定PASS，Coordinator登记DONE。独立报告evidence/legacy-calendar-scope-independent-review.md；428限定回归、25隔离HTTP与双环境浏览器及主保护SHA通过。仅关闭此子任务，阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。

- 2026-10-16 库存内部复合键获原Reviewer限定PASS，Coordinator登记DONE。21专项、434限定回归、真实MySQL净需求70/125及安全预留20/5、前后HTTP和双环境浏览器通过；主保护SHA不变。报告库存复合键续修报告.md，独立证据evidence/inventory-key-independent-review.md。仅关闭归一化/安全策略内部键；阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。

- 2026-10-16 稀疏人工调整明细窗口分页REVIEW：74专项、436限定回归、21万事实6选择器各5次完整响应不变；HTTP中位首页1279→372ms、中2372→457ms、尾2907→545ms，双环境浏览器PASS、主保护SHA不变。报告人工调整明细分页性能续修报告.md，待原Reviewer；V>P、发布/bulk及整体安全性能门禁不关闭。原非法跨月同组夹具失败证据保留，按引擎真实分组约束修正后通过。

- 稀疏人工调整明细分页获原Reviewer限定PASS，Coordinator登记该子任务DONE。独立证据evidence/override-window-independent-review.md；阶段18/19 REVIEW、CAP-1014-03与TESTKIT-PERF-01 OPEN。

- 2026-10-16 历史映射/库存策略版本HTTP工厂域补丁REVIEW：43专项、447限定回归、重启后真实HTTP及双环境大表浏览器通过；主91/run37/224100事实/0override/历史快照33保护SHA不变，临时版本清理0残留。库存按全部引用批次的冻结映射授权，未知/无绑定只ALL，历史库存全局写只ALL。报告历史映射与库存策略版本工厂域续修报告.md，等待原Reviewer。原stockVisible括号导致6项FAIL日志保留，修正后全通过；CAP-1014-03/TESTKIT-PERF-01整体OPEN、阶段18/19 REVIEW。

### 2026-10-16 Worker失败CAS续查
- WORKER-FAIL-CAS-01 OPEN：真实隔离MySQL新建invalid-input任务认领v1后异常，失败分支使用固定version0，18秒后仍RUNNING；证据worker-failure-before-runtime.json。计划修改MrpTaskWorkerService、CalcTaskMapper Java/XML及定向测试，失败写使用本次认领version+worker+RUNNING CAS，CAS成功才清理，保留旧active run。编译→定向/限定回归→双后端重启→真实失败任务+浏览器→独立复核。无公式/表结构/状态机变更。
- WORKER-LEASE-RECOVERY-01 OPEN：过期RUNNING且attempt_count<max_attempts无恢复扫描，历史P0-303/P1-404 RESOLVED不足以证明恢复闭环；不得改保留历史task1。完整租约接管/恢复/心跳调度与候选run隔离需另行记录B类决策和专项验收，本补丁不关闭此项。

- Worker失败状态CAS获原Reviewer限定PASS，WORKER-FAIL-CAS-01限定RESOLVED，Coordinator登记子项DONE。11定向、452限定回归、真实FAILED/v2和双浏览器及主保护SHA通过。隔离三轮217275日事实计算均与冻结基准业务SHA一致，35.317–43.820秒，Java峰值RSS约2.10GiB；三轮证据待补独立复核。WORKER-LEASE-RECOVERY-01及整体安全/性能OPEN，阶段18/19 REVIEW。继续全量INSERT分块专项。报告任务失败CAS与大数据重复计算续修报告.md。

- 三轮真实大数据重复计算获原Reviewer限定PASS，见test-kit/2026-10-16/evidence/repeated-calculation-independent-review.md。仅证明冻结输入重复性、所检查不变量与汇总总量；旧清理未逐表确认，主保护早于三轮结束，低内存/独立逐格准确性不在结论中。阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。


### 2026-10-16 续修：计算SQL分块 REVIEW / 库存风险 IN_PROGRESS
计算五处1000行写入保留原事务，7项MySQL专项与459限定回归通过；三轮217275日事实全字段SHA、所检不变量、13表精确清理通过。主浏览器PASS；隔离两次初始加载超时保留，原门限下空闲重验PASS（ready2369ms、grid1168ms、120帧无longtask）。并发计算/清理时页面延迟仍OPEN。详见test-kit/2026-10-16/计算写入分块续修报告.md，等待独立Review。库存风险修复结构化键、重复全池扫描、全量INSERT；修复前5项中3失败，修复后7专项PASS，后续回归/运行验证进行中。主库91/run37/33及隔离10/13只读；阶段18/19 REVIEW与整体问题OPEN不变。

- 计算SQL五处1000行分块获原Reviewer限定PASS，Coordinator登记DONE；evidence/calculation-chunk-independent-review.md。459限定回归、三轮217275事实、13表清理和23:08:53最终主保护通过。并发加载超时、完整物化/长事务/低内存及整体阶段门禁仍开放。

- 库存风险生成子项REVIEW：结构化键/单次池索引/1000行写入；7专项、465限定回归、双后端真实重启、217275事实与1502风险HTTP核对、双浏览器及23:19:53主保护PASS，夹具13表0残留，等待原Reviewer。见库存风险生成续修报告.md。整体安全/性能OPEN、阶段18/19 REVIEW不变。

- 库存风险生成子项获原Reviewer限定PASS，Coordinator登记DONE；证据test-kit/2026-10-16/evidence/stock-risk-independent-review.md。仅关闭结构化键、单次池索引及分块写入；7专项、465限定回归和真实运行/保护通过。大数据阈值、有库存池、风险抽屉UAT及整体安全性能仍OPEN，阶段18/19 REVIEW。

- 2026-10-17 高密度人工调整明细分页子项REVIEW：动态实际权重排序保留PAD SPACE语义，26专项、472限定回归、双后端重启、6种筛选各5次真实HTTP全响应等价、双环境浏览器及00:34:41最终主保护PASS；临时33/run27六表0残留，隔离来源13事实SHA不变。HTTP首页/中/尾中位2278/3176/3157→1221/1373/1440ms。报告test-kit/2026-10-16/高密度人工调整明细分页续修报告.md；等待原Reviewer。MySQL升级须复跑WEIGHT_STRING等价/长权重测试；阶段18/19 REVIEW，CAP-1014-03与TESTKIT-PERF-01整体OPEN。

- 高密度人工调整明细分页获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/dense-override-independent-review.md。26专项、472限定回归、完整HTTP等价/双浏览器/清理及最终主保护通过。隔离浏览器初载10.4秒及WEIGHT_STRING升级复验风险保留，阶段18/19 REVIEW、CAP-1014-03与TESTKIT-PERF-01整体OPEN。

- 2026-10-17 Worker心跳调度子项REVIEW：独立单线程续租，默认计算并发不变；12专项、473限定回归、双后端真实重启、34秒隔离阻塞计算租约增长、双浏览器与00:52:23主保护PASS，六表0残留。报告test-kit/2026-10-16/Worker心跳调度续修报告.md。等待原Reviewer；完整WORKER-LEASE-RECOVERY-01、失败清理批次状态、CAP/PERF整体OPEN及阶段18/19 REVIEW不变。

- Worker心跳调度获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/worker-heartbeat-independent-review.md。只关闭同步计算阻塞调度根因，完整恢复/fencing、阶段18/19 REVIEW及CAP/PERF整体OPEN不变。继续失败清理批次状态保护，原before复现证据保留。

- 2026-10-17 Worker失败批次状态保护子项REVIEW：16专项/477限定回归、双重启、8例真实Worker/active事实/六表0残留、双环境浏览器及01:09:55主保护通过。Chrome启动首败保留，原配置重验PASS。报告test-kit/2026-10-16/Worker失败批次状态保护续修报告.md，待原Reviewer；读取前后继claim和删除/activation竞态仍OPEN，CAP/PERF整体OPEN、阶段18/19 REVIEW。

- Worker失败批次状态保护获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/worker-batch-state-independent-review.md。16专项/477限定回归/真实Worker与双浏览器/主保护通过；读取前后继claim和删除/activation竞态仍OPEN，阶段18/19 REVIEW，CAP/PERF整体OPEN。

- 2026-10-17 导入确认幂等/回滚子项REVIEW：30专项、498限定回归、双重启health、33隔离真实确认HTTP、双环境排产浏览器及01:25:32最终主保护PASS；临时数据精确清理，主91/run37/224100事实/0override/历史快照33不变。报告test-kit/2026-10-16/导入确认幂等与回滚续修报告.md，待原Reviewer。只限同一任务确认；跨任务同文件、forecast不同任务versionNo并发、upload连接级LAST_INSERT_ID、staging重启恢复仍OPEN。阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 导入确认幂等/回滚获原Reviewer限定PASS，Coordinator登记同一任务确认子项DONE；evidence/import-confirmation-independent-review.md。30专项/498限定回归/33真实HTTP/双浏览器及主保护通过；跨任务/versionNo/upload连接级ID/缓存恢复仍OPEN，阶段18/19 REVIEW，CAP/PERF整体OPEN。

- 2026-10-17 上传任务身份/首次并发子项REVIEW：ImportTaskCreation按唯一requestKey读取真实任务，避免连接局部LAST_INSERT_ID，唯一键并发只复用匹配owner/type/checksum/key的胜出任务；三upload替换原重建。36专项、504限定回归、42有效上传/18确认HTTP、双重启/双浏览器及01:41:54主保护PASS，临时0残留。报告test-kit/2026-10-16/上传任务身份与并发续修报告.md，待原Reviewer。文件同路径并发、跨任务、版本号并发、重启缓存/大导入仍OPEN；阶段18/19 REVIEW，CAP/PERF整体OPEN。

- 上传任务身份/首次并发获原Reviewer限定PASS，Coordinator登记子项DONE；evidence/import-upload-identity-independent-review.md。36专项/504限定回归/42上传18确认及双浏览器/主保护通过。后续一万行真实forecast预览202/10000成功、确认500暴露源spec_model256到forecast platform128截断，失败已回滚/精确清理且隔离保护通过，进入独立字段容量修复；阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 2026-10-17 经营计划规格字段容量子项REVIEW：新增V048将platform128扩至256，2项before真实MySQL过长失败、38专项/506限定回归PASS；双重启48迁移及原47checksum/平台值保护通过。四组真实大导入10000/100000库存与60000/100002经营计划月份明细逐行/总量PASS，峰值RSS442.4MiB，无重启；临时正式行清理及隔离保护通过。双浏览器和02:03:41主保护PASS。旧local测试4条夹具精确清理后主经营计划SHA恢复，另开测试隔离修复。阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 规格字段V048及四组真实大导入分别获原Reviewer限定PASS，Coordinator登记两个子项DONE，evidence/forecast-platform-independent-review.md与large-import-independent-review.md。仅字段完整容量和本次四规模精确导入；同步慢/N+1、任意低内存/重启恢复、新输入完整排产及整体阶段18/19/CAP/PERF仍OPEN。旧local测试隔离72专项PASS，最终限定回归进行中。

- 2026-10-17 旧测试MySQL隔离子项REVIEW：10类local测试继承现有BaseIntegrationTest，72专项与506限定回归PASS；两库forecast行数/SHA不再变化、health200/UP、双浏览器和最终主保护PASS。报告旧测试数据库隔离续修报告.md；原local副作用4版本/4旧计划/1终态任务已精确清理且原SHA恢复，失败/清理证据保留。仅测试代码；阶段18/19 REVIEW、CAP/PERF整体OPEN。

- 2026-10-17 旧测试MySQL隔离子项获原Reviewer限定PASS，Coordinator登记DONE，见evidence/legacy-isolation-independent-review.md；仅关闭10类local测试数据库隔离，MaterialApiTest、Redis共享、其他local测试和并行全局隔离保持OPEN。

### 2026-10-19 实际计算进度补充修复


- PLANNING-PROGRESS-1019 REVIEW：主环境 #96 进度接口因 SELECT * 将大JSON带入排序触发 Out of sort memory，前端显示重试并从0跳100；原阶段通知粒度粗。改为窄字段查询、独立短事务、每阶段最多每秒一次的实际完成数量通知，前端保留两秒轮询且完成后不显示重试。35专项、548后端完整回归、74前端、双服务重启、真实浏览器38,850/219,450日事实及四表精确等价通过；报告 `.harness/reports/2026-10-19-actual-calculation-progress-report.md`。等待独立阶段复核；既有整体安全、性能、完整租约恢复等开放问题保持不变。
- 2026-10-19 发布/修订后台化子项 REVIEW：同步 HTTP 长时间等待已改为 calc_task 持久任务；真实隔离 #60→#61→#62、事实 3048/3108/38850 与守恒核验通过，后端 552/552、前端 76/76。待独立复核长事务、并发与进程崩溃恢复；现有阶段18/19及性能整体开放问题保持。报告 `.harness/reports/2026-10-19-planning-lifecycle-background-report.md`。

### 2026-10-20 排产异常提示修复

- MRP-MISSING-1020 / P1 / REVIEW：批次 #104 缺角色优先返回，且把仅在 BOM 存在的 SO-VX00-99 错报为“分类角色未维护”；同节点缺库存提示被遮蔽。现区分 MATERIAL_MISSING/ROLE_MISSING 并同时提示缺库存，预检去重计数。17 条真实异常和浏览器展示通过，待独立 Reviewer；见 2026-10-20-missing-input-issues-report.md。

### 2026-10-21 排产计算反复回到待计算

- MRP-CALC-1021 / P1 / REVIEW：主库批次 #106 的计算任务 #169–#177 均失败，Worker 清理后将批次复位 READY_TO_CALCULATE；旧 Worker 报 BOM snapshot not found: null，新 Worker 在保存同键末级需求时遇唯一键冲突。已在写入前按数据库唯一键汇总多 BOM 路径数量，并让工作台在复位状态下读取和显示最新任务错误。编译、单元测试及真实浏览器错误提示通过；待停用旧 Worker 并在用户明确操作或隔离环境中验证新的整批计算成功，不能把页面文案修复当作已完成排产。
- 2026-10-21 重启与主库复核：停用 8080/8081/8280/8380/8381 的旧主库实例，保留 8180 隔离测试服务；用当前代码重启 8080。批次 #106 新任务 #178 已 SUCCEEDED，run #58 COMPLETED，批次转 WAITING_DECISION；存在 24 条 CALENDAR_INCOMPLETE 待分配，合计 5167，均定位 2026-09-21。页面与 API 已验收，当前阻断属于生产日历业务配置，未自动填补或发布。

### 2026-10-23 库存分配独立复核续修

- INVENTORY-ALLOCATION-1023 / P1 / RESOLVED：原独立 Reviewer FAIL 指出三个绕过页面的写入入口、客户端审计身份和批次并发锁问题；删除入口、改用登录身份、统一批次行锁并补真实 MySQL 并发测试后，原 Reviewer 二次独立复核 PASS。编译、后端全量 607 测试、前端 100 测试及真实页面 450→400→0→450 闭环通过。批次 117 已作废保留 run 69；批次 118 的 19 条缺库存已确认、任务 195 和 run 70 计算完成但未发布，尚有 5 条安全库存及 20 条库存策略风险待业务处理。详见 .harness/reports/2026-10-23-inventory-allocation-continuation.md。

### 2026-10-29 双工作台物料重算续修

- SPLIT-PRODUCT-1102 / P2 / REVIEW：双工作台 A/B/C 产品优化及独立修复复核 PASS；历史缺料汇总、物控待办/任务进度、同筛选物料汇总导出及跨页定位完成。导出分页快照、无池已知风险、大数精度、切批晚到请求和确认响应已修复。后端 649/649、前端 107/107、5180 浏览器 9/9、8180 重启后实际导出与 23 条列表守恒。专用业务批次浏览器写入演练、外部同步核验及整体阶段复核仍待完成；不把 Testcontainers 双岗发布用例等同 UAT。另有同批次异步操作中普通刷新可能提前清除 `saving` 的后续边界。见 `.harness/reports/2026-11-02-two-workbench-product-optimization.md`。

- SPLIT-REFRESH-1102 / P2 / OPEN：同一批次的确认、反馈或开始物料分析请求尚未返回时，普通 `load()` 刷新会提前清除 `saving`，按钮可能短暂重新启用；原操作晚到后因加载代次变化被忽略，页面需再次刷新才能看到结果。后续以独立 action token 管理操作占用，补同批次刷新与操作交错测试。跨批次晚到响应已通过本轮独立复核，不与本项混淆。

- SPLIT-RECALC-1029 / P1 / REVIEW：整机确认后物料分析沿同一 active run 进行；库存分配更改使旧物料结果失效，物控可提交重算。重算前拒绝静默丢弃已处理的人工日调整、待分配、默认下钻、库存风险和异常决策；真实 MySQL 成功/失败测试分别验证同事务替换物料事实与失败恢复旧事实，整机事实不变。整机与后代同码按 BOM 层级识别，保留整机占用量；跨仓风险按库存池实际分配量计算。续轮新增真实 MySQL 分配→失效→Worker→同 run 替换→双确认门禁测试 1/1 通过，串行全量后端 636/636 通过。旧 5181 前端代理 8081，5182 才指向隔离 8180；8180 健康检查本轮返回 503，未登录批次 API 401。仍待专用 v5 业务批次的真实引擎、日分配、发布联调及独立复核。见 .harness/reports/2026-10-29-split-planning-shortage-recalculation-report.md。
- SPLIT-LEASE-1029 / P1 / REVIEW：独立 Reviewer 首轮 FAIL：过期 RUNNING 且未达上限的物料任务不会重排，达到上限后批次仍卡 CALCULATING。新增版本与租约 CAS 重排、三次失败后同 run 物料批次复位及真实 MySQL 独立事务测试 2/2；全量后端 638/638 PASS。隔离 8180 已重启；健康检查 DOWN 的直接等待点为 Oracle 数据源连接池，Oracle 协议握手慢/卡住，仍待健康门禁与真实 v5 发布闭环验收。

### 2026-11-02 经营计划／库存到排产端到端验收

- IMPORT-BOUNDARY-1102 / P1 / REVIEW：本轮隔离 API 8182/测试库 3317 发现经营计划与库存只有表头时预览 `errorRows=0`，库存未知料号、超 6 位小数及超 12 位整数也预览放行，确认时存在半数据/500 风险。已补 `NO_DATA`、`MATERIAL_NOT_FOUND`、`INVALID_QTY` 预览错误并保留确认事务兜底；旧失败证据为 `test-kit/2026-10-15/evidence/runs/2026-11-02-import-boundary-155620/` 和 `...-161033/`，修复后 13/13 异常子场景预览报错、确认 400、四张正式表行数不变（`...-161230/result.json`）；44 项相关后端测试、107 项前端单测、5 项隔离浏览器通过。待后端全量及独立复核，阶段 18/19 保持 REVIEW。
- IMPORT-FORMULA-1102 / P1 / REVIEW：经营计划数量公式无缓存值时，SAX 解析器此前按空值 0 预览放行；库存公式虽阻断但原始值丢失。现两者均读取公式原文并报 `INVALID_QTY`，公式料号亦阻断；失败现场 `test-kit/2026-10-15/evidence/runs/2026-11-02-import-matrix-162609/result.json`，修复后 13/13 边界子场景预览报错且确认 400、四表数量不变，证据 `...-170517/result.json`。后端修复后相关专项通过、全量重跑中；前端 107/107、隔离导入浏览器 5/5。完整 42 场景及独立复核未完成，阶段 18/19 保持 REVIEW。
- IMPORT-STALE-1102 / P1 / REVIEW：原失败保留：隔离页面导入 F2/V31 和 I2/快照 37 后，旧 batch 74 仍冻结 V30/快照 36 且状态 MATERIAL_REVIEW；新建批次 HTTP 409 `ACTIVE_BATCH_EXISTS`。修复导入/草稿发布对所有非当前经营计划且未终结批次的失效，`selectCurrent` 跳过 STALE，批次状态写入不再复活 STALE；前端失效历史态显示原因并只读。后端 661/661、前端 107/107、页面导入 1/1、工作台 3/3、历史页 1/1。隔离 V32/快照 38 导入后 batch 74 正式变 STALE，batch 75 建立/预检/计算成功；草稿冲突 409 且无半版本，F11/B18 场景自测 PASS。前后证据 `test-kit/2026-10-15/evidence/runs/current-ui-import-20261102171045748062/`、`.../2026-11-02-stale-recovery/result.json`。待独立复核，阶段 18/19 继续 REVIEW。
- E2E-IMPORT-PLANNING-1102 / P1 / OPEN：42 条场景中 F01/F02/F05/F11/B01/B10/B11/B13/B14/B18 自测 PASS，其余 32 条有未覆盖断言，整体 FAIL。V35/快照 41/batch 78/run 71 双岗链验证物控任务、两厂三种原材料各 10 和角色隔离；强化复跑 V37/快照 43/batch 79/run 72 逐项断言预览前后正式版本、两厂六个月、双仓 GOOD 库存以及批次冻结输入和范围，证据 `test-kit/2026-10-15/evidence/runs/2026-11-02-bom-chain-20261103100337957612/result.json`。F02 混合文件错误预览、禁确认、报告末列和正式数据不变已闭环；I01 缺不良品仓、I02 仅混合文件子项已验。库存/日历/并发/故障/性能及其他页面边界尚未闭环。旧 10 月源表基线不可比；本轮可比窗口七张源表行数和 SHA-256 不变。执行记录见 `docs/经营计划与库存导入至整机排产端到端执行记录-2026-11-02.md`。
- E2E-ROLE-SCOPE-1102 / P1 / REVIEW：B14 原 FAIL 现场保留。用户确认 PLANNER/MATERIAL 默认可看全部工厂；新增 V064 对尚无自定义工厂域的两角色播种 ALL，隔离库 V063→V064 成功；登录入口按页面权限选择。独立双岗账号在 batch 78 复测未计算/待确认提示、排产员确认、物控本人提交任务、原材料展开和越岗 403，浏览器 1/1；动态当前批次权限用例 1/1、临时账号清理。前端构建及最终单测 109/109，通过证据见 `.../2026-11-02-bom-chain-20261103093807076238/result.json`。B14 整条自测 PASS，仍待独立复核；阶段 18/19 继续 REVIEW。
- 2026-11-03 库存预览后仓库品质变化缺陷 I03：隔离 API 实测合法预览后将 `UAT_APK` 良品仓改为不良品，旧 `confirmItem` 仍返回 200 并生成快照 52。根因是确认时重新推导品质但只检查校验错误，没有对比预览时赋予的品质。现按预览/确认逐行比对仓库编码与品质，变化返回 400 并要求重新上传；新增 Testcontainers 用例。首轮 FAIL 证据 `test-kit/2026-10-15/evidence/runs/2026-11-03-warehouse-guards-105012/result.json`；修复后 5 个仓库门禁场景、正式表零增量、映射恢复与主库七表前后 SHA 一致，证据 `.../2026-11-03-warehouse-guards-110934/result.json`。后端编译及 663/663 全量测试 PASS，隔离 API 8182/前端 5192 HTTP 200。I03 自测 PASS，独立复核与整体 42 项门禁仍开放。
- E2E-FRACTIONAL-MACHINE-1103 / P1 / REVIEW：经营计划原允许 1.123456 台预览，排产 ProductionCapacityRules.machineDemands 要求整机净量为整数，造成导入成功后计算失败。现在上传预览报原行列 INVALID_QTY、确认 400；Testcontainers 专项和隔离 API 复测通过。首次全量因旧元数据容量夹具小数台 2 项失败，修正夹具后 667/667 PASS。证据 test-kit/2026-10-15/evidence/runs/2026-11-03-quantity-semantics-123214/result.json。F07 其他断言和独立复核仍开放。
- E2E-MATERIAL-CELL-1103 / P1 / REVIEW：两类导入的数值型料号可能失真，解析器现按单元格类型拒绝并保留原始值；文本前导零料号正式入版保真。隔离负例 8/8、正式四表零增量；正例经营计划六个月和库存六位小数入版，主库七张受保护源表 SHA 不变。证据 test-kit/2026-10-15/evidence/runs/2026-11-03-material-cell-123224/result.json 和 2026-11-03-leading-zero-115346/result.json。F03 自测 PASS；I07 其余断言及独立复核仍开放。
- 2026-11-03 端到端增量状态：补齐 F07/F08/I07 后 20/42 自测 PASS、22 项未关闭；隔离数量矩阵 16/16、浏览器导入 10/10，前端 build/109 单测、后端 667 单测通过。8180 本地主后端已重启并只读验证 5180；测试写入仍只走 8182/3317。F07/F08/I07 待独立复核，阶段 18/19 保持 REVIEW。
- 2026-11-03 F04/F06 增量状态：真实日期表头跨年六个月生成版本 43、正式明细 6 条；停用工厂 INVALID_FACTORY、确认 400、配置恢复，正式四表零增量，主库受保护源表前后 SHA 一致。首次停用脚本 URL 编码错误未改配置，失败记录保留。证据 2026-11-03-date-header-125115/result.json、2026-11-03-disabled-factory-125416/result.json。累计 22/42 自测 PASS、20 项未关闭，待独立复核。
- 2026-11-03 F09/F10/I08 增量状态：隔离两类导入同 task 并发确认仅各一条正式资源、同字节幂等，另一管理员读暂存/错误报告/确认均 403；预览后重启 8182，过期明确，重传同字节恢复原 task 并只新增正式版本 45/快照 58。Testcontainers 已覆盖事务故障注入回滚与重试。证据 2026-11-03-import-concurrency-125823/result.json、2026-11-03-import-restart-130014/result.json。累计 25/42 自测 PASS、17 项未关闭，独立复核尚未完成。
- 2026-11-03 I06/I09/F12/I10 增量状态：I06 首轮部分仓库范围检查在归一化后才执行，误报通用 `MRP_ILLEGAL_STATE`；现入口先校验范围，隔离 API 三种拒绝 3/3、浏览器缺仓禁确认及范围外仓库提示通过。I09 旧快照 47 的批次 96 净 703，新快照 63 的批次 97 净 0，旧 run 不变，两批取消且源表指纹不变。两类历史页分页、搜索、空态和 HTTP 500 后恢复的 Playwright 2/2 通过。首次失败和修复证据见执行记录；累计 29/42 自测 PASS、13 项未关闭，阶段 18/19 继续 REVIEW。
- 2026-11-03 B20 增量状态：版本信息抽屉原缺输入校验码，已补完整 64 位值与换行；隔离已发布 #73/失效草稿 #75 对照 API 核验，Playwright 3/3、截图人工检查通过。主后端 8180 重启并只读健康检查，正式前端 5180 工作台 3/3。累计 30/42 自测 PASS、12 项未关闭，阶段 18/19 继续 REVIEW。
- 2026-11-03 B19 增量状态：隔离历史 #73 的页面分页后整机行变化、末月 2027-04 定位、网格 500 后提示和重试恢复通过，结合此前两视口负荷格与搜索空态证据，B19 自测 PASS。累计 31/42 自测 PASS、11 项未关闭，阶段 18/19 继续 REVIEW。
- 2026-11-03 测试矩阵复核：F12/I10 的历史列表分页、空态、500 恢复仅为子项，尚未验历史数据切换/跨月及仓库物料检索/401/403。两项恢复 NOT_RUN，修正累计为 29/42 自测 PASS、13 项未关闭。此前局部证据保留，后续补完整断言再关闭。
- E2E-INVENTORY-SEARCH-1103 / P2 / REVIEW：I10 实测库存详情仅过滤当前 20 行，后页料号无法找到。现由后端先校验整份快照工厂权限，再以 MyBatis XML 对仓库/料号/名称/供应商服务端检索和计数，前端忽略晚到响应。快照 63 后页料号命中 2 行、仓库命中 82 行；历史切换、空态、500 恢复、列表与详情真实 401/403、页面提示通过。Testcontainers 专项 8/8、后端全量 668/668、前端 109/109、隔离 Playwright 4/4；首轮脚本定位失败证据保留。F12/I10 自测 PASS，累计 31/42，整体阶段 18/19 仍 REVIEW。
- E2E-BATCH-QUALITY-GATE-1103 / P1 / REVIEW：B02 发现排产弹窗忽略仓库 `qualityConfigured`，后端拒绝未确认品质，前端却允许点击确认。现在弹窗直接列出待维护仓库并禁确认；前端 110/110、隔离 5192 浏览器注入该状态与正式 5180 正常弹窗复核通过。隔离 API 8 种无效建批请求均 400，无新增批次；B02 其余门禁尚未完成，保持 NOT_RUN。
- E2E-CALENDAR-GATES-1103 / P0 / REVIEW：B03 的缺日历、跨月前周、显式休息、整周停产与缺前月业务周均在隔离 API/浏览器/Worker 验证。缺日历预检 400 且无任务；整周停产保留 200 台待分配、守恒差异 0。测试批次均取消，配置原值恢复；首轮测试脚本误读字段的失败证据保留。B03 自测 PASS，累计 32/42；整体门禁与独立复核仍开放。
- E2E-MISSING-ROOT-INVENTORY-1103 / P0 / REVIEW：B04 首轮隔离 #106 只有爱培科不良品行，物料阶段却无 `INVENTORY_MISSING`，根因是库存记录集合把不良品也算入且整机根节点在物料阶段跳过缺行问题。修复后 #107 保留候选整机排产，物料阶段生成缺行待办并在未解决时拒绝确认；#108 显式 0 良品行不误报。#109 分类角色停用则整机预检 `WAITING_DECISION`、计算拒绝、处理后恢复；三批取消、隔离角色恢复。原失败与成功证据 `test-kit/2026-10-15/evidence/runs/b04-missing-root-1793691457053/result.json`、`b04-missing-root-1793692145720/result.json`、`b04-role-gate-1793692299055/result.json`。B04 自测 PASS，累计 33/42；整体阶段 18/19 仍 REVIEW。
- E2E-BATCH-SOURCE-GATES-1103 / P1 / REVIEW：B02 8 类无效入参、缺库存版本/仓库品质、未发布角色与前月业务周已阻断；新增无所选工厂需求、无当前上海 BOM、真实停用工厂仓库映射的隔离 API 拒绝，浏览器无 BOM/缺映射提示与禁确认 2/2。清空后恢复的 145801 条 BOM 与原 checksum 一致，永惠映射恢复原值，测试批次 #110 取消，无活动测试批次。证据 `test-kit/2026-10-15/evidence/runs/b02-source-gates-1793692635136/result.json`、`b02-mapping-gate-1793693324239/result.json`。后端最新全量 669/669、前端 110/110；B02 自测 PASS，累计 34/42，独立复核与其余 8 项仍开放。
- E2E-REVIEW-IDEMPOTENCY-1103 / P1 / REVIEW：B16 首轮 #112 双并发确认均 200、数据库重复两条相同确认；同 run/指纹重复提交现幂等返回。隔离两次独立登录 #114 重验双预检/计算只各放行一次、唯一任务/run、双确认仅一条记录，取消后无活动批次。原 FAIL 与复测证据 `b16-concurrency-1793693617060/result.json`、`b16-concurrency-1793693921170/result.json`；Testcontainers 真实引擎再验整机与物料写入后故障、同批次重试，按唯一输入校验码只有 1 个发布版本。最新全量 669/669、正式 5180 只读浏览器 2/2；B16 自测 PASS，整体阶段和独立复核仍 REVIEW。
- E2E-BOM-FREEZE-1103 / P1 / RESOLVED：B15 当前 BOM 单行变化后隔离 #116 预检 400、无 run 且冻结 checksum 未变；恢复后预检可计算并取消。补隔离 #129/#130 验证日历产能与仓库优先级旧批次冻结、新批次生效且配置恢复；经营计划变化由 F11 覆盖，Testcontainers 发布版历史修订保留原版 checksum/run/事实。证据 `b15-bom-checksum-1793694352117/result.json`、`2026-11-03-b15-config-191404/result.json` 和 `TwoWorkbenchRealEngineFlowIntegrationTest`；B15 自测及独立复核 PASS。
- E2E-WORKER-FAULT-1103 / P1 / RESOLVED：B05/B17 早期隔离故障注入先后遇到 Worker 抢先认领和 MySQL binlog 权限限制，均非产品功能结论；失败 trace 保留、批次取消。`TwoWorkbenchRealEngineFlowIntegrationTest` 用真实 MySQL/引擎验证整机及物料日分配后抛错、回滚和同批次重试。隔离 #133 真实 Worker FAILED、页面显示原因并点击重试成功；#141 真实 RUNNING 的任务号、76% 阶段和刷新一致性由 5192 页面与任务 API 同时验证，B05 Playwright 2/2。隔离 #131 运行中 JVM 强杀后仅人工推进该任务租约并二次认领成功，#134 约 4 秒 Redis 短断期间任务完成；不声称自然租约时间精度或长期 Redis 故障。证据见端到端执行记录 B05/B17；两项自测及独立复核 PASS。
- E2E-SHARED-STOCK-1103 / P1 / REVIEW：B07 隔离正式导入的两版经营计划只交换整机物理行顺序，子件 11520471 共享良品 15 件；两版真实 Worker 计算及物料分析分别得到 10/5、5/10 分配，逐行守恒且总量不超 15。B08 启用测试次良品仓优先级 2，主仓 7 件先用、次仓 8 件后用，不良品仓不入池；整机负荷接口仅爱培科整机 20 台。批次 #119/#120/#122 已取消，仓库映射恢复，主库受保护源表前后指纹一致；首轮测试夹具/断言错误及成功证据均保留在 `2026-11-03-b07-shared-*`、`2026-11-03-b08-multiwarehouse-*`。累计 37/42 自测 PASS；B05/B09/B12/B15/B17 与独立复核开放。
- E2E-LINKED-TRANSFER-1103 / P1 / REVIEW：B12 隔离并发旧预览曾因转出量已不足返回 400，根因在提交时先校验余量、后比对预览指纹。`LinkedDailyTransferService` 改为提交先重建当前指纹并比对，普通预览仍直接校验余量；真实 MySQL 新增旧全量预览重复提交断言。编译、定向、全量 670/670 PASS；隔离 API batch #124 预览无写、休息日 400、并发 200/409、守恒 0、锁定日 400，正式取消；5192 Playwright 页面预览取消与双击只写一次 1/1 PASS。首轮 400 与页面测试夹具字段误读证据保留。累计 38/42 自测 PASS；B05/B09/B15/B17 与独立复核开放。
- E2E-WEEK-BORDER-1103 / P0 / REVIEW：B09 隔离正式导入 V52，batch #128/run #110 的真实 Worker 将 2026-11 五周 1003 台分为 203/200/200/200/200，将 2027-02 四周 1003 台分为 253/250/250/250；两个来源月日事实各 1003、守恒 0。闰年 2028-02 跨月由新专项断言覆盖，整周停产的 200 台待分配沿 B03 真实批次证据。批次取消、源表指纹未变，最终证据 `2026-11-03-b09-weeks-190549/result.json`；后端全量 671/671 PASS。累计 39/42 自测 PASS；B05/B15/B17 与独立复核开放。
- E2E-FINAL-SELFTEST-1103 / RESOLVED：本轮 42/42 场景自测及独立复核 PASS。B05 隔离 #141 真实 RUNNING 任务 #117 在 5192 页面显示 76%、保存日排产（0/32）和任务号，刷新同任务；隔离结果表短锁自动释放、任务成功、唯一 run，失败重试组合 Playwright 2/2。B15 当前配置冻结与 Testcontainers 历史修订、B17 JVM 运行中中断恢复及约 4 秒 Redis 短断维持 PASS，边界见执行记录。隔离活动批次 0、Redis 已恢复、七张受保护源表与本轮 11-02 后测指纹不变；本次后端编译与全量 671/671、前端 build/110/110。阶段 18/19 全部门禁仍为 REVIEW，本项解决不等于项目最终验收。
- DOCKER-API-WORKER-SCOPE-1104 / P1 / OPEN：`MrpApiApplication` 启用调度，MRP/BOM Worker 轮询类未限定 worker profile；隔离环境证明 Worker 在 API 停止时可认领任务，但未证明双进程并行时 API 不会消费。应限定任务轮询到 Worker，并覆盖 API/Worker 双进程队列与故障恢复回归。阶段 19 保持 REVIEW。
- DOCKER-PROD-REDIS-CREDENTIAL-1104 / P1 / OPEN：隔离 `prod` 启动实测空口令 Redis 导致 Redisson AUTH 失败，隔离 Compose 已配置临时 Redis 口令并复测通过。正式 `deploy/docker-compose.yml` 仍未接入 `MRP_REDIS_PASSWORD`，不能宣称正式 Compose 可运行；需补凭据接线与独立环境重验，不触碰现有正式卷。
- DOCKER-API-WORKER-SCOPE-1104 / RESOLVED_ISOLATED：双进程首次探针被 API 认领，定位为 `MrpWorkerApplication` 作为同包配置类再次启用调度。两个入口均移除注解后，调度按 `worker | !prod` profile 启用；隔离双进程探针 #3 由 Worker hostname `cf5b0bb926ca` 认领，API 未抢占。最新编译、673/673 全量测试、镜像构建与健康检查通过；正式部署未重启，阶段19仍 REVIEW。
- DOCKER-PROD-REDIS-CREDENTIAL-1104 / RESOLVED_CONFIG_ONLY：正式 Compose 已将必填 `MRP_REDIS_PASSWORD` 配到 Redis `requirepass`、健康检查和 API/Worker 环境，`docker compose config --quiet` 通过。仅作静态配置与隔离同机制运行验证，未对现有正式 Redis/卷执行重建；正式部署运行门禁仍 OPEN。
- TESTKIT-PERF-01 / 2026-11-05 限定进展：独立测试库既有 #13/run #13 的 9,174 条逐层需求守恒和负量 0 异常；8,916 个末级需求键对终点、17,382 个终点键对 217,275 条日事实均 0 差异，#10/#13 输入及结果 checksum 相同。仅存量数据逐键复核 PASS，未执行本轮代码下的 456600 日键新计算，也未测每请求 SQL、峰值 CPU/GC、低堆与故障恢复；总项保持 OPEN，阶段 18/19 保持 REVIEW。
