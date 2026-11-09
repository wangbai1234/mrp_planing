# 会话交接与上下文恢复

## 2026-11-09 阶段 18/19 验收流程压缩（仅文档，未增加通过证据）

- 用户要求从独立空库起算，在环境和版本化夹具就绪、无阻断缺陷时把完整功能覆盖控制在 5 小时。现行执行单为 `.harness/reports/stage18-19-five-hour-acceptance.md`：300 分钟覆盖环境/迁移、一次全量构建测试、导入配置、独立计算 oracle、角色配置与工厂域、浏览器、合法大负载/恢复/兼容及独立复核。119 项原 CSV 保留，逐 ID 映射证据或缺口；不再默认逐条重跑或每次微改后重复全量。
- 本次只更新验收文档，不运行新的业务测试，也不改变原 15 PASS、1 FAIL、103 NOT_RUN 及阶段 18/19 `REVIEW`。CALC-002 原混合组织失败、PERF-003 不合法旧夹具、P2-I002 正式凭据轮换和 ADMIN-003 全接口授权仍为阻断。正式资源只读，现有隔离 #2 和失败证据保留。

## 2026-11-09 阶段 18/19 当前 BOM 大负载续验

- 11-09 续验：主机重启使上一轮 `/tmp/mrp-frontier-fulltest-20261109.log` 及其退出码丢失；新独立 schema `mrp_gate_frontier_resume_20261109`/Redis 6391 的 Surefire 最终 100 套件/724 项、0 失败错误跳过，但 zsh 启动包装误写只读变量 `status`，导致 Maven 原始退出码丢失，本轮全量总门禁不计 PASS。`./mvnw package -DskipTests -q` 后续退出 0、前端 `npm run build` 退出 0。正式 5180 当前未运行，未启动或写入。
- 新 jar 在独立测试库/Redis 6391 的临时 8184 API 复算 #7 冻结输入到 #151/task #129/run #131，16.608 秒完成；380 逐层、360 终点、2,820 日事实完整业务字段 SHA 与 #7 相同，两项逐层等式及 API 360 项守恒差异均 0，临时数据/账号精确清理 0。当前前端源码在临时 5181 代理 8184 的 Chrome 定向 1/1 PASS，排产与缺料实际加载、页面错误/API 5xx 均 0，截图见 `e2e-frontier-new-code-data-ready-20261109/`。首次直接从新 jar 访问旧静态 bundle 被路由重定向，失败 trace 原件保留；中间一次只等标题的浅断言截图仍加载中，不计最终数据加载证据。8184/5181 已停止。#150、#151、任务 #127/#128/#129、run #130/#131、快照 #69、临时账号均 0 残留，#7 来源不变，8183 #2 PUBLISHED/80 日事实/checksum 不变。
- 同日隔离 8183 角色配置正向探针 25/25 PASS：仅 `role:assign_permission` 操作者经配置 API 授予/撤回 `mrp_factory:view`，目标账号原 JWT 即时按指定工厂、全部、无范围与撤权返回正确内容；非法工厂配置未落库，临时账号/角色/审计 0 残留，#2 不变。证据 `test-kit/2026-10-15/evidence/runs/role-configuration-positive-20261109/result.json`；ADMIN-003 完整有权操作与全接口矩阵仍 NOT_RUN。
- 独立 `mrp-testkit` 临时 #150/run #130 的整机 35 秒、物料 843 秒完成；8,466 根到 106,782 逐层、55,512 终点、1,202,760 日事实，根计划/终点到日事实逐键双向守恒和逐层两式均 0 差异。快照 #69 由 #6 复制并补一个不良品仓零行；输入覆盖不足，持久化 6,326 缺库存及 57 缺主数据问题，14 个缺失物料编码。实际计算在 API 容器，探针错采 Worker，PERF-003/CALC-002 不转 PASS。证据 `current-frontier-material-20261109/result.json`、`current-frontier-sql-verify-20261109/result.json`；首次探针状态误判原件保留。
- 只改 `DailyPlanningService.alignMaterialDates` 的整机日事实索引、验收证据及报告，编译和新代码小样本 API/浏览器限定复核已过；新代码完整全量退出证据、正确对象的大负载性能与独立 Reviewer 仍缺。旧 `mrp-bigcalc-api-20261109`/Worker 在主机重启后为 Exited (137)，`OOMKilled=false`，不得归为计算 OOM；正式资源及 8183 发布版 #2 不动。
- UAT 计数仍 13 PASS、1 FAIL、105 NOT_RUN，阶段 18/19 仍 `REVIEW`。后续重点是独立准确性 oracle、合法全覆盖性能输入及逐角色有权操作/工厂内容；不能重复已过 42/42 或隔离 #2 成功链。

## 2026-11-06 阶段 18/19 单厂权限内容限定续验

- 同一探针后续扩展到 382/382 PASS：爱培科仓库映射分页 1/1、库存策略 1,564 条逐行本厂、永惠日历月份 403；旧版日历版本返回空，正向读取仍缺。证据 `role-matrix-scope-pages-20261106/result.json`；临时用户/角色/审计 0 残留、#2 不变。

- 隔离 8183 七个非管理员角色扩展写路由及单厂内容探针 375/375 PASS；仅爱培科角色可见工厂目录、主数据、仓库映射各 1 行，生产日历 212 行。临时用户/角色/审计 0 残留、发布 #2 不变；证据 `test-kit/2026-10-15/evidence/runs/role-matrix-single-factory-20261106/result.json`。
- 本轮只改验收探针和报告，未重复编译、全量测试或浏览器套件。ADMIN-003 有权操作与全接口跨厂内容、CALC-002 原口径重定、PERF-003 新大负载基线、完整 UAT 与独立 Reviewer 仍缺；119 项保持 13 PASS、1 FAIL、105 NOT_RUN，18/19 保持 `REVIEW`。

## 2026-11-06 阶段 18/19 用户安全权限续验

