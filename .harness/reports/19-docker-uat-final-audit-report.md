# 19 Docker/UAT 最终审计报告

## 2026-11-09 验收执行口径更新

本次仅改验收文档，未执行新 UAT。后续按 `stage18-19-five-hour-acceptance.md` 的 300 分钟单轮流程覆盖所有功能模块，并逐项映射原 119 项证据/缺口；不能因此把 15 PASS、1 FAIL、103 NOT_RUN 改成全部通过。P0/P1 未有等价证据、正式凭据轮换及独立复核未完成前，阶段 19 保持 `REVIEW`。

## 2026-11-09 CALC-001 黄金链与 CALC-002 口径复核

原 CALC-001 的 A/B/C/D 1000 台逐层扣库输入与当前 `NetInventoryAllocationTest` 黄金用例完全一致，最终 725 项全量及该用例 Surefire XML 通过，逐层净量 500/700/2000/1000、末级 1000，登记纯引擎用例 PASS。CALC-002 原 8,436 键使用混合上海/无锡快照 #33，违反 C021；历史 #7 使用上海快照 #34、v4 规则且 `bom_source_checksum` 为空，2,820 键独立 oracle 与当前复算一致只能作为历史输入限定证据，不能替代 C023 当前同步 BOM 的新批次逐键验收。原 FAIL 保留，修订用例须冻结当前上海 BOM checksum、合法库存/计划输入，完整 HTTP 日事实对独立 Decimal oracle 逐键比对，并核对三类事实/日周月守恒。

## 2026-11-09 ADMIN-002 角色生命周期 UAT

隔离 8183 真实 API/SQL 的 ADMIN-002 14/14 PASS：新增角色、授予/回收权限、旧 JWT 即时鉴权、被用户引用时明确 400 拒绝、解除引用后删除。旧镜像 500 失败原件与旧静态资源导致的浏览器失败 trace 均保留；最终当前前端角色配置 Chrome 1/1 PASS。临时用户/角色/审计 0 残留，Flyway V065，#2/run #2 PUBLISHED/checksum 未变；仅隔离 API/Worker 更新，正式资源只读。后端最终全量 725/725 退出 0；该项完成后原始 119 项证据复核为 14 PASS、1 FAIL、104 NOT_RUN，后续 CALC-001 精确证据映射使累计变为 15 PASS、1 FAIL、103 NOT_RUN。最后只读探测 8183 health 200，正式 5180 当前未监听，未启动或写入。ADMIN-003 与独立阶段复核未完成，19 保持 `REVIEW`。

## 2026-11-09 角色配置正向限定续验

新代码在独立 `mrp-testkit` 的临时 8184 API 复算 #7 到 #151/task #129/run #131，三类事实 380/360/2,820 行完整业务 SHA 与来源一致、逐层及 API 守恒差异 0，夹具 0 残留；当前前端源码经 5181 代理 8184 的 Chrome 定向 1/1 实际加载排产与缺料物料行，页面错误/API 5xx 为 0。后端 jar 直带旧静态 bundle 的首轮路由失败 trace 和中间浅断言截图均保留，最终有效证据见阶段 18 报告。后端 Surefire 100 套件/724 项 XML 全绿，但包装命令丢失 Maven 退出码，完整回归总门禁不计 PASS；前端构建及后端跳测打包明确退出 0。8184/5181 已停止，正式 5180 未启动。完整 UAT 计数不变，阶段 `REVIEW`。

隔离 8183 的角色配置正向探针 25/25 PASS：配置操作者授予/撤销目标角色工厂查看权限，旧 JWT 即时反映权限及指定工厂/全部/无范围，非法工厂提交不落库；临时用户/角色/审计 0 残留、发布版 #2 不变。证据 `test-kit/2026-10-15/evidence/runs/role-configuration-positive-20261109/result.json`。ADMIN-003 全接口矩阵仍 NOT_RUN，119 项计数仍 13 PASS、1 FAIL、105 NOT_RUN；当前代码全量测试原始退出码缺失，阶段保持 `REVIEW`。

## 2026-11-09 独立大负载限定核验

8183 的隔离发布版 #2 未参与写入，正式主库/5180/Redis/卷只读。另在 `mrp-testkit` 上的临时 #150/run #130 完成当前 BOM 双厂真实计算，8,466 根需求到 1,202,760 日事实的逐键守恒 0 差异；快照覆盖不足导致 6,383 个持久化阻断问题，故批次停在 `MATERIAL_REVIEW`，未确认物料或发布。测试资源采样未采到实际计算的 API 容器。此为数据一致性限定证据，不替代原 `PERF-003`、完整 UAT 或独立复核；原 119 项仍 13 PASS、1 FAIL、105 NOT_RUN，阶段 19 仍 `REVIEW`。详见阶段 18 报告与 `current-frontier-sql-verify-20261109/result.json`。

## 2026-11-06 七角色与单厂范围限定续验

同日探针追加单厂仓库映射分页及库存策略逐行范围，382/382 PASS：分页 1/1，库存策略 1,564 条均属爱培科仓库，永惠日历月份 403；旧版日历版本列表为空，不视为正向验证。证据 `role-matrix-scope-pages-20261106/result.json`，账号/角色/审计 0 残留、#2 未变。ADMIN-003 与 UAT 计数不变。

隔离项目 `mrp-acceptance-20261104` 的现有 8183 API 通过 375/375 条真实断言；七个非管理员角色扩展无权写路由，临时仅爱培科角色的工厂、主数据、仓库映射各 1 行、生产日历 212 行，临时账号/角色/审计 0 残留，发布 #2 不变。证据 `test-kit/2026-10-15/evidence/runs/role-matrix-single-factory-20261106/result.json`。本轮只改探针和报告，未重跑编译/全量/浏览器；ADMIN-003、完整 UAT 和独立复核仍未通过，19 保持 `REVIEW`，原 UAT 计数仍为 13 PASS、1 FAIL、105 NOT_RUN。

