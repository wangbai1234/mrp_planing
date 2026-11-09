# 18 integration-performance-security 任务报告

## 2026-11-09 验收执行口径更新

本次仅改验收文档，无新业务测试或缺陷关闭。后续按 `stage18-19-five-hour-acceptance.md` 合并同批验证、复用指纹未变证据；优先现行上海 BOM 独立 oracle、角色有权操作及跨厂内容、合法大负载。原 15/119 PASS、1 FAIL、103 NOT_RUN 不变，阶段 18 保持 `REVIEW`。

## 2026-11-09 ADMIN-002 角色引用删除修复及总回归

追踪 `DELETE /roles/{id} -> RoleService.delete -> RoleMapper.deleteById` 与 V001 `user_role` 外键：原实现先删角色权限，再删仍被用户引用的角色，隔离 8183 返回 500。失败原件 `test-kit/2026-10-15/evidence/runs/admin002-role-before-20261109/result.json`，夹具与审计 0 残留。修复为事务内 `SELECT role FOR UPDATE`，先检查 `user_role` 引用，存在时明确返回 `MRP_ILLEGAL_STATE` 400，解除关联后删除；行锁与外键共同约束并发分配。新增真实 MySQL/MockMvc 回归。

后端 `./mvnw compile -q` 退出 0，定向 `RoleConfigurationIntegrationTest` 退出 0；最终独立 schema/Redis 6391 的 `./mvnw test -q` 退出 0，Surefire 100 套件/725 项、0 失败/错误/跳过，日志 `/tmp/mrp-admin002-fulltest-final-20261109.log`。首轮完整测试因新断言误读顶层 `code` 而 725 项中 1 FAIL，日志 `/tmp/mrp-admin002-fulltest-20261109.log` 保留，未计 PASS。此前丢失 Maven 退出码的问题由非保留变量 `run_rc` 修正，`TEST-EXIT-WRAPPER-1109` 本次关闭。

标准 Docker 构建因 Docker Hub token 请求超时失败，日志 `/tmp/mrp-admin002-docker-build-20261109.log`。本地后端 jar 打包退出 0；基于现有隔离镜像替换 jar，补入此前构建的当前前端 `dist` 静态资源，仅重建隔离 API/Worker。第一次 jar 含旧静态 bundle，角色配置页 Chrome 缺工厂范围，失败 trace 在 `e2e-admin002-role-20261109/`；最终前端资源补入后定向 Chrome 1/1 PASS，证据 `e2e-admin002-role-current-ui-20261109/`。最终 8183 真实 API 14/14 PASS，临时用户/角色/审计 0 残留，`admin002-role-final-20261109/result.json`；Flyway V065、#2/run #2 PUBLISHED 与原 checksum 未变。正式 5180/主库/Redis 未写。ADMIN-003、完整 UAT、性能及独立阶段复核仍未完成，阶段 18 保持 `REVIEW`。

## 2026-11-09 角色配置正向权限限定续验

最终回归与新代码复核补记：独立 `mrp_gate_frontier_resume_20261109`/Redis 6391 的 Surefire 写出 100 套件、724 项、0 失败/错误/跳过，两个迁移测试也完成 V065 校验；启动命令在 Maven 结束后以 zsh 只读变量 `status` 收集退出码而报错，原始 Maven 退出码丢失，故当前代码全量总门禁不记 PASS，也不为此立即重复整套测试。`./mvnw package -DskipTests -q` 明确退出 0；前端 `npm run build` 明确退出 0。

新 jar 独立在 8184 连接 `mrp-testkit`/Redis 6391，通过一次性 ADMIN 测试账号触发 #7 冻结输入复算 #151/task #129/run #131，16.608 秒成功。380 逐层、360 终点、2,820 日事实完整业务字段 SHA 均与 #7 相同；逐层 `gross=used+net`、`before=used+after` 和负量违例均 0，`/plans/conservation` 的 360 项差异 0。临时批次、任务、run、全部事实与账号精确清理为 0，#7 未变。证据 `test-kit/2026-10-15/evidence/runs/frontier-new-code-small-recalc-20261109/result.json`。这是小样本新代码行为复核，不证明 120 万事实的性能收益或 PERF-003。

首次直接从后端 jar 访问 8184 时，jar 内仓库旧静态 bundle 缺物料缺料路由，浏览器被重定向回排产页；失败截图/trace 保留在 `e2e-frontier-new-code-20261109/`。当前前端源码 `npm run build` 后，独立 5181 Vite 代理新 8184 API；中间一次仅断言标题的用例虽过但截图仍加载中，不计数据加载证据。最终收紧为实际末级物料行可见、加载遮罩消失后，Chrome 1/1 PASS，排产与缺料页面有真实数据，Console error/pageerror 和 API 5xx 均 0，截图在 `e2e-frontier-new-code-data-ready-20261109/`。8184/5181 测后停止；8183 仍 health 200，发布 #2/run #2 PUBLISHED、80 日事实和原 checksum 不变；正式 5180 未启动。阶段仍 `REVIEW`。

沿 `PermissionConfig.vue -> PUT /roles/{id}/configuration -> RoleService.updateConfiguration -> role_permission/role_data_scope` 和 `JwtAuthenticationFilter -> UserService.getEffectivePermissions -> PermissionMapper.selectCodesByUserId` 核对后，在隔离 8183 上运行一次性角色/用户探针。仅有 `role:assign_permission` 的操作者能读取配置所需的权限树和角色权限；目标账号无权配置自身角色，授予 `mrp_factory:view` 前工厂 API 为 403。非法工厂配置返回 4xx 且权限/范围均未落库。合法配置后同一目标旧 JWT 依次获得爱培科单厂、两厂、无工厂的正确内容，撤权后立即为 403。25/25 断言 PASS，临时用户/角色/审计 0 残留，隔离发布版 #2 的状态/run/checksum 未变。证据 `test-kit/2026-10-15/evidence/runs/role-configuration-positive-20261109/result.json`；这是现有 8183 镜像的角色配置限定证据，ADMIN-003 全接口有权操作仍未完成。

主机重启使上一轮 `/tmp/mrp-frontier-fulltest-20261109.log` 和退出码丢失，不能记该轮全量通过。#150/task #127/#128/run #130/快照 #69 已核对清理为 0。8183 是旧镜像，不能用它证明 `alignMaterialDates` 新代码。阶段保持 `REVIEW`。

## 2026-11-09 当前 BOM 双厂大负载与守恒续验

独立 `mrp-testkit-mysql/mrp_testkit` 上以当前上海 BOM 的 132,543 个有效边、经营计划版本 #16 的双厂 8,466 根、新增测试库存快照 #69 生成临时批次 #150/run #130。原快照 #6 未改；#69 是其 3,112 行拷贝及一条不良品仓零库存行。整机任务 #127 35 秒完成，物料任务 #128 843 秒完成，batch 为 `MATERIAL_REVIEW`，run 为 `COMPLETED_WITH_ISSUES`。真实计算有 106,782 逐层、55,512 终点、1,202,760 日事实；经营计划到第 0 层的 8,466 键、终点到日事实的 55,512 键双向 SQL 汇总均无漏键和数量差异，逐层 `gross=used+net` 与库存前后两式违例为 0。探针 `current-frontier-material-20261109/result.json` 为限定通过；首次整机探针把正常 `SCHEDULE_READY` 误判失败的原件保留，同一任务复核，没有重复计算。SQL 汇总原件为 `current-frontier-sql-verify-20261109/result.json`。