- 两项 P0 旧镜像真实越权：仅 `user:update` 直授自己 `ALLOW` 为 200/落库 1；仅 `user:create` 夹带 ADMIN 角色建号为 200/角色关联 1，失败证据 `user-direct-permission-before-20261106/result.json`、`user-create-role-before-20261106/result.json`，夹具 0 残留。V065 新增直授/重置权限并授 ADMIN，启停按已有独立权限检查，非空创建 `roleIds` 要求 `user:assign_role`。
- 最终后端编译、独立 `mrp_gate_final3_20261106`/Redis 6391 全量 100 套件/724 项 0 失败错误跳过，日志 `/tmp/mrp-user-security-fulltest-complete-20261106.log`；此前无退出码的 723 项 XML 及中止的一轮不计 PASS。新 Docker 镜像只重建隔离 API/Worker 为 `sha256:88b7ea4c...`，8183 health UP、V065/65、#2/run #2 PUBLISHED、80 条日事实与原 checksum 未变，MySQL/Redis 容器 ID 未变。新镜像权限 API 探针 PASS，临时账号/角色 0 残留：`user-security-after-20261106/result.json`。角色配置页新权限可见、仅普通编辑权限用户无重置按钮，隔离 Chrome 2/2 PASS；首轮测试定位/异步等待失败 trace 在 `evidence/e2e-user-security-20261106/`，最终在 `evidence/e2e-user-security-retest-20261106/`。正式 5180 只读 200。
- 两项仅限定自测 PASS，独立 Reviewer、ADMIN-003 全接口/工厂内容矩阵、原 CALC-002 重定、PERF-003 新基线、低堆/故障、正式凭据轮换与安全全扫描、完整 119 项 UAT 仍缺。UAT 实证保持 13 PASS、1 FAIL、105 NOT_RUN，18/19 均 `REVIEW`。不要重复 42/42、11-04 成功链或本次已通过的全量；不触碰正式资源，不清卷、不 reset。

## 2026-11-06 阶段 18/19 库存确认权限复验

- `INVENTORY-CONFIRM-AUTH-1106` P1 限定自测 PASS：旧镜像单权限角色对两条确认路径错误授权的 FAIL 原件保留；新镜像仅 `inventory:import` 为 403/403，仅 `inventory:import_confirm` 为业务 400/404。隔离浏览器上传账号在库存与经营计划弹窗均无确认按钮、页面 API 无意外 403/5xx，最终 Playwright 1/1，证据 `test-kit/2026-10-15/evidence/runs/inventory-confirm-permission-after-20261106/result.json` 和 `evidence/e2e-import-confirm-permission-retest-20261106/`。首次浏览器夹具漏授 `mrp_factory:view` 的截图原件保留，不计最终页面通过；临时用户/角色 0 残留。
- 后端编译、前端构建/111 单测通过；独立库/Redis 6391 的后端全量 100 套件/721 项、0 失败错误跳过，日志 `/tmp/mrp-inventory-confirm-fulltest-corrected-20261106.log`。首次漏传测试引导口令的上下文错误日志保留，不计 PASS。仅重建隔离 API/Worker 为 `sha256:c2b5f1e9...`，8183 health 200、V064/64、#2/run #2 PUBLISHED、80 日事实与 checksum 不变，正式 5180 只读 200。
- UAT 仍 13 PASS、1 FAIL、105 NOT_RUN。ADMIN-003 全接口/逐权限/工厂内容矩阵、现行上海 BOM 的大负载基线、低堆/故障、P2-I002 正式轮换与完整秘密扫描、完整 UAT、独立阶段 Reviewer 仍缺；18/19 保持 REVIEW。不要重跑 42/42 和 11-04 成功链，不要触碰正式资源或隔离 #2。

## 2026-11-06 阶段 18/19 七角色权限矩阵增量

- 用户明确允许相关改动成批后再做一轮编译、全量测试及真实 API/浏览器复核；本轮只有验收探针与报告改动，未改应用代码，未重跑既有 42/42 或成功链。隔离 8183 角色矩阵扩展为 7 角色/138 条真实 API 断言 PASS，三名无工厂范围角色的库存快照列表 200/空数组；此前两次探针预期错误 FAIL 原件保留，临时用户/审计 0 残留。证据 `test-kit/2026-10-15/evidence/runs/role-matrix-expanded-content-20261106/result.json`。
- #2/run #2 保持 PUBLISHED 和原 checksum；8 条逐层守恒 0 差异，80 条日事实合计 80。活跃源码有限 secret-like 字面量扫描 0 文件命中，完整归档/日志/凭据扫描仍缺。ADMIN-003 全接口/逐权限及返回内容工厂域矩阵、原 CALC-002/PERF-003 可执行新基线、完整 UAT 和独立复核均未关闭；18/19 继续 REVIEW。下一步聚焦有效上海 BOM 大负载与权限返回内容，不再无目的重复既有全量通过证据。
- PERF-003 只读核对：`load-frontier.json` 1,412 根/6 个月，其中 1 个根 `6830AA800760` 已停用；旧快照 #33 上海 119,595 + 无锡 14,450 条，#34 上海 121,711 条，当前上海 BOM 145,801 条。旧 runner 固定 8180/#33，不可直接跑或沿用 456,600 键预期；需 1,411 有效根 + 当前上海 BOM 的新独立 oracle，原用例保持 NOT_RUN。

## 2026-11-06 阶段 18/19 ADMIN-003 导出视图权限续验

- 阶段 18/19 仍 `REVIEW`，119 项仍 13 PASS、1 FAIL、105 NOT_RUN；ADMIN-003 总项 NOT_RUN。仅 `mrp_shortage:view` 且工厂 ALL 的隔离临时角色，旧镜像默认整机导出 200/8027B，物料导出 200/9908B；服务分视图补 `mrp_plan:export` 后新镜像整机 403、物料 200，临时用户/角色 0 残留。证据 `test-kit/2026-10-15/evidence/runs/export-view-permission-20261106/result.json`。
- 最终后端编译、33 组新增路由授权定向及全量 100 套件/719 项 0 失败错误跳过；正确测试库账号日志 `/tmp/mrp-export-permission-fulltest-corrected-20261106.log`。先前误用不存在的 `mrp` 测试库账号的失败日志保留。隔离 Docker 只重建 API/Worker，角色配置页、发布工作台 Playwright 各 1/1；8183 health 200、V064/64、#2/run #2 PUBLISHED、80 日事实/checksum 不变，正式 5180 只读。
- 新镜像七个非管理员角色选定 API/工厂域矩阵再次 37/37 PASS，临时用户/审计 0 残留，证据 `test-kit/2026-10-15/evidence/runs/role-matrix-after-export-fix-20261106/result.json`；仍不能代替 ADMIN-003 全接口矩阵。
- 证据保留缺口：首次 Playwright 复测使用固定输出目录，早期角色页失败 trace 被清理且现未找到；台账 `E2E-EVIDENCE-1106` 记录。后来用独立仓库内目录重新跑两项各 1/1，trace/截图见 `test-kit/2026-10-15/evidence/e2e-role-export-20261106/` 和 `e2e-published-export-20261106/`。后续浏览器测试必须使用新输出目录。
- 原 PERF-003 `load-frontier` 含停用根和历史混合组织快照，456,600 键未跑；现行上海 BOM/有效物料需新大负载基线。接下来完成 ADMIN-003 全接口逐角色/工厂矩阵、P0/P1 数据一致性与故障、安全扫描、完整 UAT 及独立 Reviewer；正式凭据轮换受正式资源只读约束。不得 reset、清卷、运行退役 runner 或重复 42/42 成功专项。