## 2026-11-06 用户权限安全隔离镜像复验

旧隔离镜像真实 API/SQL 证实两项 P0 越权：仅 `user:update` 可给自己直授 `ALLOW`，仅 `user:create` 可夹带 ADMIN 角色建号。最终后端独立测试库/Redis 全量 100 套件/724 项、0 失败错误跳过；前端已构建/111 单测。本次只重建 `mrp-acceptance-20261104` 的 API/Worker，MySQL/Redis 容器和卷未替换。新镜像 `sha256:88b7ea4c...`、8183 health UP、Flyway V065/65；权限探针分别验证直授、重置、启停和创建带角色的拒绝与显式授权成功，临时用户/角色 0 残留。隔离 Chrome 角色配置及用户按钮定向复测 2/2 PASS，首轮测试夹具失败 trace 保留。#2/run #2 仍 PUBLISHED、80 日事实、原 checksum `794268e39603f6b0c27f308d34839216eb9e1ad693a32485bd786d46b7ef85b5`；正式 5180 仅读 200。证据见阶段 18 报告和 `user-security-after-20261106/result.json`。这只关闭限定权限子项；119 项 UAT 仍 13 PASS、1 FAIL、105 NOT_RUN，完整 ADMIN-003、性能/兼容/安全总门禁、正式凭据轮换和独立 Reviewer 未通过，阶段 19 保持 `REVIEW`。

## 2026-11-06 库存确认权限隔离复验

仅重建 `mrp-acceptance-20261104` 的 API/Worker；MySQL/Redis 与卷未重建。旧镜像确认导入越权的 FAIL 原件保留，新镜像单权限真实 API 对两条确认路径分别得到仅上传 403/403、仅确认 400/404；隔离 Chrome 在经营计划和库存页的上传账号均可打开弹窗、均无确认按钮，定向 Playwright 1/1。临时用户/角色 0 残留，8183 health 200、Flyway V064/64、#2/run #2 PUBLISHED、80 日事实及原 checksum 不变，正式 5180 仅读取 200。最终后端 100 套件/721 项全 PASS、前端构建/111 单测 PASS；缺引导口令的第一次全量失败日志保留且不计 PASS。证据见阶段 18 报告和 `test-kit/2026-10-15/evidence/runs/inventory-confirm-permission-after-20261106/result.json`。此为权限限定子门禁，119 项 UAT 仍 13 PASS、1 FAIL、105 NOT_RUN；阶段 19 REVIEW。

## 2026-11-06 七角色权限矩阵限定续验

隔离 Docker 项目 `mrp-acceptance-20261104` 保持运行，8183 health 200；未重建容器、未清卷、未触碰正式 5180/主库/Redis。七个非管理员角色一次性账号扩展矩阵 138/138 真实 API 断言通过，其中无工厂范围三角色的库存快照列表 200/空数组；用户与审计 0 残留，#2/run #2 PUBLISHED 与原 checksum 不变；8 条逐层守恒等式 0 差异、80 条日事实合计 80。详见阶段 18 报告及 `test-kit/2026-10-15/evidence/runs/role-matrix-expanded-content-20261106/result.json`。本轮只改验收探针和报告，没有应用代码改动；按用户新节奏未重复编译、全量测试或浏览器套件。ADMIN-003 及 119 项完整 UAT 状态不变，18/19 均 REVIEW。

## 2026-11-06 导出权限修复后隔离复验

`mrp-acceptance-20261104` 仅重建 API/Worker 镜像，MySQL/Redis 与卷不清理，正式主库/Redis/5180 只读。仅授 `mrp_shortage:view`、工厂 ALL 的临时角色，在旧镜像对默认整机导出为 200/8027B，修复后为 403；物料导出保持 200/9908B。临时用户/角色 0 残留，#2/run #2 仍 PUBLISHED、80 日事实、原 checksum，Flyway V064/64，8183 health 200。角色配置页与隔离发布页面 Playwright 各 1/1 PASS；后端最终 719/719。失败与修复证据见阶段 18 报告和 `test-kit/2026-10-15/evidence/runs/export-view-permission-20261106/result.json`。ADMIN-003 总项、完整 UAT、性能和独立 Reviewer 仍缺；本阶段保持 REVIEW。

## 2026-11-06 CALC-002 原口径冲突与上海口径限定通过

原 119 项清单的 CALC-002 要求混合组织快照 #33 的 8,436 日键；该输入已被 C021/C023 禁止用于新计算。隔离 8184 对 #33 复现 #147/task124 `CALC_ERROR`，两组织同物料路径导致逐层明细唯一键冲突，失败证据保留且临时数据已清理；原用例记 `FAIL`，不可按新口径冒充原断言通过。上海快照 #34 的同一原始经营计划/库存导入已有 #7 的 2,820 键独立 Decimal oracle 0 差异；本轮当前 Worker #149/task126/run129 与 #7 三类系统事实完整字段 SHA 相同、逐层及 API 守恒 0 差异、精确清理 0。首次误选带 4 条人工修订的 #8 作基准而造成的 #148 FAIL 也保留，SQL 已证实 #148 与 #7 系统事实逐行相等。8184 浏览器 `/planning/7`、`/shortage/7` 和真实 API 正常、Console error 0。证据及根因详见阶段 18 报告；UAT 实证进度为 13 PASS、1 FAIL、105 NOT_RUN，原始 CSV 模板未改。