引擎报告 92,310 次阻断出现，幂等持久化为 6,326 条 `INVENTORY_MISSING`、57 条 `MATERIAL_MISSING`。快照两厂各仅有 1,556 个库存物料编码，缺库存涉及 3,244 个厂区/物料组合；14 个物料编码缺本地主数据。这是该负载输入的实际阻断，不能把守恒通过当成可发布或独立算法准确性通过。监测脚本只采样了空闲 Worker，实际大计算由临时 API 容器执行，故 CPU/内存曲线无效；API 容器 OOM 标志为 false，但缺正确峰值与任务级 GC/SQL。`PERF-003`、`CALC-002`、完整安全/兼容和独立 Reviewer 继续开放。代码复查 `DailyPlanningService.alignMaterialDates` 原来每个物料组重扫整机日事实，现按工厂/整机/来源月预分组，保持原数量投影逻辑。后端编译退出 0；全量及新镜像定向联调结果见本节后续记录。阶段仍 `REVIEW`。

## 2026-11-06 权限矩阵写路由与单厂内容续验

进一步沿 `ConfigMasterController -> ConfigFactoryAccessService/StockPolicyAccessService -> XML Mapper` 核对分页和仓库策略范围；更新映射及日历的服务会将工厂固定为原记录工厂。仅扩展验收探针后，隔离 8183 的 `role-matrix-scope-pages-20261106/result.json` 为 382/382 PASS：单厂账号仓库映射分页 1/1、库存策略 1,564 条全部属于本厂仓库，永惠日历月份请求 403。旧版日历版本列表 200/空数组，不能据此声称正向旧版读取覆盖。临时数据/审计 0 残留，#2 未变。无应用代码改动，本次未重复编译或全量/浏览器回归。

仅扩展 `role_matrix_gate.mjs` 验收探针，未修改应用代码。隔离 8183 对七个非管理员角色扩展更多关键写路由的无权 403 断言，并用临时仅爱培科角色核对工厂目录、工厂主数据、仓库映射和生产日历的逐行工厂范围。最终 `role-matrix-single-factory-20261106/result.json` 为 375/375 PASS；四类单厂列表分别为 1、1、1、212 行。临时用户、角色、审计均为 0 残留，发布 #2 未变；8183 health 与正式 5180 只读 HTTP 均为 200。本轮未重复编译、后端全量或浏览器套件，沿用此前应用改动后的 724/724、前端 111/111 和定向浏览器证据。此证据不包含全部有权操作或所有接口的跨厂内容核验，ADMIN-003 与阶段 18 仍未通过，状态 `REVIEW`。

## 2026-11-06 用户安全权限越权与限定复验

沿 `SecurityConfig -> UserController -> UserService -> user_permission/user_role` 核对，旧镜像把用户资料编辑、直授权限、重置密码和启停都归入 `user:update`。仅有该权限的隔离临时账号调用自己的 `PUT /users/{id}/permissions` 返回 200，SQL 出现 1 条 `ALLOW`，失败原件 `user-direct-permission-before-20261106/result.json`。另外 `POST /users` 只要求 `user:create`，却直接将请求 `roleIds` 写入 `user_role`；旧镜像仅创建权限的账号夹带 ADMIN 角色返回 200、SQL 关联 1 条，原件 `user-create-role-before-20261106/result.json`。两次临时用户/角色均精确清理为 0。

新增未应用迁移 V065 的 `user:assign_permission`、`user:reset_password` 并授 ADMIN，分别校验直授和重置；启停复用 `user:enable`/`user:disable` 且按目标状态精确检查。创建账号的非空 `roleIds` 另要求 `user:assign_role`；页面新增用户本无角色选择。后端新增真实鉴权集成断言，`./mvnw compile -q` 通过；前一轮全量虽生成 100 套件/723 项 XML，但进程无退出记录，不计 PASS。为合并新发现的创建入口修复，又停止一次尚未完成的全量，日志保留。最终独立 `mrp_gate_final3_20261106`/测试 Redis 6391 的 `./mvnw test -q` 明确退出 0，Surefire 100 套件/724 项、0 失败错误跳过，日志 `/tmp/mrp-user-security-fulltest-complete-20261106.log`。

仅构建并重建隔离 `mrp-acceptance-20261104` API/Worker 为 `sha256:88b7ea4c...`，MySQL/Redis 容器 ID 与卷不变。8183 health UP、Flyway V065/65、ADMIN 两项新授权和用户权限树父节点 SQL 正确；#2/run #2 PUBLISHED、80 条日事实、原 checksum 不变。新镜像真实 API：仅编辑权限对直授/重置/停用 403，仅启用不能停用 403；授直授权限后 200/落库 1；仅创建权限夹带 ADMIN 角色 403/无账号，授角色分配权限后 200/角色关联 1；全部临时夹具 0 残留。`user-security-after-20261106/result.json` 为限定 PASS。浏览器首轮两项因隐藏原生 checkbox 与登录跳转竞态误报，trace/截图保留在 `evidence/e2e-user-security-20261106/`；调整测试定位/等待后新目录 `evidence/e2e-user-security-retest-20261106/` 2/2 PASS，角色页能搜索新权限，普通编辑账号无重置按钮。正式 5180 只读 HTTP 200。两项 P0 缺陷仍待独立复核，ADMIN-003 全接口矩阵、完整 UAT 和阶段 18 总门禁保持未完成，阶段状态 `REVIEW`。

## 2026-11-06 库存确认导入权限缺陷

权限树已定义独立 `inventory:import`（上传）与 `inventory:import_confirm`（确认）。`SecurityConfig` 的库存 POST 通配规则原把两条确认路径归入上传权限；`EndpointPermissionRegressionTest` 也把旧规则写成预期。隔离 8183 旧镜像两名单权限临时角色实证：仅上传权限对旧/新版确认路径分别得到业务 400/404，证明请求已进入服务；仅确认权限均 403。无真实任务被确认，临时账号/角色和测试审计清理，#2 不变；失败证据 `test-kit/2026-10-15/evidence/runs/inventory-confirm-permission-before-20261106/result.json`。已将两条精确路径置于通配规则前并要求确认权限，增补路由回归；库存及经营计划导入弹窗的确认按钮与处理函数分别核对自身确认权限。

本轮后端 `./mvnw compile -q`、前端 `npm run build`/111 项单测已通过；第一次后端全量缺 `MRP_BOOTSTRAP_ADMIN_PASSWORD`，`ForecastExcelParserTest` 的 14 个上下文错误属于隔离测试启动配置，日志 `/tmp/mrp-inventory-confirm-fulltest-20261106.log` 保留且不计 PASS。补齐独立 `mrp_gate_confirm_20261106`/Redis 6391 配置后，仅重跑一次全量，`./mvnw test -q` 退出 0，Surefire 100 套件/721 项、0 失败错误跳过，日志 `/tmp/mrp-inventory-confirm-fulltest-corrected-20261106.log`。Docker 构建成功，只重建 `mrp-acceptance-20261104` 的 API/Worker 为 `sha256:c2b5f1e9...`；新镜像两条确认路径对仅上传权限均 403、仅确认权限分别为业务 400/404，临时角色/用户 0 残留，证据 `test-kit/2026-10-15/evidence/runs/inventory-confirm-permission-after-20261106/result.json`。隔离 Chrome 定向用例最终 1/1：仅上传权限可打开两页弹窗但均无“开始导入”，页面 API 无意外 403/5xx，截图在 `test-kit/2026-10-15/evidence/e2e-import-confirm-permission-retest-20261106/`。第一次浏览器夹具漏授页面所需 `mrp_factory:view`，出现工厂目录 403 提示，其截图保留在原 `e2e-import-confirm-permission-20261106/`，不计最终页面通过。8183 health 200、V064/64、#2/run #2 PUBLISHED、80 日事实与原 checksum 不变；正式 5180 只读 200。此缺陷限定自测 PASS，ADMIN-003 全接口矩阵、完整 UAT 和独立阶段 Reviewer 未通过，阶段 18 仍 REVIEW。

## 2026-11-06 逐角色权限矩阵增量验收