## 2026-11-06 阶段 18/19 CALC-002 原口径复现与上海口径续验

- 两阶段仍 `REVIEW`。119 项本轮实证为 13 PASS、1 FAIL（CALC-002 原 8,436 键）、105 NOT_RUN；原 CSV 模板未改。原快照 #33 为上海/无锡混合组织，C021/C023 规定历史只读，不可用于新计算。隔离 #147/task124 复现同物料路径唯一键冲突 `CALC_ERROR`，失败原件保留，临时 0 残留；原用例需按上海规则重定预期，不得虚标 PASS。
- 同一原始经营计划/库存导入的上海快照 #34 批次 #7，旧独立 Decimal oracle 2,820 日键 0 差异；当前 #149/task126/run129 14.654 秒重算 380 逐层、360 终点、2,820 系统日事实完整 SHA 与 #7 一致、守恒 0、精确清理 0。首次错选带 4 条人工修订的 #8 作基准，#148 三类事实只有日事实 4 格不同，SQL 证实 #148 与 #7 系统日事实逐行相等；FAIL 原件保留，#148 已清理。
- 最终 `node --check`、后端编译、独立 schema/Redis 6391 全量 100 套件/685 项 0 失败错误跳过，日志 `/tmp/mrp-stock-source-fulltest-20261106.log`；前端最近 build/111 项通过，本轮未改前端。8184 浏览器 `/planning/7` 50 条整机来源、`/shortage/7` 20 条汇总，Console error 0；真实批次/summary/守恒 API 200、360 项差异 0。8183 发布版 #2/run #2 仍 PUBLISHED、80 日事实、V064/64/checksum 未变；正式 5180 只读 200。8184 临时 API/Worker 用完只停止两容器。
- 下一优先级仍是数据一致性/计算与逐角色权限：原 PERF-003 456,600 日键双厂输入、低堆/Worker/DB 故障、ADMIN-003 全接口权限与工厂矩阵、安全完整扫描/正式凭据轮换、完整 UAT、独立阶段 Reviewer。不能重复 42/42 和 11-04 成功链；不能运行退役 UAT runner、reset、清卷或写正式资源。

## 2026-11-06 阶段 18/19 CALC-003 续验交接

- UAT 证据复核进度现为 13/119 PASS、106 NOT_RUN，较下方历史 12/119 增加 CALC-003。原隔离真实导入 #2 的 4,194 日键完整 HTTP 对独立 Decimal oracle 0 差异，发布 #3 与 #2 的逐层/终点/日事实完整业务字段 SHA 相同；当前镜像 8184/Worker 从 #3 冻结输入和原 #2 的 176 条池复算 #146/task123/run126，12.378 秒，515 逐层/终点、4,194 日事实 SHA 相同、守恒差异 0、精确清理 0。首次 #145 因探针漏复制池而错量，FAIL 原件保留，数据已精确清理；#3 未变。CALC-002 原始双厂同整机 8,436 键、PERF-003 456,600 键等仍 NOT_RUN。
- 11-06 后端/前端编译和 `node --check` 已通过，8184 Chrome `/planning/3`、`/shortage/3` 正常且页面错误 0；后端独立库/Redis 6391 全量正在运行，日志 `/tmp/mrp-accuracy-distinct-fulltest-20261106.log`，结束后汇总 Surefire。前端此前新增边界测试后 111/111 PASS，脚本修正后需最终复核。阶段 18/19 保持 REVIEW；P2-I002 正式轮换/扫描、全接口角色工厂矩阵、CALC-002、低堆/故障、完整 UAT 与独立阶段 Reviewer 仍缺。
- 本轮 8184 临时 API/Worker 名为 `mrp-bigcalc-api-20261106`、`mrp-bigcalc-worker-20261106`，连接 `mrp-testkit-mysql`/Redis；用完只停止这两个容器。两次启动配置失败日志保留：prod 对无密码 Redis 误 AUTH、local 默认上传目录不可写；最终 local 显式上传路径后 health 200，未改技术栈。8183 发布版 #2 和正式 5180 只读保护不变。

## 2026-11-05 阶段 18/19 角色矩阵与当前镜像大计算增量

- 8183 角色矩阵 7 个非管理员角色/37 项真实 API 断言 PASS，权限与 SQL 对账一致，临时用户/审计 0 残留，隔离发布版 #2 未变；证据 `test-kit/2026-10-15/evidence/runs/role-matrix-final-20261105/result.json`。ADMIN-003 全接口矩阵仍 NOT_RUN。
- 独立 8184 当前 API/Worker 基于发布 #13 真实重算 #144/task121/run124，45.286 秒，9,174 逐层、17,382 终点、217,275 日事实完整业务字段 SHA 与来源相同，守恒差异 0；临时 #143/#144、task120/121 及事实 0 残留。Worker 15 次采样最高 892.6 MiB，SQL 全局计数混入后台/探针，粗 GC 窗口 57 暂停/569.067ms/0 Full GC。8184 Chrome `/planning/13` 已发布、`/shortage/13` 最终显示 1,459 汇总物料/首屏 20 条、页面错误 0。后者与全量测试共用 MySQL 时页面约 25 秒完成，独立分页 API 163ms，性能待无竞争复测。证据 `test-kit/2026-10-15/evidence/runs/current-large-calculation-monitored-20261105/result.json`。
- 后端独立 `mrp_gate_20261105`/Redis 6391 全量两次均 100 套件/685 项、0 失败错误跳过；前端最终 build/111 项 PASS，`git diff --check` PASS。历史 #13 缺料页性能修复后 5181/8184 Chrome 首屏 14.6→1.029 秒、1,459 汇总物料不变，v5 #2 的确认请求正常；见阶段 18/19 报告。阶段 18/19 继续 REVIEW；完整 UAT 仍 12/119，原始 accuracy-stock/accuracy-distinct 与 456,600 日键、故障/低堆、全接口权限、正式凭据轮换/扫描及独立复核未过。
- 隔离 8183 四容器和 8184 临时 API/Worker 当前运行；后者用完只停止本轮两个容器，不碰隔离 8183 或数据卷。管理员最终口令只在 `/tmp/mrp-testkit-admin-final-20261105.secret`（权限 600），不得输出。正式主库、5180、Redis 和卷只读，不 reset、不 `down -v`、不运行退役 runner。

