# 任务报告：经营计划和库存导入至整机排产执行

## 状态

REVIEW（Phase 2 阶段 18/19）；本轮 42/42 条场景自测及独立复核 PASS，不能宣布项目最终验收。逐项状态及原始证据以执行记录为准。

## 本次完成

- 实测异常文件原九种及新增十三种均由预览识别且确认 400，正式四表行数不变；修复经营计划空料号、负数、重复月份以及库存空文件、未知料号、超数据库精度的上传前校验。
- 实测双厂经营计划/统一库存导入、预检、Worker 排产、Decimal 守恒、整机确认、物料交接，以及同字节幂等与二次建批次冲突。
- 建立逐场景真实状态和证据索引：`docs/经营计划与库存导入至整机排产端到端执行记录-2026-11-02.md`。
- 修复经营计划新版生效时旧工作流状态漏失效的问题；隔离 V32/快照 38 导入后 batch 74 失效，新 batch 75 创建、预检、Worker 计算和整机守恒通过。
- batch 75 调日 1 台后整机确认失效，旧指纹 409；重新确认后物料 task 49 成功、同一 run 69 进入 `MATERIAL_REVIEW`。日/周/月汇总 285127 台守恒，368 个工厂日负荷与整机事实逐键相等；浏览器双视口横轴 5 点负荷文字未溢出。
- 用户确认 PLANNER/MATERIAL 默认可看全部工厂；V064 对无自定义域的两角色播种 ALL。隔离库 V063→V064 与 Testcontainers 空库全迁移成功。独立账号分别登录排产/缺料，本岗 API 200、越岗 API/页面 403，测试账号清理为 0；登录默认入口按页面权限选择。
- 2026-11-03 补 B14 完整双岗链路：页面导入 V35/快照 41，排产员确认 batch 78，物控先后看到未排产/待确认阻断原因，再提交 task 53；run 71 按两厂展开 3 种原材料，各厂各料需求 10。四张截图、结果 JSON、两份 XLSX 和 SHA-256 持久化。七张主库受保护源表前后行数和 SHA-256 一致。
- 2026-11-03 续测 I06/I09/F12/I10：部分仓、组合版本和范围外仓均明确阻断；浏览器能展示缺仓和范围外原因。旧快照 47 的 batch 96 与新快照 63 的 batch 97 分别产生爱培科净需求 703 和 0，旧结果不变，两批取消。两类历史列表分页、搜索、空态及模拟 HTTP 500 后恢复通过；累计 29/42 自测 PASS。
- B20 发现版本抽屉遗漏批次输入校验码，已补全 64 位值并处理长码换行。已发布历史 #73 与失效草稿 #75 的版本/输入/结果码按各自状态展示；隔离 Playwright 3/3、正式 5180 只读工作台 3/3。累计 30/42 自测 PASS。
- B19 使用隔离历史 #73 补齐浏览器第 2 页整机行、末月 2027-04、网格模拟 500 后恢复；结合此前两视口负荷格与搜索空态证据，累计 31/42 自测 PASS。
- F12/I10 现按完整矩阵补验：两版经营计划逐版对照 API 的料号、月值及六个月列，库存快照 63 跨页料号/仓库搜索分别得 2/82 行；列表、详情均实测 401/403，页面展示 403 错误并在 401 后返回登录。库存详情原先只过滤当前 20 行，已改为服务端搜索/计数，前端忽略过期响应；独立 MySQL 专项和浏览器均通过。修正新增测试脚本的 DOM 定位后 4/4 PASS，累计 31/42 自测 PASS。
- B02 补测发现弹窗漏检仓库良品属性确认状态；已将 `qualityConfigured` 纳入确认条件并显示待维护仓库。隔离浏览器模拟未确认品质，确认禁用且提示准确；正式 5180 正常弹窗加载通过。8 组无效建批 API 均 400；缺前月业务周页面/API 均阻断。无所选工厂需求 `NO_FACTORY_FORECAST`；临时清空上海 BOM 时当前 BOM 数为 0、建批 400、浏览器禁确认，恢复 145801 行后 checksum 不变。真实停用永惠仓库映射时建批 400 且点明 `UAT_YH`，页面缺映射提示/禁确认；映射恢复。仅一厂无需求的 `ALL` 批次 #110 可创建并已取消。B02 自测 PASS。
- B03 日历门禁闭环：两个必需日缺失（含跨月前一业务周）均在预检 400 且无计算任务；显式休息日不当成缺配置；整周停产的真实 Worker 把 200 台保存在 `NO_PRODUCTION_DAY` 待分配，守恒差异 0；缺前月业务周的页面禁确认及直调 API 400。所有测试批次取消，日历与业务周恢复；首轮测试脚本字段误读的失败记录保留。B03 自测 PASS，累计 32/42。
- B04 按 C025 修正测试时点：整机候选可先生成，缺良品库存记录须在物料分析阶段转为物控待办。首轮 #106 仅不良品行却漏报，修复库存记录存在性判断后 #107 产生 `INVENTORY_MISSING` 并阻断物料确认，按零确认后 RESOLVED；#108 显式 0 良品行不误报。#109 临时停用分类角色，预检进入 `WAITING_DECISION`、计算拒绝、处理后恢复可计算。三批取消，配置恢复；B04 自测 PASS，累计 33/42。
- 最新代码后端 `./mvnw compile` 与全量 `./mvnw test` 669/669 PASS；前端 `npm run build`、`npm test -- --run` 110/110 PASS；隔离 B02 浏览器 2/2。正式本地后端按 `MRP_SERVER_PORT=8180` 和原 `local` profile 重启，`8180/actuator/health` 200、5180 首页 200、未登录代理批次 API 401，浏览器登录页可渲染。首次漏传端口变量启动到 8080，已停止并纠正；没有对正式库执行写入测试。累计 34/42，阶段 18/19 继续 REVIEW。
- B16 首轮并发 #112 发现同 run/指纹的确认写入两条；`MrpWorkbenchReviewService.confirm` 增加当前确认的幂等返回，真实双独立登录复测 #114 的第二次建批 409、双预检/计算各一成一拒、唯一任务/run、双确认 200 且单条审计确认记录。Testcontainers 双岗发布用例新增两类重复确认断言，专项 8/8、最新全量 669/669 PASS；批次取消。失败重试有效版本唯一性仍未覆盖，B16 保持 NOT_RUN。
- B15 的当前 BOM 变更在隔离 #116 实测：预检 400、无 run、冻结 checksum 不变，恢复源行后预检可计算；其余输入变更和历史修订仍开放。B05/B17 的 Worker 故障注入首轮抢先认领，次轮临时触发器因 MySQL binlog 权限被拒；失败记录保留，测试用例从 Playwright 配置移除，未改变数据库权限。隔离库无活动批次、BOM/映射恢复、无触发器；正式后端再次在 `8180` 重启，健康 200、`5180` 首页 200、未登录代理 API 401。整体仍 34/42。
- B05/B17 补确定性失败与恢复：Testcontainers 真实 MySQL、真实物料引擎在物料日事实写入后抛错，任务 FAILED 且保留错误原因；同一批次回到 SCHEDULE_READY，原整机 run/8 台事实完整，物料需求与日事实均回滚为 0。撤销故障后同批次重提任务成功，沿原 run 进入 MATERIAL_REVIEW 并最终发布。正式 5180 的排产/缺料交接与 1440/1920 负荷格只读浏览器回归 2/2 PASS；其余缺口见下条与“未完成与风险”。
- B05/B16/B17 再补整机日分配后确定性失败：任务 FAILED 并保留错误，批次恢复 READY_TO_CALCULATE，无有效 run、需求或日事实；同批次重试成功，随后物料阶段故障与重试也沿同一有效整机 run 完成，最终唯一输入校验码仅有 1 个 PUBLISHED 版本。B16 连同此前真实双账号并发和确认幂等自测 PASS，累计 35/42。B05 页面失败进度、B17 进程重启/Redis 中断仍开放。
- 2026-11-03 B05/B15/B17 全部补完自测：#129/#130 冻结配置差异和恢复；#131 运行中强杀 JVM、租约回收后二次认领成功；#133 浏览器真实 FAILED 错误提示和点击重试成功；#134 Redis 短断时任务完成。Testcontainers 发布版修订后原版状态、checksum、run、事实不变。源表七表各窗口 SHA 不变，隔离无活动批次；首轮 B05 用例取消竞态的失败 trace 保留。累计 42/42 场景自测 PASS，等待独立复核。
- 独立复核首轮指出 B05 缺真实运行中页面进度，补 `CalculationProgress.vue` 的任务号显示和隔离 #141 浏览器断言：真实任务 #117 RUNNING、76%、保存日排产（0/32），页面刷新仍为同任务；隔离库结果表短锁 20 秒自动释放后 Worker 成功、唯一 run、批次取消。`current-worker-failure-ui.spec.ts` 运行中与失败重试组合 2/2 PASS；截图 `test-kit/2026-10-15/evidence/e2e-current-2026-11-02/current-worker-failure-ui--22519-ves-a-planning-page-refresh/b05-running-task.png`。首次导航、自然窗口及锁夹具失败 trace 保留；独立 Reviewer 复核 B05/B15/B17 和其余 39 项后判本轮 42 项 PASS。
- 补 F02 混合合法/非法文件的浏览器禁确认与错误报告列序：当时异常文件页面 6/6，逐行报告最后一列为“错误原因”；原始 API 202/400 与正式四表不变证据复核通过。I02 后续补足空数量、非数字与负数页面场景，最终 9/9，整条自测 PASS。
- 强化双岗链复跑 V37/快照 43/batch 79/run 72：预览前当前经营计划和库存快照数不变，确认后唯一新增；六个月两厂 12 条经营计划、统一库存双仓 164 条 GOOD 明细、批次 DRAFT 冻结输入和双厂范围逐项对账。F01/B01 自测 PASS；后续快照 44/batch 80 已补不良品仓并将 I01 自测置 PASS。