最终后端 `./mvnw compile -q` 与独立 MySQL/Redis 全量 100 套件/685 项、0 失败错误跳过；前端最近 build/111 单测 PASS，本次未改前端。8183 隔离发布版 #2/run #2、80 日事实和 checksum 未变，V064/64，正式 5180 只读 200。原 CALC-002 需按已确认上海 BOM 规则重定可执行预期；完整角色/工厂接口矩阵、456,600 键、低堆/故障、安全扫描、正式凭据轮换、完整 UAT 和独立阶段 Reviewer 仍缺，阶段 19 继续 `REVIEW`。

## 2026-11-06 CALC-003 诊断准确性复核

复用早期隔离真实导入 #2 的 4,194 日键完整 HTTP 对独立 Decimal oracle 0 差异证据；发布 #3 的逐层、终点、日事实完整业务字段 SHA 与 #2 相同。当前镜像 8184/Worker 以相同冻结输入及原 #2 的 176 条库存池重算 #146/task123/run126，12.378 秒，515 逐层、515 终点、4,194 日事实 SHA 与 #3 相同，守恒差异 0；临时数据精确清理 0。首次探针漏复制池造成 #145 错量的 FAIL 证据保留并追根后修正。8184 浏览器排产/缺料 #3 正常、页面错误 0。CALC-003 记 PASS，UAT 本轮证据进度 13/119；CALC-002 及 456,600 日键仍未完成，阶段 19 REVIEW。

## 2026-11-05 角色与大计算续验

隔离 8183 当前镜像 7 个非管理员角色、37 项真实 API 权限/工厂范围断言 PASS，临时用户及审计 0 残留，发布版 #2 不变。独立 8184 当前 API/Worker 镜像重算 #13 来源，#144/task121/run124 在 45.286 秒完成，9,174 逐层、17,382 终点、217,275 日事实完整业务字段 SHA 与来源一致，守恒差异 0；精确清理后仅来源 #13 保留 PUBLISHED。8184 Chrome `/planning/13` 已发布、`/shortage/13` 最终 1,459 个汇总物料/首屏 20 条、页面错误 0。证据见阶段 18 报告和两个探针的 `result.json`。这不替代原始 UAT 的独立 oracle/456,600 日键及 119 项逐条执行，计数仍为 12/119 PASS；本阶段保持 REVIEW。

历史 v4 缺料页原在 #13 加载双岗确认指纹约 11.6 秒，Chrome 无竞争首屏约 14.6 秒；当前前端对已发布非 v5 省去无用确认请求后，独立 5181/8184 Chrome 首屏约 1.029 秒且仍为 1,459 汇总物料。隔离 5182/8183 的 v5 #2 仍请求确认 API 200、6 条缺料正确。新增边界测试后前端构建、111/111，后端编译和独立库全量 685/685，`git diff --check` 通过。阶段状态与 UAT 12/119 不变。

## 2026-11-05 角色撤权时序镜像复验

仅重建隔离项目 `mrp-acceptance-20261104` 的 API/Worker（同一镜像 `sha256:ee22e9c1...`），未重建 MySQL/Redis 或清卷。8183 health 200、真实管理员 `/auth/me` 与 #2 summary 200、#2/run #2 PUBLISHED、checksum 不变、Flyway V064/64，角色页面隔离 Chrome 1/1 PASS、临时用户/角色残留 0；正式 5180 只读 200。前述失败 trace 保留，新增浏览器输出目录为 `test-kit/2026-10-15/evidence/e2e-role-cache-20261105/`。后端编译及全量 100 套件/685 项 PASS，Docker 构建 PASS。原独立 UAT 仍为 12/119 PASS、107 NOT_RUN；本次不把限定复验转为完整 ADMIN 用例 PASS。19 保持 REVIEW。

## 2026-11-05 聚焦角色配置与发布版守恒续验

用户确认小团队重点为数据一致性、计算核验和清晰的逐角色权限配置；原始 119 项 UAT 状态未批量改变。隔离 API/Worker 新镜像重建，MySQL/Redis 容器及卷不清理；8183 health 200、Flyway V064/64、发布版 #2/run #2 PUBLISHED，结果 checksum 保持 `794268e39603f6b0c27f308d34839216eb9e1ad693a32485bd786d46b7ef85b5`。只读 SQL 验 8 条逐层需求、8 条末级需求、80 条日事实，守恒和逐键差异均 0，两厂整机各 10、原材料各 30。隔离角色配置 Chrome 1/1 PASS，保存/刷新/API 落库一致，临时用户和角色 0 残留；首次浏览器工具点击失败 trace 保留。后端全量 685/685、前端 110/110 与构建 PASS。此为限定增量，不把 ADMIN-002/003、CALC-002~004 或 PERF 总项直接改 PASS；完整 UAT、独立 Reviewer 和阶段 18 安全/性能总门禁仍缺，阶段 19 保持 REVIEW。

## 2026-11-05 认证错误修复后隔离运行复核

仅替换 `mrp-acceptance-20261104` API/Worker 镜像，没有清理 MySQL/Redis 卷。8183 health 200/UP、Flyway V064/64、发布版 #2/run #2 PUBLISHED；无效飞书回调真实 HTTP 返回固定 `FEISHU_AUTH_ERROR` 文案，容器仅记录固定告警。最终 Chrome Playwright 使用独立输出目录执行已发布整机/缺料及 1440/1920 负荷格回归 1/1 PASS。后端最终全量 98 套件/681 项通过，前一版 680 项及镜像证据保留。此为错误脱敏限定复核，不是外部飞书授权成功回调或完整 UAT；119 项仍为 8 PASS/111 NOT_RUN，阶段 19 继续 `REVIEW`。

## 2026-11-05 隔离环境恢复与 LIFE-010 限定复核