## 2026-11-05 阶段 18/19 角色撤权缓存时序增量

- 阶段 18/19 仍 `REVIEW`；独立 UAT 仍 12/119 PASS、107 NOT_RUN。用户确认少于 10 人，优先大数据逐键一致性、计算及角色/工厂权限，原 42/42 和隔离成功链不无目的重跑。
- `UserService.getEffectivePermissions` 原在角色事务提交前清缓存后可能被并发请求回填旧权限。已改每次鉴权从 MySQL 读取；角色集成测试故意回填旧缓存，旧 token 访问 `/plans` 403。`./mvnw compile -q`、独立 schema/Redis 6391 全量 100 套件/685 项 0 失败/错误/跳过，日志 `/tmp/mrp-role-cache-fulltest-20261105.log`。
- 隔离 API/Worker 新镜像 `sha256:ee22e9c1...`，8183 health 与管理员 `/auth/me`、#2 summary 200，Chrome 角色配置保存/刷新/落库 1/1 PASS；临时用户/角色 0 残留。V064/64、#2/run #2 PUBLISHED、80 日事实和 checksum 不变，正式 5180 只读 200。旧失败 trace 保留，新浏览器输出 `test-kit/2026-10-15/evidence/e2e-role-cache-20261105/`。
- 下一优先项：隔离大数据逐键计算和 SQL/资源证据、完整角色/工厂接口矩阵；P2-I002 正式轮换及完整秘密扫描、安全总门禁、完整 UAT、独立阶段 Reviewer 尚缺。正式主库/5180/Redis/卷只读，不 reset、不清卷、不运行退役 UAT 脚本。
- 同日已在 `mrp-testkit-mysql/mrp_testkit` 只读验既有 #13/run #13：9,174 逐层行守恒与负量 0 异常；8,916 末级键对终点、17,382 终点键对 217,275 日事实均 0 差异；#10/#13 输入和结果 checksum 相同。仅存量结果限定 PASS，仍缺本轮代码重算大批次及 SQL/CPU/GC/低堆证据。

## 2026-11-05 阶段 18/19 管理员引导后最终回归增量

- 阶段 18/19 仍 `REVIEW`，P2-I002 总门禁 `FAIL`。隔离 8183 的 V001 播种管理员已一次性引导和首次改密；旧固定口令、引导口令、旧 JWT 均拒绝。新口令只在本机权限 600 的 `/tmp/mrp-acceptance-admin-final-20261105.secret`，报告不得写明文；隔离 Playwright 环境变量为 `MRP_ACCEPTANCE_ADMIN_PASSWORD`。API/Worker 当前引导环境变量为空，V064/64、#2/run #2 PUBLISHED，正式 5180 和正式主库/Redis/卷只读。
- 最终代码 `./mvnw compile -q`、独立 `mrp_gate_20261105`/Redis 6391 下 `./mvnw test -q` 99 套件/684 项、0 失败错误跳过；日志 `/tmp/mrp-stage18-bootstrap-fulltest-script-final-20261105.log`。前端 build、13 文件/110 单测、隔离 Playwright 1/1，发布版 #2 与 summary API 均 200/PUBLISHED。首次全量因 `MaterialApiTest` 依赖旧库数据的失败日志保留。`docker_acceptance_chain.mjs` 两处旧固定口令改为必需验收环境变量，未重跑写入新批次的成功链。
- ENV-005~008 已按原断言执行，离线夹具 `verify_fixtures.py` 4/4；本轮独立 UAT 12/119 PASS、107 NOT_RUN，原始 CSV 仍是未执行模板。完整安全扫描/正式凭据轮换、全接口工厂域、456600 日键与 SQL/GC/低堆、v1/v2/v3 历史回放、备份恢复、完整 UAT 和独立阶段 Reviewer 仍缺。下方较早 8/119、678 项和“隔离默认账号仍可登录”为历史快照，当前以本节和门禁矩阵为准；不得置 DONE。
- 旧接口隔离安全脚本已改用显式验收口令；当前镜像真实 API 24/24 断言 PASS、临时数据/审计 0 残留、#2 保留，证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261105080353273/result.json`。后端修改后再次 99 套件/684 项、前端 build/110、Chrome 1/1；日志 `/tmp/mrp-stage18-legacy-bootstrap-fulltest-20261105.log`。有限源码扫描命中 15 个历史登录 bundle、38 个旧 test-kit 脚本含固定示例口令；当前 Docker 构建重建静态目录，其他打包和归档未完整审计，ADMIN-005 不关闭。

## 2026-11-05 阶段 18/19 认证错误脱敏增量

- 阶段 18/19 仍为 `REVIEW`；119 项 UAT 仍为 8 PASS/111 NOT_RUN。新发现 `AUTH-ERROR-DISCLOSURE-1105` P1：飞书上游失败响应体/异常被写日志并可能进入 API 错误体，改密异常详情也回传并记录堆栈。代码已改固定文案，含敏感标记定向 3/3、最终后端 98 套件/681 项、隔离 Docker 真实失败回调和 Chrome Playwright 1/1 自测 PASS；前一版 680 项及镜像证据保留，独立安全复核、ADMIN-005 全范围扫描仍缺。证据见阶段 18 报告、门禁矩阵和 `/tmp/mrp-stage18-feishu-security-fulltest-final-20261105.log`。
- 隔离四容器运行，8183 health 200、Flyway V064/64、#2/run #2 PUBLISHED；正式 5180 只读 200。隔离 API/Worker 已重建，MySQL/Redis 卷未清理；最终浏览器输出单独放在 `test-kit/2026-10-15/evidence/e2e-docker-acceptance-feishu-final-20261105/`，既有失败证据保留。
- P2-I002 仍 P0 FAIL：只读 SQL 确认隔离库唯一管理员的哈希仍等于 V001 固定播种值且账号启用。初始管理员安全引导和正式凭据轮换未完成；正式主库、5180、Redis 和数据卷继续只读。下一轮优先完成这条 P0，再补全接口安全/性能/兼容及其余 111 项 UAT，独立阶段复核前不得置 DONE。

