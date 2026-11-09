# 2026-11-04 阶段 18/19 UAT 逐项执行进度

2026-11-09 流程更新：后续使用 `stage18-19-five-hour-acceptance.md` 的 119 项覆盖映射和 300 分钟单轮执行窗口，不再默认逐项重复手工操作。以下历史计数和失败证据不重写；当前仍为 15 PASS、1 FAIL、103 NOT_RUN，18/19 `REVIEW`。映射不等于 PASS，P0/P1 没有等价断言仍需执行。

11-09 CALC-001 精确证据映射 PASS：`NetInventoryAllocationTest.layerNettingMatchesConfirmed1000UnitGoldenExample` 的内存纯引擎输入与原 UAT A/B/C/D 数量、BOM 比例和逐层库存完全一致，净量 500/700/2000/1000，末级 1000；最终全量 Surefire XML 确认用例存在且通过。CALC-002 仍 FAIL，现行新批次须使用当前同步上海 BOM 的独立 oracle，不能把历史 #7 的 2,820 键直接改称新批次验收。累计 15 PASS、1 FAIL、103 NOT_RUN。

11-09 ADMIN-002 完整自测 PASS：隔离 8183 角色创建、权限授予/回收、同一 JWT 实时鉴权、引用中删除业务拒绝及解除引用后删除，真实 API 14/14；当前角色配置页 Chrome 1/1，临时账号/角色/审计 0 残留，#2 不变。原镜像被引用删除 500 失败及旧静态页面失败 trace 均保留。后端最终编译、独立 MySQL/Redis 全量 100 套件/725 项退出 0，见阶段 18/19 报告。该次后累计 14 PASS、1 FAIL、104 NOT_RUN；ADMIN-003 全接口矩阵仍未完成。

11-09 新代码小样本限定增量：独立 8184 的 #151/task #129/run #131 从 #7 冻结输入复算，380 逐层/360 终点/2,820 日事实完整业务 SHA 与来源相同、守恒 0 差异，临时数据与账号 0 残留。5181 当前前端代理新 API 的 Chrome 定向页面 1/1，缺料表格实际载入物料行；失败旧静态路由 trace 和浅断言截图保留。该复核不替代 CALC-002 原断言、PERF-003 大负载或完整浏览器 UAT，累计仍 13 PASS、1 FAIL、105 NOT_RUN。后端本轮 Surefire 100/724 XML 0 失败错误跳过，但外层包装丢失 Maven 退出码，ENV-007 当前代码总门禁仍未重记 PASS。

11-09 角色配置正向限定增量：隔离 8183 的 25/25 断言验证角色权限及工厂范围同事务配置、非法配置回滚、原 JWT 即时授权与撤权，临时账号/角色/审计 0 残留。`role-configuration-positive-20261109/result.json`；尚无完整 view/import/manage/adjust/publish/export 有权操作及全接口跨厂内容，ADMIN-003 仍 NOT_RUN，累计 13 PASS、1 FAIL、105 NOT_RUN。

11-09 数据一致性限定增量：独立 #150/run #130 用当前上海 BOM 与双厂 8,466 个经营计划键实际完成整机及物料计算，逐层 106,782、终点 55,512、日事实 1,202,760；经营计划到根需求、终点到日事实逐键双向数量差异 0。快照仅覆盖两厂各 1,556 物料编码，计算出现 6,326 个缺库存及 57 个缺主数据持久化阻断，实际计算资源探针采错空闲 Worker；这不关闭 PERF-003、CALC-002 或完整 UAT。证据 `current-frontier-material-20261109/result.json`、`current-frontier-sql-verify-20261109/result.json`。累计仍 13 PASS、1 FAIL、105 NOT_RUN。

11-06 权限限定增量：隔离 8183 的扩展七角色与临时单厂探针 375/375 PASS，单厂工厂/主数据/仓库映射各 1 行、日历 212 行，夹具与审计 0 残留、#2 不变。ADMIN-003 的完整有权操作及全接口跨厂内容断言仍缺，保持 NOT_RUN；原 UAT 计数 13 PASS、1 FAIL、105 NOT_RUN 不变。证据 `test-kit/2026-10-15/evidence/runs/role-matrix-single-factory-20261106/result.json`。