AUTH-001/002/003 追加：隔离 API/Chrome 的登录退出、三种错误凭证、篡改 token 401/页面回登录及再次登录均按原用例通过，证据 `test-kit/2026-10-15/evidence/runs/auth-boundary-20261105/`；首轮浏览器脚本误报保留。AUTH-004 零权限临时账号的三项受限 API 403、浏览器等待权限页与夹具 0 残留，证据 `test-kit/2026-10-15/evidence/runs/auth004-no-permission-20261105/`；误报密码修改成功文案另记 P2。逐项 UAT 本轮 8/119 PASS、111 项仍未完成；原 CSV 模板未改，阶段状态不变。

隔离项目四个既有容器此前停止，复核为 API/Worker/MySQL exit 137 且 `OOMKilled=false`、Redis exit 0；仅用 `docker start` 恢复，没有重建镜像或清卷。MySQL/Redis 健康，8183 health 200/UP，Flyway V064/64、发布版 #2 PUBLISHED，正式 5180 只读 200。隔离 API 的公式前缀 XLSX 导出限定探针 202/200、11 个字符串单元格、0 公式节点、四种前缀保真、夹具/文件 0 残留；证据见 `test-kit/2026-10-15/evidence/runs/life010-formula-3115b413e75842fa/result.json`。首次探针自身误判原件保留。后端独立 schema/Redis 6391 全量 97 套件/676 项通过，前端 build/110 单测与隔离 Chrome Playwright 1/1 通过，8183 `/planning` 和正式 5180 `/planning` 均只读 200。原 LIFE-010 要求从可编辑文本入口输入，当前只用隔离 SQL 夹具，故 UAT 仍 4/119 PASS、LIFE-010 NOT_RUN；完整安全/性能/兼容和独立阶段 Reviewer 尚未通过，阶段 18/19 均保持 REVIEW。

## 2026-11-04 AUTH-005/007 与下载路径续验

状态仍为 `REVIEW`。仅重建隔离 `mrp-acceptance-20261104` API/Worker；MySQL/Redis 与卷、发布版 #2/run #2、正式主库/Redis/5180 均保留。AUTH-005 双厂 READONLY 账号读 #2 为 200，五类写 API 均 403，`.../legacy-phase1-gate-20261104122703592/result.json`；页面按钮未验，整项 NOT_RUN。AUTH-007 实际 XLSX 所有者下载 200/3350B，同权限另一账号按任务 ID 下载 403，整项本轮自测 PASS。旧镜像元数据暴露内部路径且可下载导出目录外文件 200，失败 `.../legacy-phase1-gate-20261104122903394/result.json`；修复后目录外/缺失文件 404，元数据和错误体不泄漏路径，`.../legacy-phase1-gate-20261104130228745/result.json`。脚本夹具、文件、审计 0 残留，#2 仍 PUBLISHED。

`./mvnw test-compile -q` 与独立 schema/Redis 6391 的全量后端 97 套件/676 项 0 失败/错误/跳过；Docker 前后端构建退出 0；隔离 Chrome Playwright 1/1、8183 health/planning 与正式 5180/planning 只读 HTTP 200。原始 119 项 CSV 仍为待执行模板，本轮逐项 UAT 3/119 PASS。LIFE-009 时间到期口径、完整安全/性能/兼容、其余 116 项 UAT 和阶段独立 Reviewer 未通过；18/19 均保持 REVIEW。

AUTH-005 页面增量：READONLY 旧静态前端经营计划/库存导入按钮各可见 1；按后端权限码修正经营计划、库存、物料、分类、BOM 五个可访问页面的写按钮。重建隔离 API/Worker 后真实 Chrome 在 READONLY 的六页（含排产）所列写按钮均 0，管理员经营计划导入按钮仍为 1，Console error 0。最终 `legacy_phase1_gate.mjs` 全断言 PASS，五类写 API 403、夹具 0 残留，证据 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104133304962/result.json`；前端构建与 110/110、后端 97 套件/676 项、隔离 Playwright 1/1。V064/64，#2 PUBLISHED，8183 health 与正式 5180 只读 200。逐项进度更新为 AUTH-005/006/007/009 共 4/119 PASS，仍有 115 项和阶段独立 Reviewer 未完成；18/19 保持 REVIEW。

## 2026-11-04 Phase 1 兼容与启动日志续验

AUTH-009 续验：隔离镜像 `sha256:a90040ba...` 只重建 API/Worker，临时账号停用后的原 JWT 现在访问 `/plans` 返回 401；旧镜像失败 200 与最终 PASS 分别见 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104120125749/result.json`、`.../legacy-phase1-gate-20261104121840419/result.json`。最终脚本清理用户、旧版本、任务、导出夹具和测试审计，残留 0。后端 97 套件/675 项与隔离 Playwright 1/1 再次通过，8183 health 200、Flyway V064/64、发布版 #2/run #2 PUBLISHED；正式 5180 只读 200。完整 AUTH-009 角色撤权、119 项 UAT、性能与独立 Reviewer 仍未完成。

AUTH-009 权限撤销补验：管理员经权限 API 移除临时账号的 `schedule:view`，原 JWT 读 `/plans` 立即由 200 变 403，停用后再变 401；`test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104122135986/result.json`，清理断言覆盖用户权限和审计。UAT 本轮 AUTH-006/009 两项自测 PASS，117 项仍未完成；完整阶段 19 仍 REVIEW。