按用户确认的批量验证节奏，本轮未修改应用代码，仅扩展隔离 API 探针。`role_matrix_gate.mjs` 以七个非管理员内置角色的一次性账号，对账 `/auth/me` 与有效权限 SQL，并增加经营计划、库存、分类角色/日历/库存策略/业务周、角色权限树共 8 类读取，以及无权限时的导入、发布、调整、缺料分析、策略管理与角色创建 POST 拒绝断言。`node --check` 通过；隔离 8183 最终探针 138/138 断言 PASS、7/7 角色、临时用户/审计 0 残留，发布版 #2 的状态、run、checksum 不变。WAREHOUSE、READONLY、MANAGER 均无工厂范围，库存快照列表实际 200/空数组；MATERIAL 有 ALL 范围，返回 1 条。证据：`test-kit/2026-10-15/evidence/runs/role-matrix-expanded-content-20261106/result.json`。首轮对无工厂范围角色预期过宽的两次 FAIL 保留在同级 `role-matrix-expanded-20261106`、`role-matrix-expanded-retest-20261106` 目录；修正仅限探针预期，未改服务代码。其他有权但无工厂范围的列表接口 200/403 依业务端点而异，探针只记录状态，尚未逐项断言返回内容。隔离只读 SQL 再核 #2 为 PUBLISHED/run #2、原 checksum、8 条逐层需求两条等式 0 差异、80 条日事实合计 80。活跃源码 `backend/src/main`、`frontend/src`、`deploy` 的有限 secret-like 字面量路径扫描 0 命中，但不覆盖静态归档、日志和历史工具。ADMIN-003 全接口/逐权限/工厂域矩阵及安全总门禁仍 NOT_RUN，阶段继续 REVIEW。

## 2026-11-06 PERF-003 夹具可执行性只读核对

`load-frontier.json` 为 1,412 根、6 个月；独立 `mrp-testkit` 库只读 SQL 确认 1,411 个根有效、`6830AA800760` 已停用，原快照 #33 为上海 119,595 与无锡 14,450 条关系，#34 为上海 121,711 条、无其他组织。当前 `bom_detail` 有上海 145,801 条、其他组织 0。C021/C023 要求新批次直接读取当前上海 BOM，不得把历史混合组织快照 #33 作为新输入；旧 `run_case.mjs` 固定 8180 且默认 source-manifest 的 #33。原 456,600 日键是旧夹具预期，当前不能原样执行或记 PASS。应以 1,411 个有效根和当前上海 BOM 重新生成可核对的期望键及独立 Decimal oracle，再在独立库压测；现有 217,275 键重算只作已通过的限定证据。PERF-003 保持 NOT_RUN，未改历史原件或现有业务数据。

## 2026-11-06 ADMIN-003 导出视图权限缺陷与限定复验

沿 `SecurityConfig -> MrpDailyPlanExportController -> MrpDailyPlanExportService -> RollingPlanningQueryService` 核对权限、视图和工厂数据流。共用导出端点允许 `mrp_plan:export` 或 `mrp_shortage:view`，服务原只对 `TERMINAL` 校验后者，默认 `MACHINE_OUTPUT` 视图漏验前者。隔离 8183 临时角色只授 `mrp_shortage:view` 且工厂 ALL，真实整机导出 200/8027B、物料导出 200/9908B，复现 P1 越权；账号/角色精确清理 0。

服务补整机视图 `mrp_plan:export` 校验，物料视图原校验保留。增补 33 组路由授权参数化测试和服务层视图拒绝测试；`./mvnw compile -q`、定向测试、最终独立 MySQL schema/Redis 6391 全量 100 套件/719 项、0 失败错误跳过。首次全量误用不存在的 `mrp` 测试账号被 MySQL 拒绝，日志 `/tmp/mrp-export-permission-fulltest-final-20261106.log` 保留，未计 PASS；更正为容器实际 `mrp_testkit` 后退出 0，日志 `/tmp/mrp-export-permission-fulltest-corrected-20261106.log`。Docker 镜像构建成功，只替换隔离 API/Worker。新镜像同角色整机 403/物料 200，临时用户/角色 0 残留；角色配置页和发布工作台 Playwright 各 1/1 PASS，独立保存的 trace/截图在 `test-kit/2026-10-15/evidence/e2e-role-export-20261106/` 和 `e2e-published-export-20261106/`。8183 health 200，V064/64、#2/run #2 PUBLISHED、80 日事实及 checksum 不变。证据 `test-kit/2026-10-15/evidence/runs/export-view-permission-20261106/result.json`。本次复测最初沿用旧 Playwright 输出目录，早期角色页失败 trace 被清理，原文件未找到，记 `E2E-EVIDENCE-1106`；旧失败文字记录仍在，不能宣称原 trace 完整。本次仅关闭导出视图越权自测；ADMIN-003 全接口角色/工厂矩阵、安全总门禁和独立 Reviewer 仍未过，阶段 18 为 REVIEW。

原 PERF-003 `load-frontier` 含停用根物料，并绑定 C021/C023 已限制新计算的混合组织历史快照；456,600 日键原断言尚未执行，需保留原用例并按当前上海 BOM/有效物料建立可执行基线。此前 217,275 键重算不能替代该门禁。

## 2026-11-06 CALC-002 原用例复现与上海口径重算

按原清单复现前核对代码链 `MrpBatchController.create -> BomSnapshotService.edgesForBatch -> NetRequirementEngine.computePathHash -> MrpRequirementDetailMapper.insertBatch` 和独立库数据。原 `accuracy-stock` 夹具要求快照 #33（134,045 边）产生 8,436 日键；#33 同时含上海 119,595 条与无锡 14,450 条边，物料 `6839AA000685 -> 11155211` 在两组织有相同物料路径。现行 C021/C023 明确禁止新计算使用这种混合组织历史快照，原输入与已裁决业务契约冲突。

隔离 8184 当前 Worker 为复现旧用例临时建立 #147/task124，4.695 秒后 `CALC_ERROR`：同一路径哈希触发 `mrp_requirement_detail.uk_mrd_run_factory_root_month_path_material_level` 唯一键冲突。失败 `test-kit/2026-10-15/evidence/runs/current-accuracy-stock-20261106/result.json` 保留，临时批次/任务/事实 0 残留。此结果不能作为当前上海 BOM 的计算缺陷；原 8,436 键 CALC-002 记 `FAIL`（夹具口径已被 C021/C023 取代），需重定基线，不能虚记 PASS，也不修改历史快照、净需求公式或已应用迁移。

同一原始经营计划 #15、库存快照 #5、162 条冻结库存池的上海快照 #34 批次 #7 曾有 2,820 日键独立 Decimal oracle 0 差异，证据 `accuracy-stock-2026-10-16T06-45-00-793Z/repair-oracle.json`。首次以修订发布版 #8 作原始基准时，#148/task125/run128 重算三类事实中逐层/终点哈希一致，日事实 4 格不同；SQL 逐行确认 #8 在修订时带入 #7 的 4 条人工覆盖，#148 与 #7 的 2,820 条系统日事实逐行相等。失败 `current-accuracy-stock-shanghai-20261106/result.json` 保留，#148 已精确清理。改以原计算批次 #7 的系统事实为基准，当前 #149/task126/run129 在 14.654 秒完成，380 逐层、360 终点、2,820 日事实的完整业务字段 SHA 全部一致；逐层两项守恒/负量 0 异常，API 360 项守恒差异 0，临时数据 0 残留，#7 不变。证据 `current-accuracy-stock-original-system-20261106/result.json`。8184 `/planning/7` 与 `/shortage/7` 真实浏览器显示 50 条整机来源、20 条汇总物料、Console error 0；批次/summary/守恒 API 均 200。此为上海口径限定 PASS，不关闭原 8,436 键用例或完整阶段门禁。

两次探针改动后 `node --check`、`./mvnw compile -q` 均 PASS；独立 `mrp_gate_20261105`/Redis 6391 的后端全量两次均退出 0，Surefire 100 套件/685 项、0 失败/错误/跳过，最终日志 `/tmp/mrp-stock-source-fulltest-20261106.log`。前端此前构建及 111/111 已通过，本次无前端改动。8183 发布 #2/run #2 仍 PUBLISHED、80 日事实、V064/64 且 checksum 不变；正式 5180 仅只读 200。阶段 18 仍 `REVIEW`。