## 2026-11-05 阶段 18/19 凭据续验增量

- 继续保持阶段 18/19 `REVIEW`。P2-I002 整体 `FAIL`：已应用 V001 仍播种固定管理员密码、隔离默认账号可登录，正式现有凭据轮换未做；不得修改已应用迁移或写正式库。新建/重置随机一次性密码、强制改密、改密后旧 JWT 持续失效、飞书新用户无固定本地口令及停用邮箱绑定拒绝为限定子项 PASS。AUTH-008、ADMIN-001/005 完整用例仍 NOT_RUN，119 项 UAT 维持 8 PASS/111 NOT_RUN。
- 最终后端 `./mvnw test-compile -q`、独立 schema/Redis 6391 全量 97 套件/678 项 PASS，前端 build/110 单测、Docker 镜像构建及最终镜像 Playwright 1/1 PASS。首轮改密过滤器误拒的 677 项/1 FAIL 日志 `/tmp/mrp-stage18-fulltest-credential-20261105.log` 保留；最终日志 `/tmp/mrp-stage18-fulltest-final-20261105.log`。隔离 8183 Chrome/API 临时账号首次改密后等待权限页、旧 JWT 401；两个临时账号和 4 条对应审计夹具精确清理 0 残留。#2 PUBLISHED、V064/64、health 200/UP，正式 5180 只读 200。仅重建隔离 API/Worker，未重建 MySQL/Redis 卷。
- 下一步仍优先 P0 初始凭据与轮换方案、全接口安全/审计、独立大负载性能及 Phase 1 历史兼容，然后完整 UAT 和独立 Reviewer；见门禁矩阵与 issue-log。旧 JWT 因新增凭据版本字段需重新登录。隔离默认管理员仍可登录，未可宣称安全总门禁通过。

## 2026-11-05 阶段 18/19 续验增量

- AUTH-001/002/003 隔离 API 与 Chrome 真实验证 PASS：正确登录/退出后受限页回登录，空值/错密/未知用户业务拒绝且响应无内部信息，篡改 token 受限 API 401、页面清 token 回登录并可再次登录。`test-kit/2026-10-15/evidence/runs/auth-boundary-20261105/`；首轮浏览器脚本异步等待和 URL 匹配误报 `browser.json` 保留。原 AUTH-003 允许“过期或篡改”，本轮仅篡改分支。AUTH-004 零权限账号隔离 API 三项 403、Chrome 等待权限页、夹具 0 残留 PASS；`test-kit/2026-10-15/evidence/runs/auth004-no-permission-20261105/`。等待页误写密码修改成功记 P2。UAT 自测累计 8/119，其余 111 项；阶段 18/19 仍 REVIEW。
- P2-I002 P0 凭据门禁新增代码发现：V001 固定管理员凭据播种，`UserService.create/resetPassword` 固定口令，登录页预填管理员凭据；隔离默认账号可登录。已应用迁移不可改，正式资源本轮只读；需新建/重置一次性凭据与首次改密流程及授权运维轮换既有正式凭据。P2-I002 仍 FAIL，ADMIN-005 总项仍 NOT_RUN；未把局部 AUTH PASS 误写为安全总 PASS。

- 依入口顺序重读规定文档并核对旧证据、HEAD `f040614` 与大量未提交改动。隔离 `mrp-acceptance-20261104` 四容器原停止；只用 `docker start` 恢复，MySQL/Redis 健康、8183 health 200/UP、Flyway V064/64、发布版 #2 PUBLISHED。正式 5180 只读 200；未触碰正式主库/Redis/卷。
- LIFE-010 旧版导出代码/SQL 与 Phase 2 两个 XLSX 服务已追踪。隔离临时 SQL 文本包含 `=,+,-,@` 公式前缀，真实 API 202/下载 200/3452B，OOXML 11 字符串单元格、0 公式节点，临时行/文件 0 残留。PASS `test-kit/2026-10-15/evidence/runs/life010-formula-3115b413e75842fa/result.json`；首次探针共享字符串去重计数误报 FAIL `.../life010-formula-b9c5d38764cd4782/result.json` 保留。原用例要求可编辑文本入口而本轮为 SQL 夹具，故 LIFE-010 整项仍 NOT_RUN、UAT 仍 4/119 PASS。
- 后端 `./mvnw compile -q`、独立 `mrp_gate_20261105` schema/Redis 6391 全量 `./mvnw test -q` 97 套件/676 项、前端 `npm run build`/110 单测、隔离 Chrome Playwright 1/1 PASS；最终日志 `/tmp/mrp-stage18-fulltest-granted-20261105.log`。两次测试凭据/新 schema 授权错误的失败日志 `/tmp/mrp-stage18-fulltest-20261105.log` 与 `/tmp/mrp-stage18-fulltest-corrected-20261105.log` 保留，不计 PASS。阶段 18/19 保持 REVIEW；性能大负载、完整安全/兼容、其余 115 项 UAT 与独立 Reviewer 不变。

Mimo 可能跨多个会话执行。每次停止前必须留下可恢复状态，下一次不能依赖聊天历史。

## 2026-11-04 阶段 18/19 验收交接（最新）