随后单厂分页/仓库策略与跨厂日历扩展为 382/382 PASS：仓库映射分页 1/1、库存策略 1,564 条均归本厂、永惠月份 403；旧版日历版本列表 0 行，不当作正向读取证据。`role-matrix-scope-pages-20261106/result.json`；ADMIN-003 和原 UAT 计数不变。

本文件记录对 `test-kit/2026-10-15/用例清单.csv` 的本轮实际执行；原始 119 项 CSV 保持待执行模板，未把既有 42/42 专项批量迁入。当前 15 PASS、1 FAIL、103 NOT_RUN；限定子断言不能代替整项 PASS。CALC-002 原断言的混合组织快照已被 C021/C023 取代，失败和上海口径限定通过分别记录，不改写原始 CSV。

11-06 用户权限限定增量：旧镜像仅 `user:update` 可给自己直授权限，旧镜像仅 `user:create` 可夹带 ADMIN 角色创建新账号，两个 P0 越权均有真实 API/SQL 失败证据。新镜像直授、重置、启停和创建带角色分别按独立权限拒绝/允许，隔离探针 PASS、浏览器定向 2/2；V065 角色配置页可见新权限。ADMIN-003 仍要求全接口逐权限和跨厂内容矩阵，故总项 NOT_RUN，13 PASS、1 FAIL、105 NOT_RUN 计数不变；首轮浏览器定位/异步等待失败 trace 保留。

11-06 库存确认权限限定增量：旧镜像两条确认路径错用上传权限，新镜像仅上传 403/403、仅确认进入业务层 400/404；两页隔离浏览器均隐藏上传账号的确认按钮。后端最终 721/721、前端构建/111 单测 PASS，#2 不变。ADMIN-003 还要求完整逐权限接口与工厂内容矩阵，仍 NOT_RUN；UAT 计数不变。

11-06 限定增量：七角色 138/138 真实 API 断言通过，包含三名无工厂范围角色读取库存快照时 200/空数组；ADMIN-003 仍缺全接口逐权限及其余列表返回内容工厂域断言，故保持 NOT_RUN。PERF-003 原夹具 1,412 根中有 1 个已停用，原快照 #33 混合上海/无锡；只读核对有效根 1,411、当前上海 BOM 145,801 条，旧 456,600 键不能照搬，仍 NOT_RUN。本段未改变 13 PASS、1 FAIL、105 NOT_RUN 的计数。

11-06 ADMIN-003 限定增量：33 组路由权限回归通过；隔离临时角色仅授 `mrp_shortage:view` 和 ALL 工厂时，默认整机导出旧镜像 200，修复后 403，物料导出仍 200。角色配置页浏览器 1/1，最终后端 719/719。该用例还要求逐权限 view/import/manage/adjust/publish/export 的完整真实 API/工厂矩阵，故 ADMIN-003 仍 NOT_RUN，计数保持 13 PASS、1 FAIL、105 NOT_RUN。`PERF-003` 原夹具含停用根与历史混合组织快照，仍 NOT_RUN；需现行规则的大负载基线。

11-05 限定证据追加：角色矩阵 7 角色/37 项 API 断言 PASS，但 ADMIN-003 的 view/import/manage/adjust/publish/export 全权限矩阵仍 NOT_RUN；当前镜像 #144 基于 #13 真实重算 217,275 日事实，三类事实完整业务字段哈希与来源相同、守恒差异 0。该大样本本身不覆盖 CALC-002/003 原数据集；CALC-003 已由下表 11-06 的独立诊断用例证据关闭。PERF-002 原始 224,250 键和 PERF-003 的 456,600 键未执行，PERF-004/005 仅有混合 SQL 计数和粗 GC 窗口，不满足原用例。上述大样本限定证据不额外增加 UAT 计数。