## 修改文件

- 业务修复：`ForecastExcelParser.java`、`InventoryItemExcelParser.java`、`InventoryImportService.java`、`TemplateGenerator.java` 及相关解析/确认测试；旧工厂测试夹具修正为六个月模板，库存 API 测试夹具补有效料号；经营计划和库存公式单元格预览拒绝并保留公式原文。
- 执行与回归：`test-kit/2026-10-15/tools/current_import_boundary.py`、`current_import_matrix.py`、`generate_current_ui_imports.py`、`current_source_snapshot.py`、`frontend/e2e/current-import-boundary.spec.ts`、`current-import-positive.spec.ts`、`current-import-first-version.spec.ts` 及隔离 Playwright 配置。
- 本次记录：执行记录、原方案状态和本报告。
- 批次失效修复：`ForecastBatchInvalidation.java`、`ForecastImportService.java`、`ForecastWorkspaceService.java`、`MrpCalcBatchMapper.xml`、`MrpBatchController.java`、`MrpTaskWorkerService.java`、`DailyPlanningService.java` 及 `ImportConfirmationIntegrationTest.java`、`ForecastWorkspaceIntegrationTest.java`、`MrpTaskWorkerServiceTest.java`；失效历史页修复 `MrpWorkspace.vue`、`mrpDisplay.ts` 及文案测试。
- 本轮工厂域修复：`V064__default_workbench_role_factory_scope.sql`、`WorkbenchRoleScopeSeedIntegrationTest.java`；登录入口 `menu.ts`、`LoginView.vue`、`FeishuCallback.vue`、`ForbiddenView.vue`、`router/index.ts` 及菜单单测；隔离浏览器 `workbench-role-scope.spec.ts`、`start_backend.py` 和 B14 复测证据。
- 本次测试修正：`frontend/e2e/workbench-role-scope.spec.ts` 改为读取当前批次 ID；`frontend/e2e/current-bom-role-chain.spec.ts` 把结果和关键页面截图保存到夹具证据目录；`frontend/e2e/current-import-boundary.spec.ts` 增加混合文件及 XLSX 末列校验；执行记录、结果 JSON、问题台账同步更新。
- 本次续测新增 `frontend/e2e/current-history-scope.spec.ts` 与配置登记，修正 `test-kit/2026-10-15/tools/current_snapshot_freeze.py` 的提交响应、任务 ID、MySQL 字符集及失败清理断言；执行记录与报告同步更新。
- B20 修正 `frontend/src/views/mrp/MrpWorkspace.vue` 版本抽屉输入/结果校验码展示与长文本换行；相应 Playwright 用历史发布版和失效草稿分别核验。
- I10 搜索修复：`InventorySnapshotAccessService.java`、`InventoryItemMapper.java/.xml`、`InventoryImportService.java`、`InventoryController.java`、`InventoryImport.vue`，专项 `InventorySnapshotScopeIntegrationTest.java` 和 `current-history-scope.spec.ts`。
- B02 弹窗门禁：`frontend/src/views/mrp/components/BatchCreateDialog.vue` 与对应单测 `BatchCreateDialog.test.ts`。
- B03 隔离夹具：`test-kit/2026-10-15/tools/current_calendar_missing_gate.mjs`、`current_calendar_stoppage_gate.mjs`、`current_calendar_stopped_week.mjs`；业务周页面/API 夹具 `frontend/e2e/current-business-week-gate.spec.ts`。
- B04 修复与夹具：`NetRequirementEngine.java`、`BatchOrchestratorService.java`、`MrpCalculationService.java`、`MaterialAnalysisStageService.java`、`MissingInputIssuesTest.java`；隔离脚本 `current_missing_root_inventory.mjs`、`current_missing_role_gate.mjs`，修复前失败与修复后证据均在 `test-kit/2026-10-15/evidence/runs/`。
- B02 后续夹具：`test-kit/2026-10-15/tools/current_batch_source_gates.mjs`、`current_batch_mapping_gate.mjs`、`frontend/e2e/current-batch-source-gate.spec.ts` 及 Playwright 配置；仅在隔离库短时清空并恢复上海 BOM/停用并恢复永惠映射，恢复后内容 checksum 与原值一致。
- B15/B16 夹具和修复：`current_bom_checksum_gate.mjs`、`current_batch_concurrency.mjs`；`MrpWorkbenchReviewService.java`、`TwoWorkbenchRealEngineFlowIntegrationTest.java`。B05/B17 未通过的浏览器故障注入 trace 保留，试验用例已移除。
- B05/B17 新增 `TwoWorkbenchRealEngineFlowIntegrationTest.java` 中物料日分配后故障、回滚与重试断言；执行记录和问题台账同步保留未覆盖边界。
- B05 补 `frontend/src/views/mrp/components/CalculationProgress.vue` 运行中任务号，以及 `frontend/e2e/current-worker-failure-ui.spec.ts` 的真实进度、刷新和隔离表锁夹具。
- 本轮新增 B15 冻结配置隔离脚本 current_config_freeze_boundary.py；B17 真实 JVM 中断与 Redis 中断脚本 current_worker_process_recovery.py、current_redis_outage_task.py；B05 页面真实失败和重试用例 current-worker-failure-ui.spec.ts。TwoWorkbenchRealEngineFlowIntegrationTest 增加自造已发布版的修订边界断言。