状态：`REVIEW`。仅重建独立 `mrp-acceptance-20261104` 的 API/Worker，最终镜像 `sha256:ce7bc504...`；MySQL/Redis 卷、正式主库、5180 与正式 Redis 未写。首次旧镜像临时双厂旧版本与单厂账号探针 FAIL，见 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261104105403840/result.json`。旧 API 写入口、工厂域与任务类型修复后最终镜像同探针全部断言 PASS，含 AUTH-006 双厂发布批次/整机导出拒绝、他人导出任务元数据/下载 403、旧导出入口仍可用、#2 仍 PUBLISHED、临时夹具 0 残留，见 `.../legacy-phase1-gate-20261104115334130/result.json`。

- API 启动日志上一镜像曾打印 Spring 自动生成密码；排除未使用的内存用户自动配置后，最终容器启动成功且日志无该提示。后端最终 `./mvnw test-compile -q`、独立测试 schema/Redis 6391 全量 97 套件/675 项通过；缺 `MRP_DB_PASSWORD` 的一次 3 项上下文错误日志 `/tmp/mrp-stage18-fulltest-scope-20261104.log` 保留，不计为通过。前端 13 文件/110 项通过，Docker 前端构建成功。
- 8183 health/planning/shortage 均 HTTP 200；隔离 Chrome Playwright 1/1 PASS 并保留 trace，1440/1920 负荷格无越界。浏览器 planning/shortage 的 Console error、pageerror、requestfailed、API 5xx 为 0；截图和原始结果见 `test-kit/2026-10-15/evidence/runs/browser-stage18-20261104/`。Flyway 64/64、V064，发布版 #2/run #2 PUBLISHED，临时账号 0 残留；正式 5180/planning 只读 HTTP 200。
- 119 项原始 CSV 保持待执行模板，本轮 AUTH-006 新增执行证据，见 `2026-11-04-stage18-19-uat-progress.md`。性能全负载、10 万物理行 XLSX、完整权限/下载/审计安全、备份恢复、外部飞书回调、v1/v2/v3 真实历史回放和独立阶段 Reviewer 仍缺；阶段 18/19 不改 DONE。

## 2026-11-04 隔离新镜像与已发布版复核

状态：`REVIEW`。首次 `docker compose ... up -d --build --no-deps api worker` 在 Maven Central 依赖下载时收到 TLS `bad_record_mac`，镜像未替换，失败现场保留。用户要求继续后用相同隔离项目重试构建成功，API/Worker 已重建并启动，没有执行 `down -v` 或正式 Compose 重启。

- 新镜像 `8183/actuator/health` HTTP 200；`/planning`、`/shortage` SPA refresh 均 200 HTML。隔离 Flyway 仍 V064/64 条、失败 0；原批次 #1 SUPERSEDED，发布版 #2/run #2 PUBLISHED，未重复发布。
- 登录后的真实 API：#2 批次与 summary 均 200/PUBLISHED；匿名与无效 token summary 均 401。隔离单厂受限账号的跨厂读取均 403，账号及工厂域清理后 0 残留。
- 前端 `npm run build` 与串行 `npm test -- --run --maxWorkers=1` 110/110 通过。原隔离 Playwright 的发布前 #1 强断言已更新为已发布 #2 状态，系统 Chrome `npx playwright test --config=e2e/docker-acceptance.config.ts` 1/1 PASS，保留 1440/1920 负荷格边界断言；浏览器两个工作台 pageerror 0。
- 阶段 18/19 的完整安全、性能、Phase 1 历史兼容、119 项 UAT 与独立 Reviewer 仍未通过，状态均保持 REVIEW。逐项门禁见 `2026-11-04-stage18-19-gate-matrix.md`。

## 2026-11-04 隔离 Docker 成功业务闭环续验

状态：`REVIEW`。本节补上同一 Docker 镜像内成功业务计算和正式发布的运行证据；仍不等于阶段 18/19 全量门禁或最终交付 PASS。

- 仅向独立项目 `mrp-acceptance-20261104` 的业务空库复制已有隔离测试库 `mrp-testkit-mysql` 的夹具：物料 20,053、当前上海组织 BOM 明细 145,801、两厂仓库映射 2、库存策略 3,128、物料策略 1,564、日历 424、业务周 34、已发布分类角色 246 条。正式主库和正式 5180 未写入；Flyway 仍为 V064/64 条。首次 `mysqldump` 因未加 `--no-tablespaces` 输出权限错误，但目标物料/BOM 实际行数已逐表核对；配置复制改用该选项。仓库映射首次 `--where` 引号错误未写入，修正后目标为 2 条。
- `node test-kit/2026-10-15/tools/docker_acceptance_chain.mjs` PASS，原始证据为 `test-kit/2026-10-15/evidence/runs/docker-acceptance-20261104/result.json`。经营计划 XLSX SHA-256 `82090741...ec5f7f4`，预览 2 行/0 错、确认 V1；库存 XLSX SHA-256 `1b17eb91...8c9cdb`，预览 164 行/0 错、确认快照 1。批次 #1 从 `DRAFT` → `READY_TO_CALCULATE` → Worker 排产成功 `SCHEDULE_READY`；整机确认返回 `CONFIRMED`；物料分析任务 202 并由 Worker 成功执行，run #1、状态 `MATERIAL_REVIEW`，开放异常/待分配均为 0。
- `node test-kit/2026-10-15/tools/docker_acceptance_chain.mjs --finish` 最终 PASS，证据 `.../docker-acceptance-20261104/finish.json`。物控确认后提交产能指纹，发布任务 #6 `SUCCEEDED`，原批次 #1 变 `SUPERSEDED`，新批次 #2/run #2 为 `PUBLISHED`，64 位结果 checksum 已落库，双岗确认均为 `CONFIRMED`、反馈 0。首次脚本错误断言原批次应直接 `PUBLISHED`，保留失败现场；按发布任务的 `resultBatchId=2` 修正读取后通过，未重复发布。
- 独立 SQL 对账：V1 两厂 2026-11 各 10 台，run #1 与发布 run #2 的整机日事实各厂均为 10，差额 0；每个 run 均有三种原材料 `11520471`、`25900077`、`43111794`，每料两厂合计 20。两个成功任务的 `worker_id` 均为 Worker 容器 `cf5b0bb926ca-1`，不是 API 容器 `09f1bad0d813`。
- 隔离浏览器 `8183/planning` 实见整机已确认、两厂整机行；`8183/shortage` 实见六条原材料行和独立物控操作，发布后显示批次 #2/run #2，Console error/warn 0。`npx playwright test --config=e2e/docker-acceptance.config.ts` 1/1 PASS，含 1440×900 与 1920×1080 的负荷格文字边界强断言。无 token 访问隔离 summary 为 401；正式 `5180/planning` 只读 HTTP 200。
- 旧 `two-workbench-compact.spec.ts` 用默认 `playwright.config.ts` 指向正式 5180，三项均在登录跳转等待中超时，失败现场保留在 `frontend/test-results/`；该次结果既不代表隔离 8183 的业务失败，也不算 PASS。隔离专用配置已固定 8183、系统 Chrome 和当前发布前状态断言。
- 本轮前端 `npm run build` PASS、`npm test -- --run --maxWorkers=1` 110/110 PASS；后端 `./mvnw compile -q` PASS、`./mvnw test -q` 退出码 0，Surefire 97 套件/673 项/0 失败/0 错误/0 跳过。`git diff --check` 和脚本 `node --check` 均退出 0。旧历史报告中的 5180/8183、版本与测试数字须按当时日期解释。
- 仍未关闭：阶段 18 的全范围性能/安全/兼容、10 万行 XLSX 实际导入、完整 119 项 UAT、正式 Compose 实际重建及阶段独立 Reviewer。阶段 18/19 均保持 `REVIEW`；测试夹具和发布版保留在隔离库供复核，不清卷。

## 2026-11-04 隔离 Docker 运行续验

状态：`REVIEW`，本节仅确认 Docker 空库启动、镜像资源、Worker 基本消费和重启持久性；不代替完整 UAT、性能/安全门禁或独立 Reviewer。

最终续验：发现 API 抢占探针任务后，移除两个应用入口的重复 `@EnableScheduling`，新增仅对 `worker` 或非 `prod` profile 生效的 `WorkerSchedulingConfig`。隔离 `prod` API 和 `prod,worker` 同时在线时，探针任务 #3 的 `worker_id=cf5b0bb926ca-1` 与 Worker 容器 hostname 一致，API 容器 hostname 为 `09f1bad0d813`；任务按预期因负批次不存在失败。正式 Compose 已为 Redis 配置必填 `MRP_REDIS_PASSWORD`，并传给 API/Worker；`MRP_REDIS_PASSWORD=validation-only docker compose -f deploy/docker-compose.yml config --quiet` 退出 0，仅解析配置，未重建正式 Redis 或正式卷。最新修改后 `./mvnw compile -q`、`./mvnw test -q`、隔离镜像构建均退出 0；Surefire 97 套件、673 项、0 失败/错误/跳过。重建后 API health 200、`/planning` 200、隔离浏览器刷新登录页正常且控制台无 error/warn，迁移仍为 V064/64 条、失败 0，checksum 汇总不变。

- 文件与不变量：`backend/Dockerfile` 移除耗时且会遍历非打包依赖的 `dependency:go-offline`；`deploy/docker-compose-acceptance.yml` 仅在独立项目内给 Redis 配置临时口令并传给应用；`SecurityConfig` 仅在 Servlet 应用加载，`PasswordConfig` 保留两种进程所需的密码编码器。正式 MySQL/Redis 卷与 5180 服务未修改。
- 空库起始业务表 0 张。Node 24 + Java 21 多阶段镜像 `mrp-acceptance-api:local` 构建退出 0，前端 Vite 构建与后端 Maven package 均成功。首次隔离 API/Worker 启动完成 V001→V064，`flyway_schema_history` 为 64 条、最高 064、失败 0。
- 首次运行发现两个真实错误并修复：Redis 无口令却收到 AUTH，导致两个进程退出；Worker 非 Web 模式仍实例化 MVC `SecurityFilterChain`，缺少 `mvcHandlerMappingIntrospector` 后退出。修复后 API 与 Worker 均启动；`http://127.0.0.1:8183/actuator/health` 返回 200/UP，`/planning`、`/shortage` 的 SPA 刷新返回 200 HTML，引用的 `index-CUEfJ5FI.js` 和 `ShortageWorkspace-D4DUCrxj.js` 均返回 200 JavaScript。
- 隔离 API 停止时写入一条 `MRP_CALCULATION` 探针任务，负批次 ID 故意不存在；Worker 独立将其从 `PENDING` 认领为 `FAILED`，`worker_id` 有值，错误为预期的 `CALC_ERROR: Batch not found: -1`。这证明认领/错误回写链路，不证明成功业务计算。
- API/Worker 重启后均报告 64 条迁移已校验且 `No migration necessary`；迁移版本/checksum 汇总 SHA256 前后均为 `7d7422302f97999114d1516163e69fe1e8fd9aa46edd509b5b1cf9c902e9d7ad`，探针任务仍为 `FAILED`，health 200。
- 修改后 `./mvnw compile -q`、`./mvnw test -q` 均退出 0；Surefire 97 套件、673 项、失败/错误/跳过均 0。`git diff --check` 退出 0。
- 开放缺口：正式 Compose 的凭据接线仅经配置解析，未重启正式部署；Worker 成功业务计算仍需完整 UAT 证明。完整 UAT、性能/安全、独立复核仍未通过；阶段 18/19 不改为 `DONE`。