| 用例 | 状态 | 输入/操作与实际结果 | 证据/限制 |
| --- | --- | --- | --- |
| ENV-005 技术栈 | PASS | `java -version` 为 21.0.12.1，`node -v` 为 24.21.0；本轮未修改技术栈配置。 | 11-05 命令输出；阶段 18 报告。 |
| ENV-006 编译 | PASS | 最终代码 `./mvnw compile -q` 与 `npm run build` 均退出 0。 | 11-05 命令输出；阶段 18 报告。 |
| ENV-007 单测集成 | PASS | 独立 `mrp_gate_final3_20261106` schema/Redis 6391 上后端全量 `./mvnw test -q` 100 套件/724 项、0 失败错误跳过，退出 0；前端 13 文件/111 项通过。 | `/tmp/mrp-user-security-fulltest-complete-20261106.log`、Surefire XML；无退出记录的 723 项 XML 与中止运行不计 PASS。 |
| ENV-008 基础数据校验 | PASS | `python3 test-kit/2026-10-15/tools/verify_fixtures.py` 4/4 通过，含 29 工作簿表头/行数、引用/策略、日周月守恒及库存公式。 | 11-05 命令输出；离线夹具校验，不代表真实 API 导入。 |
| AUTH-001 登录退出 | PASS | 隔离 Chrome 正确密码登录、退出；重新访问受限 `/versions` 回登录页且本地 token 已清除。 | `test-kit/2026-10-15/evidence/runs/auth-boundary-20261105/browser-retest.json`。 |
| AUTH-002 错误凭证 | PASS | 空表单被页面拦截；错误密码与不存在账号在隔离 API 均 `success=false/MRP_AUTH_ERROR`，响应无 SQL、哈希、路径等内部信息；浏览器显示“邮箱或密码错误”，页面错误 0。HTTP 为 200 包业务失败，按本用例“拒绝”预期记录，不替代 HTTP 状态规范审计。 | 同目录 `api.json`、`browser-retest.json`、`wrong-password-retest.png`；首轮浏览器脚本未等待异步提示的误报 `browser.json` 保留。 |
| AUTH-003 篡改 token | PASS | 已登录 token 修改签名后调用受限 `/plans` 为 401；浏览器刷新受限 `/versions` 后清 token 并回登录页，再次正确密码登录成功。原用例允许“过期或篡改”，本轮覆盖篡改分支；过期时钟边界未单独测。 | 同目录 `api.json`、`browser-retest.json`、`tamper-relogin.json`。 |
| AUTH-004 无业务权限账号 | PASS | 隔离 API 临时创建零角色账号，登录后有效权限集为空；`/plans`、`/mrp/batches/2`、`/users` 均 403。Chrome 登录与直接访问 `/planning` 均到等待权限页；账号和测试审计精确清理 0 残留。 | `test-kit/2026-10-15/evidence/runs/auth004-no-permission-20261105/result.json`、`waiting-permission.png`。原等待页文案错误保留截图；11-05 新镜像 Chrome 复核标题为“等待权限分配”。 |
| AUTH-006 工厂数据域 | PASS | 独立 Docker 库临时 READONLY 账号仅授爱培科；以永惠参数访问旧当前计划返回空，跨厂旧网格/对比/任务、双厂发布批次 #2/summary/整机导出均 403；管理员读取 #2 仍 200/PUBLISHED；临时账号、旧版本、任务和导出夹具 0 残留。 | `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104115334130/result.json`；只覆盖该用例的指定工厂域，CAP-1014-03 全接口矩阵仍 OPEN。 |
| AUTH-005 只读权限 | PASS | 双厂 READONLY 用户可读发布批次 #2；经营计划确认、库存确认、日转移、发布、删除用户五个写 API 均 403。修复前真实 Chrome 在经营计划和库存页各看到 1 个导入按钮；修复后 READONLY 在经营计划、库存、物料、分类、BOM、排产六页的导入/新增/编辑/删除/启停/同步/生成/发布写按钮计数均 0，管理员在经营计划、库存、物料、分类、BOM 对应写入口各可见 1，Console error 0。 | 原 API `.../legacy-phase1-gate-20261104122703592/result.json`；最终 API `.../legacy-phase1-gate-20261104133304962/result.json`，夹具 0 残留；隔离 Chrome 真实运行、前端 110/110、后端 676/676、Playwright 1/1。仅为该用例指定只读角色与可访问页自测，完整权限矩阵仍缺。 |
| AUTH-007 下载越权 | PASS | 隔离 API 生成真实 3350B XLSX，所有者下载 200；另一同权限账号按任务 ID 下载 403，旧任务元数据/下载的非所有者均 403，跨厂导出 403。 | `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104130228745/result.json`；账号、任务、文件和审计夹具 0 残留。 |
| AUTH-009 角色变更生效 | PASS | 临时单厂账号旧 JWT 先读 `/plans` 200；管理员经权限 API 撤销 `schedule:view` 后同 token 立即 403；管理员停用后同 token 401。用户、权限、导出、旧版本/任务、审计夹具 0 残留。 | 原停用失败 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104120125749/result.json`；最终撤权/停用 `.../legacy-phase1-gate-20261104122135986/result.json`。 |
| ADMIN-002 角色管理 | PASS | 隔离 API 新建角色，配置 `mrp_factory:view`/ALL；目标账号旧 JWT 从 403 到 200，撤权后同 JWT 回 403。仍被用户引用时删除返回明确 400 且数据不变；解除关联后角色及范围删除。当前角色配置页 Chrome 1/1，临时用户/角色/审计 0 残留，#2 未变。 | 旧镜像 500 失败 `admin002-role-before-20261109/result.json`；最终 API 14/14 `admin002-role-final-20261109/result.json`、浏览器 `e2e-admin002-role-current-ui-20261109/`；后端 725/725。 |
| CALC-001 黄金链式扣库存 | PASS | 纯引擎精确输入 A1000/A 库500，A→B×2/B 库300，B→C×3/C 库100，C→D×1/D 库1000；逐层净量 A500/B700/C2000/D1000、终点 D1000。 | `backend/src/test/java/com/mrp/mrp/engine/NetInventoryAllocationTest.java` 的黄金测试与最终 `TEST-com.mrp.mrp.engine.NetInventoryAllocationTest.xml`，全量退出 0。 |
| CALC-003 诊断准确性 | PASS | 原始隔离真实 Excel 配置/经营计划/库存导入与 API 计算批次 #2，经完整分页 HTTP 采集 4,194 日键，对独立 Decimal oracle 数量/维度差异均为 0；发布 #3 后本轮只读核对 #2/#3 三类事实完整业务字段 SHA 相同。11-06 当前镜像在独立 8184 基于 #3 冻结输入与来源 #2 的 176 条库存池复算 #146/task123/run126，515 逐层、515 终点、4,194 日事实完整字段 SHA 与 #3 相同，守恒差异 0，临时数据清理 0。初次 #145 因探针漏复制发布源库存池得到不同数量，原 FAIL 保留，确认根因后精确清理。 | `test-kit/2026-10-15/evidence/runs/accuracy-distinct-2026-10-15T11-57-48-418Z/oracle-comparison.json`、`test-kit/2026-10-15/evidence/runs/current-accuracy-distinct-with-pools-20261106/result.json`；原测试已由独立 Reviewer 限定核查，当前复算尚待独立阶段复核。 |
| CALC-002 原始双厂库存准确性 | FAIL | 原清单预期快照 #33、8,436 日键；隔离 #147/task124 使用该快照时因上海/无锡同物料路径撞逐层事实唯一键，`CALC_ERROR`，临时数据 0 残留。C021/C023 已禁止混合组织历史快照进入新计算，故原断言需重定基线，不能以此修改当前引擎。上海快照 #34 下，同一原始计划/库存导入的 #7 独立 Decimal oracle 为 2,820 日键 0 差异；当前 #149/task126/run129 重算三类系统事实 SHA 与 #7 全部相同、守恒 0 差异、临时 0 残留。首次误用带 4 条人工修订的 #8 作原始基准产生 #148 FAIL，已 SQL 归因并清理。 | `CALC-002-FIXTURE-1106`；`current-accuracy-stock-20261106/result.json`、`current-accuracy-stock-shanghai-20261106/result.json` 保留；限定 PASS 为 `accuracy-stock-2026-10-16T06-45-00-793Z/repair-oracle.json` 与 `current-accuracy-stock-original-system-20261106/result.json`。原 8,436 键用例仍 FAIL。 |
| LIFE-006 旧版本兼容 | NOT_RUN | 临时 v1 旧版本的列表/当前/网格与旧写入口拒绝已验证；真实既有 v1/v2/v3 数据回放、页面与历史计算口径未全验。 | 同上及旧镜像失败 `.../legacy-phase1-gate-20261104105403840/result.json`。 |
| LIFE-009 下载安全 | NOT_RUN | 他人任务 403；真实文件 200；删除文件后 404、数据库指向导出目录外文件 404、集成测试中符号链接 404，元数据与错误体均不泄漏服务器路径。 | 旧镜像目录外文件 200、元数据泄露的失败证据 `.../legacy-phase1-gate-20261104122903394/result.json`；修复后 `.../legacy-phase1-gate-20261104130228745/result.json`，后端 676/676。项目没有文件时间到期规则，尚不能声称时间到期策略 PASS。 |
| LIFE-010 表格注入 | NOT_RUN | 11-05 隔离 SQL 构造的四种公式前缀文本经真实导出 API 生成 XLSX；下载文件 11 个字符串单元格、0 公式节点，夹具及导出文件 0 残留。尚未从原用例要求的可编辑文本入口输入并形成版本，因此仅限定导出子项 PASS。 | `test-kit/2026-10-15/evidence/runs/life010-formula-3115b413e75842fa/result.json`；首次探针去重计数误报 FAIL 见 `.../life010-formula-b9c5d38764cd4782/result.json`。 |

11-05 增量：ADMIN-001 的新建/重置随机临时密码、首次改密和旧会话失效仅限定子项 PASS；增改停用恢复的完整组合仍 NOT_RUN。ADMIN-005 的预填与固定口令子项已修；隔离 V001 播种哈希已一次性引导并改密，旧固定口令拒绝，正式凭据轮换仍缺，完整日志/审计/归档秘密扫描 NOT_RUN。AUTH-008 的飞书本地绑定拒绝停用账号及新用户无本地口令由独立 MySQL 测试覆盖，外部授权回调仍 NOT_RUN。等待权限页文案修正后隔离 Chrome PASS。原始 119 项状态仍为 8 PASS、111 NOT_RUN。

11-05 认证错误增量：ADMIN-005 的飞书上游失败日志、回调错误体和改密错误体脱敏限定子项自测 PASS，证据见阶段 18 报告与 `FeishuErrorDisclosureTest`；真实无效飞书回调固定错误文案，隔离 Chrome 1/1。完整 ADMIN-005 仍 NOT_RUN，119 项状态仍为 8 PASS、111 NOT_RUN。

2026-11-05 当时的下一批优先级：P2-I002 剩余管理员初始凭据方案及正式轮换、LIFE-009 时间到期口径、LIFE-010、ADMIN-004/005、PERF-001~014 中的 P0/P1；性能需另建有独立端口和卷的负载环境，不使用指向 8180 的旧 runner。当时计划按原始 CSV 逐项执行完整 UAT；该执行方式已由本文件顶部 2026-11-09 的覆盖映射口径取代，原始记录保留。

11-05 最新增量：隔离 V001 播种管理员已引导并首次改密，旧固定口令、引导口令及旧 JWT 拒绝；`docker_acceptance_chain.mjs` 的两处旧固定登录口令已改为显式验收环境变量，未重跑有写入的新批次成功链。最终后端 684/684、前端 110/110、隔离 Playwright 1/1 和发布版 #2 API 200/PUBLISHED；ENV-005~008 四项加入本轮独立执行进度，累计 12 PASS/107 NOT_RUN。ADMIN-005 因正式轮换、完整扫描与独立复核缺失仍 NOT_RUN，阶段 18/19 均 REVIEW。