- 11-09 ADMIN-002 与 CALC-001 增量：隔离 8183 原镜像删除被用户引用的角色为 500；`RoleService.delete` 加角色行锁和引用业务检查后，最终 14/14 真实 API、当前前端角色配置 Chrome 1/1 PASS，临时用户/角色/审计 0 残留，发布 #2/run #2/checksum 不变。失败原件及旧静态页面失败 trace 保留。后端最终 `./mvnw compile -q`、独立 MySQL/Redis 全量 100 套件/725 项退出 0；标准 Docker 构建因 Docker Hub token 超时，使用现有隔离镜像叠加当前 jar/前端 dist，仅替换 API/Worker。CALC-001 精确黄金链用例在全量中通过。119 项证据复核为 15 PASS、1 FAIL、103 NOT_RUN；ADMIN-003、PERF-003、CALC-002 当前 BOM 新批次重定、完整 UAT 与独立 Reviewer 未完成，18/19 保持 REVIEW。最后只读探测 8183 health 200；正式 5180 未监听，未启动或写入正式前端。

- 21:34 AUTH-005 页面续验：旧隔离 Chrome 的 READONLY 账号在经营计划/库存页各见 1 个无权导入按钮，代码还发现物料/分类/BOM 写按钮无权限控制。五页按后端权限码修正后，隔离 API/Worker 新镜像真实 Chrome 在 READONLY 六页（含排产）写按钮均 0、管理员经营计划导入为 1、Console error 0；综合探针五类写 API 403、全部断言 PASS、夹具 0 残留，`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104133304962/result.json`。前端构建/110 单测、后端 test-compile/97 套件 676 项、隔离 Playwright 1/1、V064/64、#2 PUBLISHED、8183 与正式 5180 只读 200。AUTH-005 自测 PASS，逐项 UAT 4/119，原 CSV 仍 119 项待执行模板；其余 115 项、CAP-1014-03 全接口矩阵、性能大负载与独立 Reviewer 未完成。18/19 仍 REVIEW。
- 21:03 AUTH-005/007 与下载路径续验：原镜像真实 API 暴露导出任务 `filePath`、可读取导出目录外文件 200，失败 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104122903394/result.json`。`ExportTask` 隐藏路径，`ExportService` 下载限定导出目录内真实 `.xlsx`；最终隔离镜像实际 XLSX 所有者 200/3350B、同权限另一账号 403、目录外与缺失文件 404、响应无路径，`.../legacy-phase1-gate-20261104130228745/result.json`；临时夹具/文件/审计 0 残留，V064/64、#2 PUBLISHED。后端编译与全量 97 套件/676 项 0 失败，Docker 构建、隔离 Chrome 1/1、8183/5180 只读健康检查通过。AUTH-007 本轮自测 PASS，逐项进度 3/119；AUTH-005 五个写 API 403 但页面按钮未验，LIFE-009 时间到期口径未验。`EXPORT-DOWNLOAD-1104` 待独立复核；18/19 均 REVIEW。一次漏传测试专用 local JWT 的全量运行有 3 个上下文错误，保留失败，不算 PASS。
- 只读性能/兼容盘点：隔离 #2 summary 20 次 7~24ms/408B、整机 grid 20 次 26~131ms/104058B，均 200；schema digest 语句累计增 642 含后台 Worker，不能算每请求 SQL；API/Worker 内存快照 467/401.3MiB。正式主库只读核对 `plan_version` 只有 v1 538 条，`mrp_calc_batch` 有 v2/v4/v5、无 v3，完整 v1/v2/v3 回放仍缺。PERF-001~014 不因小样本改 PASS；下一优先项是独立大负载与故障环境、AUTH-005 浏览器按钮及完整 UAT。
- 20:20 AUTH-009 增量：隔离旧镜像停用临时账号后旧 JWT 仍 GET `/plans` 200，失败证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104120125749/result.json`。JWT 过滤器补当前用户 ID/用户名/启用状态 SQL 检查后，最终隔离镜像 `sha256:a90040ba...` 同探针全部 PASS、停用旧 token 401，见 `.../legacy-phase1-gate-20261104121840419/result.json`；临时用户、旧版本、任务、导出与审计均 0 残留。后端全量 97 套件/675 项、隔离 Playwright 1/1、V064/64、#2/run #2 PUBLISHED、5180 只读 200。AUTH-009 完整角色撤权和独立复核未完成，阶段 18/19 仍 REVIEW。
- AUTH-009 原用例补充：管理员通过权限 API 撤销 `schedule:view` 后，临时账号原 JWT 读 `/plans` 由 200 立即变 403，随后停用变 401；最终 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104122135986/result.json`，权限与审计残留均 0。UAT 本轮 AUTH-006/009 两项自测 PASS，117 项仍未完成；阶段状态不变。
- 19:30 续验增量：门禁矩阵 `reports/2026-11-04-stage18-19-gate-matrix.md` 已逐项区分 PASS/FAIL/NOT_RUN，UAT 本轮进度见 `reports/2026-11-04-stage18-19-uat-progress.md`。隔离旧镜像真实复现 Phase 1 旧写入口未封闭、跨厂列表/当前/网格/对比/任务读取及 Phase 2 任务混读，原 FAIL 证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104105403840/result.json`。代码修复后最终隔离镜像同探针全部断言 PASS，含 AUTH-006、双厂整机导出与他人导出任务/下载拒绝；证据 `.../legacy-phase1-gate-20261104115334130/result.json`，临时夹具 0 残留。
- 本次最终代码后端全量 97 套件/675 项、前端 13 文件/110 项通过；隔离 Docker 镜像重建后 health 200、Flyway V064/64、发布版 #2/run #2 PUBLISHED。API 启动日志不再打印 Spring 自动生成密码；Chrome Playwright 1/1 有 trace，planning/shortage 浏览器 Console/Network 错误 0、截图和原始结果在 `test-kit/2026-10-15/evidence/runs/browser-stage18-20261104/`。正式 5180 只读 HTTP 200。缺 local 密码变量的全量测试失败日志 `/tmp/mrp-stage18-fulltest-scope-20261104.log` 保留；正确隔离变量重跑通过，不能把前者当 PASS。
- 未完成优先项：P2-I002 正式凭据轮换、CAP-1014-03 全接口安全/下载/审计、TESTKIT-PERF-01 的 456600 日键/SQL/GC/低堆/故障恢复、10 万物理行 XLSX、真实 v1/v2/v3 回放、118 项剩余 UAT、备份恢复/外部飞书及独立阶段 Reviewer。当前 18/19 仍 REVIEW；不运行指向 8180 的旧 UAT runner，不动正式主库/5180/Redis/卷或隔离 #2。
- 当前阶段/状态：`.harness/workflow.yaml` 的 18、19 均为 `REVIEW`；旧 `current_audit` 区块是 2026-09-29 历史快照，不代表本轮结论。工作树有大量既有未提交改动，禁止 reset、清理或覆盖。当前 HEAD `f040614`。
- 已完成到：经营计划与库存导入至整机排产专项 42/42 场景已于 11-03 自测和独立复核 PASS，见 `docs/经营计划与库存导入至整机排产端到端执行记录-2026-11-02.md`。11-04 独立 Docker 项目 `mrp-acceptance-20261104` 空库迁移 V001→V064、V020 升级专项、API/Worker 分离、重启持久性和成功业务链已验。成功链为 Excel 导入→批次 #1 预检→Worker 排产→整机确认→物料分析→物控确认→发布版 #2/run #2；两厂各 10 台、差额 0，三种原材料各两厂合计 20。正式前端端口为 5180；8183 是隔离 Docker API/静态前端，不可混称。
- 最新门禁：后端 `./mvnw test-compile -q` PASS、独立测试 schema/Redis 6391 下 `./mvnw test -q` 97 套件/675 项全过；前端 Docker build PASS、`npm test -- --run --maxWorkers=1` 110/110；隔离专用 Playwright 1/1 PASS，含 1440/1920 负荷格边界及 trace。8183 health/planning/shortage 与正式 5180 planning HTTP 200。运行证据见阶段 18/19 报告及 `test-kit/2026-10-15/evidence/runs/docker-acceptance-20261104/{result,finish}.json`。
- 本轮新增文件：`test-kit/2026-10-15/tools/docker_acceptance_chain.mjs`、`test-kit/2026-10-15/tools/legacy_phase1_gate.mjs`、`frontend/e2e/docker-acceptance.config.ts`、`frontend/e2e/docker-acceptance.spec.ts`、门禁矩阵和 UAT 进度报告；这些及失败证据均在未提交工作树中，不得清理。旧 `two-workbench-compact.spec.ts` 默认连 5180，三项登录超时；失败现场在 `frontend/test-results/`，不能记为业务 PASS，隔离专用脚本已通过。
- 正在运行：隔离 MySQL/Redis/API/Worker 容器和 8183 映射，发布版 #2 留在隔离库供复核；正式 5180 未写入。无本轮未结束的测试命令。Compose 启动依赖本地验收密钥环境变量，禁止打印凭据或猜测值。
- 未完成：阶段 18 全范围性能、安全、兼容，10 万行 XLSX 实际导入、完整 119 项 UAT、正式 Compose 实际运行和独立阶段 Reviewer；`TESTKIT-PERF-01`、`CAP-1014-03` 等整体问题仍按最新台账核实。阶段 18/19 不得改 `DONE`。正式主库、5180 和正式 Redis/卷只读；写入测试仅用隔离环境，不执行 `docker compose down -v` 或退役的 `deploy/run-uat-tests.sh`。
- 恢复第一步：按根 `AGENTS.md` 读取 `project.md`、`.harness/execution-laws.md`、`.harness/README.md`、阶段 18/19 Prompt、报告、`test-and-gates.md`、`issue-log.md` 和本交接；随后 `git status --short`、`docker ps --filter name=mrp-acceptance-20261104`、`curl http://127.0.0.1:8183/actuator/health`。下一动作是对照 18/19 门禁与 119 项 UAT 台账找出仍无运行证据的项目，先补可在隔离环境执行的 P0/P1 和安全/性能边界，逐项记录 PASS/FAIL/NOT_RUN，并在发现真实缺陷时按仓库三层验证流程修复。