## 业务不变量

- 错误文件不能产生正式经营计划/库存记录；合法文件仅确认后入版。
- 同一批次以冻结输入计算；整机先确认，物控后开始分析；整机事实按工厂独立守恒。

## 验证命令与结果

详见执行记录及原始 JSON。本次后端 `./mvnw compile -q` PASS、`./mvnw test -q` 退出码 0，Surefire 96 套件、671/671 PASS（零失败/错误/跳过）；此前定向 `TwoWorkbenchRealEngineFlowIntegrationTest` 1/1 PASS。前端本次 `npm run build` PASS、`npm test -- --run` 110/110 PASS，隔离 5192 Playwright B05 2/2 PASS。8182/8180 健康 200，5192/5180 首页 200；写入测试只走 Testcontainers 或隔离 8182/5192。2026-11-03 20:09 主库受保护七表与本轮 11-02 后测指纹逐表一致，隔离活动批次 0、Redis PONG、日排产结果表无残留锁。

## 未完成与风险

- 本轮 42/42 项场景自测和独立复核 PASS；阶段 18/19 全量门禁尚未完成，继续保持 REVIEW。B17 进程恢复测试为了确定性只对单个隔离任务推进租约到期，不能代表自然等待五分钟的时间精度；Redis 中断持续约四秒。
- 首次持久证据脚本误用整机料号作目录，产生 batch 77 草稿；经正式 API 取消后修正并重测。一次前端全量单测并行超时，最终全量重跑通过；两类失败均保留过程记录。
- 旧源库保护基线不可比；本轮独立前后窗口 7 张表行数与 SHA-256 一致，但不能将历史差异归因本轮。
- `5180` 连接主库的服务仍在，写入测试只允许隔离入口；阶段 18/19 维持 REVIEW。