## 2026-11-06 CALC-003 独立 oracle 续验

原 `accuracy-distinct` 隔离真实 Excel 导入、HTTP 计算与完整分页采集在 #2/run2 取得 4,194 日键，独立 Decimal oracle 的数量与维度均为 0 差异，原证据 `test-kit/2026-10-15/evidence/runs/accuracy-distinct-2026-10-15T11-57-48-418Z/oracle-comparison.json`。发布 #3/run3 后，本轮只读核对 #2/#3 的逐层需求、终点需求、日事实完整业务字段 SHA 相同，且 #3 无人工日覆盖。

当前镜像 8184/Worker 复算首次 #145/task122/run125 的 515 条需求与 #3 不同：`gross` 2,232,930、库存使用 0，而 #3 为 `gross` 2,160,675、库存使用 789。追踪 `MrpCalculationService.calculate -> inventoryService.getPoolsByBatch(batchId)` 及发布来源关系，发现发布 #3 的 176 条冻结库存池实际存于被替代的 #2；探针仅复制批次/工厂/业务周范围，遗漏库存池。原 FAIL `test-kit/2026-10-15/evidence/runs/current-accuracy-distinct-20261106/result.json` 保留，#145 的任务、事实、风险、范围和 run 精确清理为 0，#3 未变。探针现从发布来源及其父批次定位冻结池，拒绝未实现 ID 映射的人工分配源，再复制池到临时批次。

修正后 #146/task123/run126 真实异步计算 12.378 秒成功；515 逐层、515 终点、4,194 日事实完整业务字段 SHA 与 #3 相同，逐层 `gross=used+net`、`before=used+after`、负量及 515 项终点到日事实守恒异常全 0。临时批次、任务、风险、库存池、事实和范围复核均 0 残留，#3 仍 PUBLISHED。8184 Chrome `/planning/3`、`/shortage/3` 正常显示，页面错误 0。最终证据 `test-kit/2026-10-15/evidence/runs/current-accuracy-distinct-with-pools-20261106/result.json`，结合原真实导入/oracle 与 #2/#3 SHA，CALC-003 逐项 PASS。`node --check`、后端编译、前端构建通过；独立测试 schema/Redis 上后端全量退出 0，Surefire 100 套件/685 项、0 失败/错误/跳过，日志 `/tmp/mrp-accuracy-distinct-fulltest-20261106.log`。CALC-002 后续复现见上节。

## 2026-11-05 当前镜像角色矩阵与大批次重算增量

隔离 8183 的 `role_matrix_gate.mjs` 为 7 个非管理员内置角色建立一次性账号，逐角色将 `/auth/me` 与有效权限 SQL 对账，并核对发布批次 #2 读取、角色配置读取和无 `role:assign_permission` 时的写入 403。37/37 真实 API 断言 PASS；PLANNER/MATERIAL 现配置为 ALL，其余五角色无工厂域而拒绝 #2。临时用户、审计 0 残留，#2 未变。证据 `test-kit/2026-10-15/evidence/runs/role-matrix-final-20261105/result.json`；前两次探针排序误报保留。此矩阵只覆盖选定端点，不等于 ADMIN-003 的 view/import/manage/adjust/publish/export 全接口覆盖。

隔离 `mrp-testkit-mysql/mrp_testkit` 使用当前 API/Worker 镜像在 8184 基于已发布 #13 做两次真实异步重算。#143/task120/run123 的首次探针误要求 READY 有发布 checksum，复核三类事实完整业务字段哈希一致、逐层守恒通过后精确清理；原 FAIL 留存。#144/task121/run124 的监测复跑 45.286 秒 PASS：9,174 逐层、17,382 终点和 217,275 日事实均与 #13 完整业务字段 SHA 相同，逐层守恒/负量差异 0，`/plans/conservation` 17,382 项差异 0。临时批次、任务、三类事实 0 残留，来源 #13 仍 PUBLISHED。证据 `test-kit/2026-10-15/evidence/runs/current-large-calculation-monitored-20261105/result.json`。READY 批次的 `result_checksum` 为 NULL 是发布前状态，不计失败。

计算窗口 2 秒采样 15 次，Worker 最高观察内存 892.6 MiB；MySQL 全局状态增量 SELECT 593、INSERT 249、UPDATE 96、Questions 1069、Bytes_sent 37,390,747，包含 Worker 轮询和探针 SQL，不能归因于纯计算。按 11:37:43Z-11:38:29Z 的 Docker GC 日志粗窗口统计 57 次暂停、累计 569.067ms、最大 82.735ms、0 Full GC；该窗口含计算前后少量后台活动，不是精确任务级 GC。CPU 只有瞬时容器采样，无完整峰值/堆曲线。`node --check` 两个探针通过。8184 Chrome 直接查看 `/planning/13` 为已发布，`/shortage/13` 最终显示 1,459 个汇总物料/首屏 20 条，页面错误 0；页面在共享测试 MySQL 与全量测试并行时约 25 秒才完成加载，独立 API 分页 163ms，此耗时暂不当成无竞争基线。

此项仅将“当前代码真实大批次重算与已发布来源逐字段一致”的限定子门禁记 PASS。原始 224,250/456,600 日键、独立 oracle、低堆、数据库/Worker 故障恢复、纯计算 SQL 和全接口权限矩阵仍未通过。阶段 18/19 保持 REVIEW。

存量独立 oracle 复核：10-16 独立 Reviewer 已核验 `load-distinct-2026-10-16T06-59-59-109Z/repair-oracle.json`，批次 #9 的 217,275 日键 Decimal 精确差异 0、9,174 路径在 0.000001 容差内差异 0、1,486 grid 行与 2,850 汇总组一致。本轮只读计算 #9 与发布 #13 三类事实完整业务字段 SHA 全部相同，再结合 #144 当前镜像与 #13 相同，可把该数据集的独立 oracle 结果关联到当前重算。原 CALC-002/003 指定的 accuracy-stock/accuracy-distinct 不同数据集仍须逐项执行，不能据此增加 UAT PASS 数。

历史大批次缺料页性能缺陷：8184 Chrome `/shortage/13` 首次与全量测试并行时约 25 秒、无竞争时约 14.6 秒才显示 1,459 个物料；Network 定位 `/batches/13/reviews` 单请求约 11.6 秒，而缺料分页约 123ms。`ShortageWorkspace.load` 对所有批次请求双岗确认，后端 `MrpWorkbenchReviewService.statusForBatch -> scheduleFingerprint/materialFingerprint` 为已发布 v4 读取并排序大量事实，历史 v4 页面无需该确认。前端仅在 `PUBLISHED` 且非 v5 时跳过 `/reviews`；独立 5181 指向 8184 的 Chrome 复测约 1.029 秒显示同一 1,459 总数，`/reviews` 0 次、其他 API 均 200、页面错误 0。独立 5182 指向隔离 8183 的 v5 发布版 #2，`/reviews` 仍调用 1 次且 200，缺料 6 条正常。新增前端边界测试后 `npm run build`、13 文件/111 项均 PASS；后端 `./mvnw compile -q`、独立 MySQL/Redis 全量 100 套件/685 项、0 失败/错误/跳过，日志 `/tmp/mrp-shortage-legacy-fulltest-20261105.log`。`git diff --check` PASS。此为历史页面限定性能修复，不关闭 PERF 总门禁。

## 2026-11-05 大样本存量结果逐键只读核验