## 1. 停止前检查

- `.harness/workflow.yaml` 的阶段状态与真实进度一致；
- 当前阶段报告已写入，即使尚未完成也记录最后通过的门禁；
- 当前修改文件和未提交变化清单已记录；
- 最后一条成功命令和第一条建议恢复命令已记录；
- 阻塞、失败日志、临时数据位置和待决策均已记录；
- `.superpowers/sdd/2026-09-22-phase2-daily-mrp/progress.md` 与当前阶段一致；
- 当前阶段 Agent 文件所有权、已完成/仍运行 Agent 和独立 Reviewer 结论已记录；
- 不留下无法解释的后台任务、半执行迁移或 READY 半版本。

## 2. 恢复顺序

新会话只按以下顺序恢复：

1. `project.md`；
2. `.harness/workflow.yaml`；
3. 当前阶段 Prompt；
4. 当前阶段报告；
5. 当前阶段引用的契约；
6. `.harness/issue-log.md` 和 `decision-log.md`；
7. `.superpowers/sdd/2026-09-22-phase2-daily-mrp/progress.md` 和当前 task report；
8. 检查工作区差异、服务、Agent 和任务状态；
9. 重新运行报告中的最小恢复验证。

如果 workflow 状态、报告和代码不一致，以可复核的代码与测试事实为准，先修正文档状态，不直接继续编码。

## 3. 交接摘要模板

```markdown
## 会话交接
- 当前阶段/状态：
- 已完成到：
- 当前修改文件：
- 最后通过的门禁：
- 当前失败或阻塞：
- 正在运行的服务/任务：
- 恢复后的第一条命令：
- 下一具体动作：
```

## 2026-10-16 17:40 排产续修运行交接

- 当前阶段18/19保持REVIEW；本轮详情见`test-kit/2026-10-16/排产权限与负荷接口续修报告.md`及`evidence/continuation-independent-review.md`，补充任务只对限定范围记录状态。
- 本轮批次工厂域、独立整机负荷、发布复用/分块、日分配冻结/防重复、v4守恒SQL及孤立组发布阻断已完成三层验证。最新52项专项、361项限定排除回归、真实HTTP及浏览器PASS；失败证据全部保留。
- 主8080/5180及隔离8180/5181已加载最新后端；17:40:28主库保护SHA全部PASS。主91/run37、历史快照33禁止重算、调整、取消或发布。物料/分类/BOM管理不测试。隔离大发布10/13保留核验，临时fixture18/19/20已清理。
- 未完成仍为CAP-1014-03库存/共享配置/导出等全链路、同步发布/capacity-review全量投影、bulk和全量详情，以及三轮checksum/并发写/低内存恢复/大导入/导出/V020升级/完整交互门禁。
- 下一步先核报告最终独立结论和workflow状态，再按剩余具体问题追踪真实代码；勿重复已通过的测试，勿覆盖原before/失败日志或reset既有dirty workspace。私有运行参数保持在/tmp，禁止打印凭据。