## 下一步

- 本轮已补 I01/I02：隔离库存快照 44 含良品 164 条、不良品 1 条；batch 80/run 73 证明不良品 1000 件标准化后不可用且不入库存池、两厂整机日事实各 10。导入 API 负例 15/15、页面 9/9，前端 build 和 109/109 单测通过；两批次经正式接口取消。原始证据及主库源表指纹见逐项执行记录。
- D2 已补五档根库存 API/Worker 对账：缺行、显式零、部分、足额、超额分别得到净需求 1003/1003/703/0/0，双厂未串库存；五批次正式取消。安全/预警库存全链路新增 batch 87/88：冻结保底 100、预警 250；300 件良品扣 200、净需求 803、物料阶段安全风险剩余 100；1200 件扣 1003、净需求 0、预警风险剩余 197。另导入双来源月 V39，batch 89/run 82 逐月扣 200/100，库存总扣减 300，月净量 0/100、逐月守恒，永惠不串厂。批次 87–89 取消、策略恢复，主库七张受保护表前后指纹一致。证据 `test-kit/2026-10-15/evidence/runs/2026-11-03-stock-policy-full-103342/result.json`、`test-kit/2026-10-15/evidence/runs/2026-11-03-stock-cross-month-103734/result.json`；B06 自测 PASS，待独立复核。
- I05 新增快照 50 的同仓同料双供应商 100/200 行，原始两行保留、规范化良品 300 仅扣一次，不良品 1000 只追溯；batch 90/run 83 净量 703。缺行快照 45 与显式零快照 46 的记录差异和净量 1003 已核对；batch 90 取消，主库受保护表指纹不变。证据 `test-kit/2026-10-15/evidence/runs/2026-11-03-stock-duplicate-104325/result.json`；I05 自测 PASS。
- I04 旧 8 列品质文件与新 7 列模板实测：匹配良品仓的“良品”确认快照 51/GOOD 10；同仓填“不良品”预览 `QUALITY_MISMATCH`、直接确认 400，正式表只因合法文件增加 1 快照/1 明细。不良品库存不可扣由 I01/I05 覆盖。证据 `test-kit/2026-10-15/evidence/runs/2026-11-03-legacy-quality-104631/result.json`；I04 自测 PASS。
- I03 首轮实测 FAIL：合法库存预览后把仓库从良品改为不良品，旧确认仍 HTTP 200，生成隔离快照 52；保留现场。修复 `InventoryImportService.confirmItem` 重新校验并逐行比对预览品质，新增 Testcontainers 回归。重启隔离 API 后空/未知/停用/历史未确认仓库与品质变更均 400，正式表零增量，映射已恢复；专项 24/24 PASS。最终后端 `./mvnw compile -q` PASS、`./mvnw test -q` **663/663 PASS**（96 个套件，零失败/错误/跳过），8182 API 与 5192 隔离页面 HTTP 200、无未关闭隔离批次。首轮全量因新增测试夹具缺少七列表头导致断言文案不符，修正测试夹具后重跑通过；不隐藏首轮失败。证据 `test-kit/2026-10-15/evidence/runs/2026-11-03-warehouse-guards-105012/result.json`、`.../2026-11-03-warehouse-guards-110934/result.json`；I03 自测 PASS。
- 一次诊断误导入带顶层执行的旧 D2 脚本，隔离环境新增 V40/快照 53/batch 91；立即中断，正式 API 取消 batch 91，并以完整有效库存文件新建快照 54 作为当前最新。未完成 `progress.json` 保留，不计场景通过；恢复记录 `test-kit/2026-10-15/evidence/runs/2026-11-03-warehouse-guards-110934/cleanup.json`，当前无活动批次。
- 用隔离 API/浏览器补测门禁和业务边界，完整闭环后再建立覆盖整轮写入的源库前后保护证据。
- 2026-11-03 料号与整数整机量补测：文本 0209006 正式经营计划保留 6 条月明细，库存快照保留精确 1.123456；七张受保护主库源表 SHA 前后相同，证据 2026-11-03-leading-zero-115346/result.json。数值型料号、空/未知料号及隐藏/稀疏行隔离负例 8/8，正式四表不变，证据 2026-11-03-material-cell-123224/result.json；F03 自测 PASS。数量矩阵 9/9：原先经营计划小数台可预览，但排产只允许整数台；现预览 INVALID_QTY、确认 400。修复后后端编译 PASS、全量 667/667 PASS（96 套件，零失败/错误/跳过）；8182 重启，5192 和正式 5180 只读 HTTP 均 200，隔离矩阵复跑 17/17。首次全量 2 项因旧测试夹具使用小数整机量失败，修正后全量通过，首次失败记录保留。F07/F08/I07 仍有未测断言，不能标整条通过。
- 后续补齐 F07/F08/I07 断言：隔离数量矩阵扩到 16/16，空格、整数/小数科学计数法、最大库存精度、末月数量缺失与纯空行均有原始 XLSX、SHA 和 API 响应；四张正式表无新增。隔离浏览器 10/10，新增小数台的弹窗错误、禁确认及错误报告末列通过；前端 build、109/109 单测 PASS。本地主后端 8180 按原配置重启、健康 200，前端 5180 页面 200、代理未登录 401；未在主库执行写入测试。三个场景自测置 PASS，整体仍 22 条未关闭。
- F06/F04 隔离补测：真实 Excel 日期表头跨年六个月确认后生成版本 43，正式明细逐月 1–6；停用爱培科工厂导入报 INVALID_FACTORY、确认 400，配置通过正式 API 恢复，正式四表零增量。两轮主库受保护源表指纹不变。首次停用脚本中文 URL 未编码失败，未更改配置；修正后通过。证据 2026-11-03-date-header-125115/result.json 和 2026-11-03-disabled-factory-125416/result.json。两条场景自测置 PASS，20 项仍未关闭。
- F09/F10/I08 自测：隔离唯一文件两类并发确认各只产一个正式资源，重复上传/确认幂等，另一管理员读暂存/错误报告/确认均 403，测试账号删除；预览后重启 8182，暂存明确过期，同字节重传恢复原 task，确认仅新增版本 45/快照 58。Testcontainers 既有事务故障注入验证回滚和重试。证据 2026-11-03-import-concurrency-125823/result.json、2026-11-03-import-restart-130014/result.json。三条自测 PASS，17 条未关闭。