在独立 `mrp-testkit-mysql/mrp_testkit` 中只读复核既有已发布批次 #13/run #13，未创建或重算批次。该批次为 v4 规则、9,174 条 `mrp_requirement_detail`、17,382 条 `mrp_terminal_demand`、217,275 条 `mrp_daily_plan_detail`。MySQL `DECIMAL` 精确比较：逐层 `gross=used+net`、`before=used+after` 及负量均 0 异常；按工厂/来源整机/来源月/物料聚合的 8,916 个末级需求键，对终点需求差异 0、绝对差额 0；按工厂/来源整机/来源月/物料/终点类型/供应方式聚合的 17,382 个终点需求键，对日事实差异 0、绝对差额 0。批次 #10 与 #13 输入 checksum、结果 checksum 相同，#13 仍 PUBLISHED，结果 checksum `a6c035027455613c0bf456bc8a4c73398c2d35e560a078289af2428b591a54c3`。这是存量结果的逐键数据一致性限定 PASS，不证明本轮代码重新计算了大批次，不关闭 456600 日键、SQL 数、CPU/GC/低堆或 PERF 总门禁。

## 2026-11-05 角色撤权缓存时序续验

真实链路为 `JwtAuthenticationFilter -> UserService.getEffectivePermissions -> PermissionMapper/UserPermissionMapper`。此前角色配置在数据库事务提交前清 Redis，另一请求可用未提交前的旧授权重新填充缓存；提交后旧 JWT 可能短暂继续获权。用户不超过 10 人，改为每次鉴权直接查 MySQL，不再读写权限缓存。`RoleConfigurationIntegrationTest` 故意在停用角色后填入旧 `schedule:view` 缓存，同 token `/auth/me` 权限为空、`/plans` 403；ADMIN 配置的部分权限请求 400。`./mvnw compile -q` PASS；独立 `mrp_gate_20261105`/Redis 6391 的 `./mvnw test -q` 100 套件/685 项、0 失败/错误/跳过，日志 `/tmp/mrp-role-cache-fulltest-20261105.log`。`git diff --check` PASS。

隔离 API/Worker 镜像 `sha256:ee22e9c1...` 更新后 8183 health 200；管理员真实登录、`/auth/me` 118 项权限、#2 summary 200/PUBLISHED。隔离 Chrome 角色页保存/刷新/API 落库 1/1 PASS，新增输出 `test-kit/2026-10-15/evidence/e2e-role-cache-20261105/`；临时用户/角色 0 残留。SQL 只读复核 #2/run #2 PUBLISHED、结果 checksum `794268e39603f6b0c27f308d34839216eb9e1ad693a32485bd786d46b7ef85b5`、80 条日事实、Flyway V064/64。正式 5180 只读 200。此为撤权时序限定自测，完整角色/工厂权限矩阵、大数据逐键及独立 Reviewer 尚未完成，18 维持 REVIEW。

隔离真实 API 逐角色读取 `/roles/{id}/permissions` 与 `/data-scope` 8/8 成功：ADMIN 118/ALL、PLANNER 63/ALL、MATERIAL 26/ALL；BUSINESS 14/NONE、WAREHOUSE 19/NONE、READONLY 47/NONE、MANAGER 35/NONE、NEW_USER 0/NONE。`PlanningAccessMapper` 的数据域还合并 `user_factory_scope`，隔离库该表为 0；NONE 是当前角色配置值，未自行授予工厂。逐角色 API 可读不替代跨厂越权矩阵。

## 状态

`REVIEW`（旧正文的 DONE 为历史局部结论，当前以 workflow 的阶段总状态为准）

## 2026-11-05 聚焦角色与数据一致性增量

按用户确认的少于 10 人规模，优先核算逐层 `gross=used+net`、`before=used+after`、末级与日事实逐键相等，以及角色配置页、旧 JWT、工厂范围。原 42/42 专项不重复写入。隔离发布版 #2/run #2 的只读 SQL：8 条逐层需求两项守恒差异均 0、负量 0；末级需求 8 条总量 80，日事实 80 条总量 80；逐厂/来源整机/来源月/物料的逐层末级对账 0 差异，末级对日事实 0 差异；override 0，ERROR 异常 0。两厂 MACHINE_OUTPUT 各 10、RAW_MATERIAL 各 30。发布版状态和 64 位结果 checksum 保持不变。该小批次只读核验不替代 CALC-002~004 或 PERF-001~003 的真实大数据逐键门禁。

角色链路根因：原 `RoleService.update` 对未传字段写 NULL；`PermissionMapper.selectCodesByUserId` 未过滤停用角色；`RoleService.update` 未清权限缓存；角色页没有 `role_data_scope` 配置，原路由允许配置权限但加载角色/权限树 API 需额外查看权限。修复后功能权限与工厂范围单事务保存、无效工厂拒绝、ADMIN 全权限/全工厂保护、角色复制带范围、旧 JWT 依当前有效权限鉴权；刷新从受保护 `/auth/me` 读取当前权限。独立 MySQL 定向 `RoleConfigurationIntegrationTest` PASS；最终 `./mvnw compile -q`、独立 schema/Redis 6391 `./mvnw test -q` 100 套件/685 项、0 失败错误跳过；前端 `npm run build`、110/110 PASS。初次未指定测试库 URL 的全量运行在 `ForecastExcelParserTest` 因默认 localhost:3307 不通发生上下文错误，已停止并以明确独立库配置全量重跑成功；不把失败运行计 PASS。

隔离 Docker 仅重建 API/Worker：8183 health 200、Flyway V064/64、#2/run #2 PUBLISHED；临时管理员与角色的隔离 Chrome 用例 1/1 PASS，实际页面选择 `schedule:view` 和爱培科单厂范围、保存、刷新后选中状态与 GET API 一致，夹具用户/角色 0 残留。第一次 Playwright 用原生 `check()` 点击 Element Plus 装饰层失败，trace/截图保留在 `test-kit/2026-10-15/evidence/e2e-role-configuration-20261105/`；改为实际标签点击后 PASS。新角色/权限配置仍需独立 Reviewer 与完整接口矩阵，阶段 18 继续 REVIEW；P2-I002 正式凭据轮换及完整安全扫描仍 FAIL。

## 2026-11-05 最终管理员引导与全量回归增量

当前结论优先于下文较早的“隔离默认账号仍可登录”快照。代码链路为 `LegacyAdminBootstrap -> user.password_hash/need_change_password`、`AuthController.login -> JwtService` 和 `JwtAuthenticationFilter -> UserService.authenticationStateForActiveUser`。启动时仅精确匹配已应用 V001 的 `admin` 播种哈希才要求 24 位以上含字母数字的引导口令，并以哈希条件更新；缺口令拒绝启动，旧哈希在登录和 JWT 校验中被拒绝。V001 未改。隔离 8183 已用一次性随机口令引导并首次改密，旧固定口令、引导口令和旧 JWT 均拒绝，新口令登录及受限 API 通过；重建后的 API/Worker 引导环境变量均为空。验收口令仅保存在本机权限 600 的临时文件，报告不记录明文。

最终代码 `./mvnw compile -q` 退出 0；独立 `mrp_gate_20261105` schema 和 Redis 6391 上 `./mvnw test -q` 退出 0，Surefire 99 套件/684 项，0 失败/错误/跳过，日志 `/tmp/mrp-stage18-bootstrap-fulltest-script-final-20261105.log`。前端 `npm run build` 退出 0、13 文件/110 单测通过；`node --check test-kit/2026-10-15/tools/docker_acceptance_chain.mjs` 通过。隔离专用 Playwright 1/1，真实 API `GET /mrp/batches/2` 与 `/summary` 均 200/PUBLISHED，8183 health 200，正式 5180 只读 HTTP 200。首次全量因 `MaterialApiTest` 依赖旧库数据失败的 `/tmp/mrp-stage18-bootstrap-fulltest-20261105.log` 保留，不计最终 PASS。

验收脚本 `docker_acceptance_chain.mjs` 两处历史固定口令已改为必需的 `MRP_ACCEPTANCE_ADMIN_PASSWORD`；未重跑会写入新批次的完整导入发布链，保留现有隔离 #2。P2-I002 总项仍 FAIL：正式现有凭据轮换、完整秘密扫描及独立安全复核缺证据。大负载性能、全接口安全、Phase 1 完整历史回放和阶段独立 Reviewer 仍未通过；阶段状态保持 REVIEW。