## 2026-10-16 20:03 后续修复状态

- 当前工厂映射/生产日历HTTP工厂域：DONE，原Reviewer限定PASS。49专项、394限定回归、19真实HTTP、双环境浏览器通过；主91/run37/224100事实/0override/快照33保护SHA不变。报告：test-kit/2026-10-16/工厂映射与生产日历工厂域续修报告.md。
- 17:40后还已限定DONE：自动明细索引V047、无override投影、库存快照域、用户scope删除、指纹join内存、旧Phase1导出任务归属。逐项证据见workflow对应review，不代表整个阶段完成。
- 阶段18/19仍REVIEW，CAP-1014-03、TESTKIT-PERF-01整体OPEN。当前审计接口尚无工厂过滤，下一步修复列表/count及配置历史日志读取；capacity-review全量物化/排序、已有override分页、同步发布、大导入导出/并发恢复/V020升级仍未关闭。
- 主API8080/UI5180、隔离API8180/UI5181已加载日历scope补丁。禁止改主batch91/run37和隔离保留大数据10/13；物料/分类/BOM页面不测试。私有启动参数仅由restart_verified_backend.py读取，不输出。

### 2026-10-16 20:23 审计列表工厂域限定DONE
原Reviewer限定PASS：55专项、401限定回归、17真实HTTP、双环境浏览器各1；20:19主库保护SHA不变。报告test-kit/2026-10-16/审计日志工厂域续修报告.md、evidence/audit-scope-independent-review.md。阶段18/19仍REVIEW，权限及性能整体OPEN。后续推进无覆盖capacity-review性能，保留所有大批次。

- 2026-10-16 20:37 无override整机capacity-review流式窄投影补丁REVIEW：82专项、409限定回归PASS；双环境重启后完整336项负荷/指纹不变，主中位9347→1587ms、隔离9389→1669ms；排产页面浏览器各1PASS；主库保护SHA不变。见无覆盖产能确认性能续修报告.md，待原Reviewer。仍保留每条checksum字符串并排序，非恒定内存；override路径/发布/bulk及TESTKIT-PERF-01整体OPEN，阶段18/19 REVIEW。

- 无override整机产能确认流式投影原Reviewer限定PASS，Coordinator登记DONE。证据evidence/capacity-projection-independent-review.md；整体性能/阶段门禁不关闭。

- 2026-10-16 库存策略当前HTTP工厂域补丁REVIEW：30专项、416限定回归、17隔离真实HTTP、双环境浏览器及主只读HTTP通过；主保护SHA不变。混厂提交整份拒绝、原记录仓库授权、列表/count先过滤、未知仅ALL、新审计scope。见库存策略工厂域续修报告.md；等待原Reviewer。CAP-1014-03整体OPEN，旧版本配置域/并发时序及性能总门禁未关闭。

- 2026-10-16 库存策略审计分隔符修复获原Reviewer限定PASS，Coordinator登记当前库存策略HTTP工厂域DONE。复审报告evidence/stock-scope-delimiter-independent-review.md；32专项、418限定回归、14新增HTTP及原17HTTP/双环境浏览器通过，主保护SHA不变。原FAIL保留，阶段18/19 REVIEW及CAP-1014-03/TESTKIT-PERF-01 OPEN。继续历史生产日历版本HTTP工厂域修复，before13项真实HTTP已留证。

- 2026-10-16 历史生产日历版本HTTP域补丁REVIEW：38专项、428限定回归、25隔离真实HTTP及双环境浏览器各1PASS，临时夹具清理0残留；主只读4HTTP、21:20:58保护SHA不变。报告历史生产日历版本工厂域续修报告.md，等待原Reviewer；CAP-1014-03整体OPEN，阶段18/19 REVIEW。旧映射/库存策略版本域及性能剩余任务未关闭。

- 历史生产日历版本HTTP域获原Reviewer限定PASS，Coordinator登记DONE。独立报告evidence/legacy-calendar-scope-independent-review.md；428限定回归、25隔离HTTP与双环境浏览器及主保护SHA通过。仅关闭此子任务，阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。

- 2026-10-16 库存内部复合键获原Reviewer限定PASS，Coordinator登记DONE。21专项、434限定回归、真实MySQL净需求70/125及安全预留20/5、前后HTTP和双环境浏览器通过；主保护SHA不变。报告库存复合键续修报告.md，独立证据evidence/inventory-key-independent-review.md。仅关闭归一化/安全策略内部键；阶段18/19 REVIEW、CAP-1014-03/TESTKIT-PERF-01 OPEN。

- 2026-10-16 稀疏人工调整明细窗口分页REVIEW：74专项、436限定回归、21万事实6选择器各5次完整响应不变；HTTP中位首页1279→372ms、中2372→457ms、尾2907→545ms，双环境浏览器PASS、主保护SHA不变。报告人工调整明细分页性能续修报告.md，待原Reviewer；V>P、发布/bulk及整体安全性能门禁不关闭。原非法跨月同组夹具失败证据保留，按引擎真实分组约束修正后通过。

- 稀疏人工调整明细分页获原Reviewer限定PASS，Coordinator登记该子任务DONE。独立证据evidence/override-window-independent-review.md；阶段18/19 REVIEW、CAP-1014-03与TESTKIT-PERF-01 OPEN。

- 2026-10-16 历史映射/库存策略版本HTTP工厂域补丁REVIEW：43专项、447限定回归、重启后真实HTTP及双环境大表浏览器通过；主91/run37/224100事实/0override/历史快照33保护SHA不变，临时版本清理0残留。库存按全部引用批次的冻结映射授权，未知/无绑定只ALL，历史库存全局写只ALL。报告历史映射与库存策略版本工厂域续修报告.md，等待原Reviewer。原stockVisible括号导致6项FAIL日志保留，修正后全通过；CAP-1014-03/TESTKIT-PERF-01整体OPEN、阶段18/19 REVIEW。

- 历史映射/库存策略版本HTTP域获原Reviewer限定PASS，Coordinator登记该子任务DONE。独立证据evidence/legacy-scope-versions-independent-review.md；447限定回归、30真实HTTP、双环境浏览器及主保护通过；并发时序未验收，阶段18/19 REVIEW、CAP/PERF整体OPEN。

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