## 2026-11-04 迁移门禁续验（当前结论）

状态：`REVIEW`。以下只关闭隔离 MySQL 迁移专项和旧脚本的误操作风险，不替代完整 Docker/UAT 或最终审计；下文旧日期的 PASS/阻断描述均须按其当时环境解释。

- 新增 `MigrationAcceptanceTest`，使用两套 Testcontainers 临时 MySQL 8，无固定容器名、端口或共享卷。空库 V001→V064 执行 64 个迁移，`validate` 通过，第二次执行 0；V001→V020 执行 20 个、V020→V064 执行 44 个，V001–V020 checksum 与 2026-11-04 只读取得的主库已应用基线一致且升级前后相同，既有经营计划 sentinel 行保留，`validate` 通过，第二次执行 0。此处只证明重复迁移调用幂等，容器重启另验。`./mvnw test -Dtest=MigrationAcceptanceTest` 首轮退出码 0，2/2 PASS；固化历史基线后的复测结果见下文续记。
- `deploy/test-docker-acceptance.sh` 改为上述隔离迁移专项入口，不再运行 `docker compose down -v`；`deploy/run-uat-tests.sh` 的固定批次写入和旧 API 已撤除，执行时明确退出 2，不能再伪报 UAT PASS。`bash -n` 和 `git diff --check` 均退出 0。
- 改动后后端 `./mvnw compile` 退出 0；`./mvnw test -q` 退出 0，Surefire 97 套件、673 项，0 failure/error。前端 `npm run build` 退出 0；默认并行 `npm test -- --run` 曾有 1 项 5 秒超时（109/110），该项单独复跑 6/6 PASS，串行全量 `npm test -- --run --maxWorkers=1` 为 110/110 PASS。超时不是已证明的业务逻辑失败，默认并行门禁仍需稳定性复核。
- 真实运行态只读核查：`http://localhost:5180/planning` 和 `/shortage` 登录后同为已发布批次 #120；缺料页显示末级物料列表。5180 路由 200、8081 health 200；1440×900 与 1920×1080 各抽 12 个整机负荷日格，文字越界 0；该浏览器标签控制台 error/warn 0。主库未执行写入。
- 仍未完成：独立 Compose 项目中的 API/Worker/静态前端重启和任务消费、完整 Phase 2 UAT、性能/安全/兼容全量门禁、独立阶段 Reviewer PASS。阶段 18/19 均保持 `REVIEW`。