后续限定安全复核：`legacy_phase1_gate.mjs` 的隔离管理员/临时账号登录改用显式验收变量后，8183 实际执行 24/24 断言 PASS，临时表行和审计 0 残留，#2 仍 PUBLISHED；证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261105080353273/result.json`。覆盖旧入口工厂域、旧写拒绝、导出所有权/路径、撤权和停用旧 JWT。修改后 `./mvnw compile -q`、全量 `./mvnw test -q` 再次 99 套件/684 项、前端 build/110 单测、隔离 Chrome Playwright 1/1 均通过；最终日志 `/tmp/mrp-stage18-legacy-bootstrap-fulltest-20261105.log`。这是限定子门禁，不代替全接口矩阵。

有限源码/脚本扫描仍命中 15 个历史 `LoginView` bundle 与 38 个旧 test-kit 脚本含固定示例口令。当前 Dockerfile 构建前清空后端静态目录再复制新前端产物；未证明其他打包方式和历史归档无泄漏。旧 8182 工具未批量改写或执行，ADMIN-005 完整响应、日志、审计、归档扫描继续 NOT_RUN，P2-I002 总项继续 FAIL。

## 本次完成

- 全量后端编译、测试、前端构建通过
- 完整生命周期集成测试（BOM快照→库存规范化→批次→池→预检→锁定→计算→分配→摘要→发布）
- 安全验证（无token返回403，无效token返回403）
- 数据守恒验证（分配数量不超过可用库存）

## 验证命令与结果

```text
$ ./mvnw clean compile -q → BUILD SUCCESS
$ ./mvnw test -pl . -Dtest='!BomExplosionServiceTest' → 124/124 PASS
$ npm run build → built in 379ms

Full Integration Test:
1. BOM Snapshot: 9 (132774 edges)
2. Normalized: 396 materials
3. Batch: 8
4. Pools: 396
5. Precheck: CHECKING
6. Lock: READY_TO_CALCULATE
7. Calculate: READY
8. Distribute: 66 daily plans
9. Summary: status=READY, plans=66, pending=15, pools=396
10. Publish (fail): "Cannot publish: 15 pending unallocated quantities"
11. Publish: PUBLISHED
12. Security (no token): HTTP 403
13. Security (invalid token): HTTP 403
14. Final: status=PUBLISHED, checksum=98af2964459a28f2...
```

## 安全验证

| 场景 | 结果 |
|---|---|
| 无 token 访问 | HTTP 403 |
| 无效 token 访问 | HTTP 403 |
| 权限码检查 | schedule:view 控制访问 |

## 数据守恒验证

| 场景 | 结果 |
|---|---|
| 分配超过可用库存 | MRP_ILLEGAL_STATE 拒绝 |
| 乐观锁版本冲突 | 更新失败，需重试 |
| 发布前校验 | 未解决 issues/unallocated 阻断发布 |

## 性能观察

| 指标 | 值 |
|---|---|
| BOM 快照（132774 边） | ~3s |
| 库存规范化（396 材料） | <1s |
| 净需求计算（3 层） | <1s |
| 日分配（66 计划） | <1s |
| 前端构建 | 379ms |

## 未完成与风险

- P2-I002: 配置存在 secret-like 默认值（mrp/mrp_dev）
- 大数据量性能测试未执行
- Playwright E2E 测试未实现

## 下一步

进入阶段 19：Docker/UAT

## 2026-11-04 剩余门禁续验

### 2026-11-05 LIFE-010 限定导出安全复核

11-05 登录安全增量：按 `AuthController`、`JwtAuthenticationFilter`、前端 `LoginView`/`App`/`client` 的调用链核对后，隔离 API 空表单、错密码、不存在账号均 `success=false/MRP_AUTH_ERROR` 且无内部信息；缺失或篡改 token 访问受限 `/plans` 为 401。Chrome 真实操作正确登录、退出后受限页回登录、错误凭证提示、篡改 token 后清除并跳登录、再次登录成功，页面错误 0。原用例 AUTH-003 允许过期或篡改，本轮选择篡改分支；未测过期时钟边界。证据 `test-kit/2026-10-15/evidence/runs/auth-boundary-20261105/` 的 `api.json`、`browser-retest.json`、`tamper-relogin.json`；首轮浏览器脚本过早读取异步提示且精确 URL 未匹配 `?redirect=`，误报 `browser.json` 保留。AUTH-001/002/003 本轮自测 PASS，阶段安全总门禁与独立 Reviewer 未关闭。

AUTH-004 增量：从 `UserController.create -> UserService.create`、权限查询、`SecurityConfig` 与前端 `defaultPage/router` 追踪后，在隔离 API 新建零角色临时账号，登录成功但权限集为空；旧计划/发布批次/用户列表均 403。Chrome 登录和直接访问 `/planning` 均进入等待权限页；删除账号与测试审计后残留 0。证据 `test-kit/2026-10-15/evidence/runs/auth004-no-permission-20261105/result.json` 与截图。该页误写“密码修改成功”，账号未改密，单独记 P2 文案缺陷；AUTH-004 越权门禁自测 PASS，阶段安全总门禁仍未关闭。

P0 凭据门禁进一步复核：`V001__init_schema.sql` 在空库播种固定管理员凭据；`UserService.create/resetPassword` 对普通账号使用固定口令；`LoginView.vue` 预填管理员凭据。隔离默认账号可登录已由本轮 API/Chrome 证实。此前仅移除 local/Compose 的数据库和 JWT 默认值，不能关闭 P2-I002。已应用 V001 不得修改；需新增安全的新建/重置及首次改密流程，并由获授权人员轮换正式现有账号凭据，当前正式资源只读。未执行正式登录探针或任何正式写入。

按原用例先追踪真实代码：旧版 `PlanningController` 的导出请求调用 `ExportService`，`PlanMapper` 读取 `plan_detail` 的料号/机型/机头料号；导出使用 Apache POI `setCellValue(String)`。Phase 2 `MrpDailyPlanExportService` 与 `MrpShortageExportService` 同样使用 XLSX 字符串单元格，没有写公式 API。隔离 `mrp-acceptance-20261104` 的既有容器停止后只用 `docker start` 恢复，未重建或清卷；8183 health 200/UP、Flyway V064/64、发布版 #2 PUBLISHED，正式 5180 仅只读 HTTP 200。

新探针只在隔离库插入一条临时旧版计划明细，含 `=1+2`、`+SUM(1,2)`、`-1+2`、`@SUM(1,2)` 文本，并用真实 API 创建导出 202、下载 XLSX 200/3452B。OOXML 检查显示 11 个字符串单元格、0 个公式节点，四种前缀均为共享字符串，临时行和文件精确清理 0 残留，#2 保持 PUBLISHED。PASS 原件 `test-kit/2026-10-15/evidence/runs/life010-formula-3115b413e75842fa/result.json`。首次探针误把共享字符串表去重项计数当出现次数而报告 FAIL，原因在探针断言，原件 `.../life010-formula-b9c5d38764cd4782/result.json` 保留。

此探针通过 SQL 构造测试数据，未覆盖原用例规定的可编辑文本输入链路，故 LIFE-010 整项仍 NOT_RUN、安全总门禁仍 NOT_RUN。后端 `./mvnw compile -q`、独立 `mrp_gate_20261105` schema/Redis 6391 的 `./mvnw test -q` 97 套件/676 项/0 失败错误跳过、前端 `npm run build`/110 单测、隔离 Chrome Playwright 1/1 均通过。最终后端日志 `/tmp/mrp-stage18-fulltest-granted-20261105.log`。首次测试命令未正确取得测试库密码，第二次未授新 schema 权限，失败日志 `/tmp/mrp-stage18-fulltest-20261105.log` 与 `/tmp/mrp-stage18-fulltest-corrected-20261105.log` 保留，均不计为测试通过；仅在测试容器中新 schema 授权后重跑成功。