## 状态

`REVIEW_BLOCKED_BY_BUSINESS_DATA`

截至 2026-09-29，Phase 2 代码、数据库迁移、自动化测试和重点页面真实浏览器验证已完成；但完整的新批次业务闭环仍被真实主数据不一致及必要配置未发布阻断，不能标记为 `DONE`。

## 已确认通过

| 验证项 | 结果 | 证据 |
|---|---|---|
| Docker Desktop / MySQL / Redis | PASS | `mrp-mysql` healthy，3307；`mrp-redis` running |
| 空库迁移 | PASS | Testcontainers 从 V001 执行至 V034 |
| 后端全量测试 | PASS | `./mvnw test`：221 项，0 失败，0 错误 |
| 前端生产构建 | PASS | Node 24 下 `npm run build` |
| 前端类型检查 | PASS | `npx vue-tsc --noEmit` |
| 前端单元测试 | PASS | Vitest 18/18 |
| 后端运行 | PASS | `http://localhost:8080` 真实启动 |
| 前端运行 | PASS | `http://localhost:5180` 真实启动 |
| Phase 2 配置 API | PASS | 物料策略、生产日历、仓库映射、库存策略、业务周接口均返回业务响应 |
| 批次 issues API | PASS | `/api/v1/mrp/batches/79/issues` 正常，无重复 handler |
| 旧批次安全门禁 | PASS | 缺锁定配置时前端禁止预检，后端返回中文 `MRP_ILLEGAL_STATE` |
| 权限配置 | PASS | `/roles/1/permissions` 可见并勾选业务周等 Phase 2 权限 |
| 配置页面访问 | PASS | 物料策略、生产日历页面不再跳转登录页 |
| 库存导入展示 | PASS | 导入弹窗、仓库字段、品质列和良品/不良品中文显示正常 |
| 历史乱码修复 | PASS | V034 修复已知 UAT 日历原因乱码，浏览器显示“UAT全生产日” |

## 本轮关键修复

1. 增加旧批次“放弃当前排产”能力；正式发布批次不可作废。
2. 对未锁定工厂仓库映射、库存策略、业务周版本的旧批次增加前后端双重预检门禁。
3. 修复生产日历已知双重编码历史数据，仅精确更新已确认的 UAT 值。
4. 修正 Phase 2 E2E 页面标题和当前批次入口断言。
5. 修正验收台账中 Docker 不可用、工作台未实现、Playwright 路径硬编码等过时结论。

## 自动化 E2E 环境阻断

执行 `npx playwright test` 时，40 个用例均在浏览器进程启动前停止，原因是本机缺少 Playwright 管理的 Chromium Headless Shell。该结果不是 40 个业务断言失败，也不能算 40 个用例通过。

项目规则禁止代理自行安装或切换环境版本，因此本轮采用真实 Google Chrome 对重点页面进行人工验收，并将全量 Playwright 记录为 `BLOCKED_ENVIRONMENT`。

## 完整业务流阻断

### 1. 工厂编码不一致

| 数据来源 | 当前值 |
|---|---|
| 产能配置 `capacity_line.factory_code` | 永惠、爱培科 |
| 经营计划版本 1 | F001、F002 |
| 已发布生产日历 | F001 |

在业务确认统一编码和迁移口径前，系统无法可靠关联产能、经营计划、日历和库存仓库。

### 2. 新版库存与仓库配置未准备

- 现有库存快照的 `warehouse_code` 为空，不能用于“一工厂一仓库”的新版计算。
- 当前无已发布工厂仓库映射版本。
- 当前无已发布库存控制策略版本。
- 不应由开发代理猜测 F001/F002 与永惠仓、爱培科仓、惠州仓的对应关系。