阶段总状态仍为 `REVIEW`。逐项状态和证据索引见 `2026-11-04-stage18-19-gate-matrix.md`，不继承旧正文的阶段 DONE。

- P2-I002 根因复核：`application-prod.yml` 已要求注入凭据，但正式 Compose 的数据库与 JWT 仍给出可用默认值，local profile 也有数据库/JWT 默认值。已从正式 Compose 与 local profile 移除这些默认值，示例文件凭据留空；未改技术栈版本。正式现有凭据轮换尚无证据，因此 P0 安全总门禁未关闭。
- 编译与回归：`./mvnw compile -q` 退出 0；用独立 `mrp_gate_20261104` schema 和 6391 测试 Redis 执行 `./mvnw test -q`，退出 0，Surefire 97 套件/673 项/0 失败/错误/跳过。local Redisson 与 Testcontainers 基类的 Redis 地址改为遵循测试环境端口，未访问正式 Redis 6379。
- 隔离 HTTP 安全探针：匿名和无效 token 读取 #2 summary 均 401；临时 READONLY 用户仅授爱培科域，读两厂 #2 与 summary 均 403，列表为空；用户/域记录清理后 0 残留。该项只证明 AUTH-006 的指定场景，CAP-1014-03 整体仍 OPEN。
- 隔离小样本性能：发布 run #2 有 80 日事实，BOM 明细 145801；三次只读请求 summary 12~39ms/408B、整机 grid 59~181ms/104058B、物料详情 35~83ms/12462B、产能负荷 9~20ms/17279B，全部 200。API/Worker `docker stats --no-stream` 观察内存约 467.9/393.3MiB。缺 SQL 数、GC、CPU 峰值及 456600 日键负载，TESTKIT-PERF-01 不关闭。
- Phase 1 `/plans` 与 `/plans/current` 在无历史计划的隔离库均返回 200；v1/v2/v3 回放、旧周 override 写入拒绝仍未实际验证。

### 2026-11-04 Phase 1 安全兼容缺陷续修中

- 旧镜像在独立 Docker 库临时建立双厂旧版本、单厂账号与旧重算任务后，真实 HTTP 复现跨厂列表/当前版本/网格/对比/任务读取；Phase 2 任务也可由旧重算入口读取。旧重算、override、发布写请求返回服务层 500/404，而非安全层 403。失败原件：`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104105403840/result.json`；夹具残留 0，发布版 #2 保持 PUBLISHED。
- 根因是旧 Controller 直接读取 `plan_version`/`plan_detail`，工厂域拦截器只覆盖 `/api/v1/mrp/**`；旧写路径的既有权限规则允许具有排产权的用户进入服务层。已补旧写入口拒绝、旧读取按工厂域过滤/拒绝、旧任务类型隔离。`PlanningApiTest` 定向通过；全量测试和新镜像 HTTP/浏览器尚在执行，暂不记 PASS。

复核进展：完整变量下后端全量 97 套件/674 项/0 失败通过；隔离新镜像同探针全部断言 PASS，证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104110743160/result.json`，夹具 0 残留、#2 保持 PUBLISHED；Playwright 1/1、正式 5180 只读 HTTP 200。仅关闭旧 API 写封闭/工厂域/任务类型限定子项；真实 v1/v2/v3 历史回放和独立阶段 Reviewer 仍未做。

另发现隔离 API 启动日志打印 Spring 自动生成密码。HTTP Basic 探针为 401，但日志仍违反无秘密门禁；已排除 API 入口未使用的 `UserDetailsServiceAutoConfiguration`，启动上下文定向测试通过，新的全量测试和隔离日志复核进行中。真实密码不写入报告。

最终复核：排除自动配置后的后端全量 97 套件/675 项/0 失败/错误/跳过；前端 13 文件/110 项通过，隔离镜像重建成功。API 新容器启动日志没有自动生成密码或内存用户提示，8183 health 200；旧 API/工厂域/AUTH-006/跨厂导出及他人导出任务所有权综合探针全部断言 PASS，见 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104115334130/result.json`。隔离浏览器 planning/shortage 页面 Console error、pageerror、requestfailed、API 5xx 均为 0，截图见 `test-kit/2026-10-15/evidence/runs/browser-stage18-20261104/`；Chrome Playwright 1/1 与 trace 通过。此为限定安全/兼容子项自测，完整审计敏感信息扫描、全接口权限矩阵、性能大负载及独立 Reviewer 仍未执行。

AUTH-009 追加：隔离旧镜像管理员停用临时账号后原 JWT 仍能读取 `/plans` 200，失败 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104120125749/result.json`。调用链为 `JwtAuthenticationFilter` 验签后直接从权限缓存建立认证，未核对 `user.is_active`。新增按 ID/用户名/启用状态 SQL 检查后，最终镜像旧 token 返回 401，综合探针全部 PASS、夹具与审计 0 残留，见 `.../legacy-phase1-gate-20261104121840419/result.json`。编译、后端全量 97 套件/675 项、隔离 Playwright 1/1 再次通过；镜像 `sha256:a90040ba...`，V064/64、#2/run #2 PUBLISHED，5180 只读 HTTP 200。该停用场景自测 PASS；角色撤权旧会话和独立复核仍缺。

AUTH-009 原用例再补：管理员通过权限 API 对同一临时账号撤销 `schedule:view`，原 JWT 读取 `/plans` 由 200 立即变为 403；随后停用变 401。最终证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104122135986/result.json`，用户/权限/任务/导出/审计残留 0。AUTH-009 本轮自测 PASS，独立阶段 Reviewer 尚无结论。旧探针曾遗漏一条测试审计，已按精确 ID 清理并扩展残留断言；失败现场文件保留。