### 3. 业务周未准备

当前无已发布业务周版本，无法对真实月份验证 W1-Wn 的周汇总结果。业务需维护每月的周日期范围，日排产仍以日期为事实，周页面只做汇总。

## 当前不能声称通过的场景

以下流程虽已有代码与定向测试，但尚未在当前真实业务数据上完成端到端证明：

1. 发布工厂仓库映射、库存策略和业务周；
2. 使用包含仓库、品质字段的新版库存快照；
3. 创建新版批次；
4. 预检并处理业务决策；
5. 执行净需求计算和日排产；
6. 校验安全库存、预警库存、共享库存经营计划顺序；
7. 查看 W1-Wn 周汇总；
8. 发布、执行确认、月中重算和修订版本。

## UAT 前置数据清单

业务准备以下数据后，阶段 19 才能继续完成：

- 统一后的工厂编码清单；
- 工厂与永惠仓、爱培科仓、惠州仓的一对一映射；
- 每个工厂/仓库/物料的安全库存和预警库存；
- 至少一个目标月份的 W1-Wn 日期范围；
- 与上述工厂、仓库一致的经营计划、生产日历和新版库存快照；
- 可计算的分类角色、物料策略和已固化 BOM 快照。

## 审计结论

代码层和重点交互缺陷已完成一轮闭环，Docker 与空库升级已恢复可信证据。阶段 19 继续保持 `REVIEW`，不得因为测试类通过而替代真实业务数据闭环。下一轮应先完成主数据治理和业务配置发布，再执行完整“创建→预检→决策→计算→日排产→发布→修订”UAT。

## 2026-11-05 隔离镜像凭据链复核

阶段状态保持 `REVIEW`。最终代码的前端 build/110 单测、后端 97 套件/678 项、隔离 Docker 镜像构建通过；仅替换隔离 API/Worker，8183 health 200/UP、Flyway V064/64，发布版 #2 仍 PUBLISHED。真实 Chrome 完成临时账号首次改密、等待权限页和旧 JWT 失效验证，账号已删除；正式 5180 仅只读 200。外部飞书回调、备份恢复、完整 119 项 UAT、正式 Compose 运行和独立阶段复核仍未完成，阶段 19 不改 DONE。详见阶段 18 报告与门禁矩阵。

## 2026-11-05 管理员引导后续验

此节更新上述旧计数。隔离 8183 的 V001 播种管理员已一次性引导和首次改密，旧固定口令与引导口令均拒绝；API/Worker 当前引导变量为空，#2/run #2 仍 PUBLISHED，MySQL/Redis 卷未清理。最终后端 99 套件/684 项、前端 110 项、隔离 Playwright 1/1，发布版与 summary 的真实 API 均 200/PUBLISHED。验收脚本已移除两处旧固定登录口令并要求环境变量，但完整写入链未因脚本改动重复执行。ENV-005~008 本轮逐项有真实命令，独立 UAT 进度为 12 PASS/107 NOT_RUN；原始 119 项 CSV 仍是未执行模板。完整业务 UAT、正式凭据轮换、性能与安全总门禁、备份恢复及独立 Reviewer 未过，阶段 19 保持 REVIEW。

隔离旧接口安全探针在凭据轮换后重新可运行，24/24 断言 PASS、临时数据 0 残留，见 `test-kit/2026-10-15/evidence/runs/legacy-phase1-gate-20261105080353273/result.json`；后端最终再回归 684/684，前端 build/110、Chrome 1/1。此探针是现有 AUTH-005/006/007/009 与旧接口限定范围复核，不新增完整 UAT 项；总进度仍 12/119。正式资源未写，隔离发布版 #2 保留。

## 2026-10-16 修复上线与真实数据库补证

本节为旧报告的日期补充，历史环境/数据阻断不直接代表当前运行状态。**本轮定向修复经原Reviewer PASS，阶段19仍为REVIEW。** 报告为 `test-kit/2026-10-16/修复完成报告.md`，复核为 `test-kit/2026-10-16/evidence/repair/independent-repair-review.md`。

- 已使用系统Chrome执行Playwright：主/隔离页面冒烟各16PASS、权限保存及整数联动预览2PASS；主库91/隔离11月表各77格抽样PASS。该证据更新旧“Playwright未执行”状态，不代表119项全交互通过。
- 隔离真实MySQL完成配置/经营计划/库存导入、冻结上海BOM、异步计算和读取，批次7及9分别发布为8及10。双厂同SKU库存样本、217275日键大批次、38850日键完整叶子样本独立核算通过；实际深度最大2。保留逐页采集预算失败，后续SQL/bulk补证不替代分页门禁。
- 主后端已按原配置重启；按用户C021授权同步主库上海BOM：3425父项/145808明细，新快照34 READY、121717边、重复父子边0。隔离库同号34为121711边，属于不同内容，不能混同。
- 主库批次91/run37、224100日事实、0人工override、历史快照33的134045边，以及20053物料/250分类前后行数与SHA256一致，见 `main-rollout-verification.json`。此次授权替换的只有现行本地BOM及新快照，历史结果未重算。
- 页面核验：主库历史结果 `http://localhost:5180/planning/91`；新上海隔离发布大批次 `http://localhost:5181/planning/10`。主库新上海快照没有重新执行大批量计算；隔离结果来自真实MySQL。
- 本轮没有补完V020 Docker升级、故障/低堆恢复、并发写、三轮固定输入checksum、1万/10万实际导入、导出完整性、工厂数据域和全部UAT；不能重新标记阶段19或FINAL-AUDIT完成。分页、summary及同步发布耗时仍为开放性能债务。