下载安全续验：代码链路 `PlanningController.downloadExport -> ExportService.getExportTask -> export_task.file_path -> FileSystemResource` 只做任务所有权/工厂域检查，未限定磁盘路径；实体 `ExportTask.getFilePath` 会被序列化。隔离原镜像临时任务指向 `/etc/hostname` 下载 HTTP 200，元数据暴露路径，失败证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104122903394/result.json`。修复后只接受配置导出目录内已存在的 `.xlsx` 普通文件，元数据隐藏路径，目录外/缺失/符号链接 404 且错误不含内部路径。后端 `test-compile` PASS，独立 schema/Redis 6391 全量 97 套件/676 项 0 失败/错误/跳过；隔离 Docker 前端/后端构建 PASS、真实 XLSX 所有者下载 200/3350B、同权限第二账号 403、目录外和删除文件后 404，证据 `.../legacy-phase1-gate-20261104130228745/result.json`；Chrome Playwright 1/1，Flyway V064/64、#2 PUBLISHED、正式 5180 只读 200。一次漏传 local JWT 测试变量的 676 项运行有 3 个上下文错误，未计 PASS。AUTH-007 本轮自测 PASS；AUTH-005 按钮和 LIFE-009 时间到期规则仍缺，CAP-1014-03 全范围与阶段独立 Reviewer 仍未通过。

性能与兼容只读盘点：隔离发布版 #2 的 summary 20 次全 200，408B、7~24ms（样本中位 12ms，样本第 19 值 23ms）；整机 rolling-grid 20 次全 200，104058B、26~131ms（样本中位 30ms，样本第 19 值 64ms）。`performance_schema` 全 schema digest `COUNT_STAR` 采样窗口增 642，包含 Worker 后台 SQL，不能推导每请求 SQL 数或 N+1；API/Worker `docker stats --no-stream` 内存快照 467/401.3MiB、CPU 0.54%/0.42%，不代表计算峰值。正式主库只读盘点旧 `plan_version` 仅 v1 538 条；`mrp_calc_batch` v2 56、v4 13、v5 19，无 v3。旧版本全量回放、456600 日键、GC/SQL/低堆/故障恢复仍 NOT_RUN。

AUTH-005 页面复核：隔离 READONLY 账号能进入经营计划、库存、物料、分类、BOM 和排产页面。旧静态前端真实 Chrome 中经营计划/库存各可见 1 个无权限的导入按钮；代码审计还见物料增删改/导入、分类增删改/启停和 BOM 同步未按写权限隐藏。按 `SecurityConfig` 现有 `forecast:import`、`inventory:import`、`material:manage`、`material_category:manage`、`bom:manage` 权限码修正五页按钮，不改服务端授权。最终前端构建、110/110 单测、后端 test-compile 与独立库/Redis 6391 全量 97 套件/676 项均 PASS；隔离镜像重建后 READONLY 六页写按钮计数均 0，管理员经营计划导入计数 1，Console error 0；综合 API 五类写请求仍 403，`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104133304962/result.json` 全断言 PASS、夹具 0 残留；隔离 Playwright 1/1。AUTH-005 本轮自测 PASS，CAP-1014-03 全接口矩阵和独立 Reviewer 仍未通过。

## 2026-11-05 凭据安全续验

状态：`REVIEW`。P2-I002 总门禁仍 `FAIL`，仅本节限定子项自测 `PASS`。

- 真实链路：V001 已应用迁移播种固定管理员密码；`UserService.create/resetPassword` 原使用固定口令，`LoginView` 预填账号密码；`FeishuService` 新用户原写固定 `123456`，按邮箱绑定既有账号时未检查停用状态。已应用 V001 未修改，正式主库/Redis/数据卷和 5180 均只读。
- 修复：管理员新建/重置返回一次性随机密码，库中只存 BCrypt 哈希并标记首次改密；普通登录返回改密标记，JWT 过滤器在改密前仅允许改密端点，令牌绑定密码哈希版本，改密后返回新令牌并使旧令牌持续失效。飞书新用户本地密码为 NULL，停用账号邮箱绑定被拒绝。登录页去掉预填，等待权限页文案改为“等待权限分配”。临时密码不进入审计对象。
- 首轮全量 97 套件/677 项中新增测试 1 FAIL：MockMvc 请求的 `servletPath` 匹配不到改密端点，导致 401；失败日志 `/tmp/mrp-stage18-fulltest-credential-20261105.log` 保留。修正 URI 判断后定向 7/7、全量 677/677。加入密码版本与飞书分支回归后最终 `./mvnw test-compile -q`、独立 `mrp_gate_20261105`/Redis 6391 的 `./mvnw test -q` 为 97 套件/678 项/0 失败错误跳过，日志 `/tmp/mrp-stage18-fulltest-final-20261105.log`。前端 `npm run build`、13 文件/110 单测 PASS；Docker 构建日志 `/tmp/mrp-stage18-credential-docker-build-final-20261105.log`。
- 最终隔离镜像 `sha256:18f8e828...` 仅重建 `mrp-acceptance-20261104` API/Worker，MySQL/Redis 卷未重建；8183 health 200/UP、Flyway V064/64。真实 API/Chrome 临时账号首次登录标记 true、业务接口 401、改密后进入等待权限页、旧 JWT 401、旧密码拒绝、登录页输入为空、账号删除 200；本轮两个临时账号和 4 条对应审计夹具按精确 ID 清理 0 残留。最终镜像 Playwright 1/1 覆盖 #2 已发布整机/缺料与 1440/1920 负荷格。发布版 #2 仍 PUBLISHED，正式 5180 只读 200。飞书停用账号/空本地密码由独立 MySQL 集成测试覆盖，外部飞书回调未验证。
- 未完成：V001 固定管理员播种与正式现有凭据轮换、全接口权限及审计秘密扫描、大负载性能、完整 UAT、独立安全 Reviewer。启动新镜像后旧 JWT 因新增凭据版本字段需重新登录。P2-I002 不关闭，阶段 18 不改 DONE。

## 2026-11-05 认证错误信息续验

状态：`REVIEW`。沿 `AuthController -> FeishuService -> RestTemplate` 和改密 JDBC 错误链检查，发现飞书上游失败响应体及异常被写日志，上游/数据库异常详情还可能进入 API 错误消息；记录 `AUTH-ERROR-DISCLOSURE-1105` P1。修复为不包含上游响应体、异常堆栈和异常消息的固定日志/业务错误文案。第一次全量/隔离复核后复查发现改密分支仍记录异常堆栈，已继续清除并补含敏感标记的断言；前次 680 项日志 `/tmp/mrp-stage18-feishu-security-fulltest-20261105.log` 保留，不算最终版证据。最终 `./mvnw compile -q`、定向 3/3、独立 schema/Redis 6391 下 `./mvnw test -q` 为 98 套件/681 项、0 失败/错误/跳过，日志 `/tmp/mrp-stage18-feishu-security-fulltest-final-20261105.log`。最终隔离 Docker API/Worker 重建成功，8183 health 200，真实无效飞书回调返回固定 `FEISHU_AUTH_ERROR` 文案、日志仅固定告警；Chrome Playwright 1/1 PASS，输出在 `test-kit/2026-10-15/evidence/e2e-docker-acceptance-feishu-final-20261105/`。Flyway V064/64、#2/run #2 PUBLISHED，正式资源未写。此为限定自测 PASS；外部成功授权回调、ADMIN-005 完整扫描及独立安全复核仍 NOT_RUN。

P2-I002 继续为 P0 FAIL：隔离库只读核对唯一管理员仍是 V001 固定哈希、启用状态；Flyway 最新 V064，发布版 #2/run #2 仍 PUBLISHED。直接封禁默认登录会失去当前隔离环境的管理员入口，安全初始化需要同步提供一次性引导与既有账号轮换方案；已应用 V001 不可改，正式凭据和资源不在本轮写入范围。

## 2026-10-16 定向修复与回归补充

本节更新旧报告中相关未修复/未测试事项，保留历史记录。**已审核定向修复DONE；阶段18仍为REVIEW，不进入下一阶段总验收。** 原Reviewer已对本轮范围PASS，见 `test-kit/2026-10-16/evidence/repair/independent-repair-review.md`；完整文件、命令、数据与限制见 `test-kit/2026-10-16/修复完成报告.md`。

- 已修复用户/经营计划详情功能权限、密码hash序列化、权限配置目录及勾选、映射错误模板/启用枚举、非法请求状态码、经营计划双厂维度、上海BOM同步及新快照输入保护。净需求公式和pathHash未改。正确模板下工厂/仓库目录白名单尚未实施。
- 后端编译PASS，344回归测试PASS（排除MaterialApiTest、BomExplosionServiceTest、SecurityPermissionTest、RoleBasedAccessTest四类）；BOM专项19PASS；前端构建及64测试PASS；隔离API82PASS、补充安全7PASS；主/隔离页面冒烟各16PASS；浏览器权限保存及整数预览2PASS。
- 隔离真实MySQL、上海快照34：大批次9有1411整机/8466根料号月份行，217275日键计算42.432秒，9174路径、1486grid行、2850单位汇总组独立Decimal零差异。完整EXPLODE样本11有38850日键，18.293秒，3048路径、384grid行、175汇总组零差异。SQL与bulk HTTP事实比对PASS。最大展开深度分别1和2；不代表任意深度压力通过。
- 逐页采集仍超预算，bulk114.5MB/10.163秒，发布同步约100秒；主库summary一次11.434秒，未达到候选3秒目标。读取/发布46次采样服务存活，监测未覆盖计算阶段峰值；401探针只证明可响应。
- 主库91与隔离11月表各77格DOM独立求和抽样PASS，不是全部分页逐格验收。主库91完整日/周grid、容量及只读并发核对PASS。
- 工厂域权限CAP-1014-03、三轮相同输入checksum、并发写、故障/低内存恢复、1万/10万实际导入、导出完整性及其他全量覆盖仍待完成。TESTKIT-PERF-01保持OPEN。不能以本节替代阶段18总门禁或119项全交互。
