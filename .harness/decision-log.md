# MRP Harness 决策日志

本文件只记录会影响实现的架构或业务决策。新增记录格式：日期、提出问题、证据、决定、影响、决定人/来源。

## 已冻结决策

2026-10-20 用户新确认：新排产沿 BOM 管理当前最新同步的全部关系逐层下钻，仅物料策略 STOP 终止；NC HBDEFAULT 不控制排产。新批次不再生成或选择 BOM 版本快照；保存当前 BOM 校验码并在预检、计算、发布前核对，变化须重建批次。历史批次和原快照保留只读。此决定取代 2026-09-22 的新批次 BOM 快照规则。

| 日期 | 决策 | 影响 |
|---|---|---|
| 2026-09-03 | 采用 Java 21 + Spring Boot 3.x + MyBatis XML + MySQL + Redis/Redisson + Spring Batch + POI SAX + Spring Security/JWT + JUnit 5 + Docker | 模块化单体，API/Worker 同镜像 |
| 2026-09-03 | 不引入 MQ、对象存储、独立调度中心、搜索引擎和微服务 | 异步任务使用 MySQL 任务表 + Worker 轮询 |
| 2026-09-03 | 排产系统和产能统一按周，不做日能力到周能力换算 | 使用 `weekly_capacity` |
| 2026-09-03 | 真实 Excel 只作为字段和布局参考；经营计划月份读取真实单元格年月 | 支持 2026-12 到 2027-01 跨年 |
| 2026-09-03 | 历史 R013 状态由较新需求裁决为负值归零 | 采用 `MAX(0, ...)` |
| 2026-09-03 | 不创建“滚动一周”按钮和独立系统设置页 | 固定12周横向查看，自动重算开关位于排产页 |
| 2026-09-03 | Maven 3.9+ / Maven Wrapper 纳入后端构建基线 | 所有构建、测试和打包优先使用 `./mvnw` |
| 2026-09-03 | Node.js LTS + npm 纳入前端构建基线 | 通过 `.nvmrc` 或 `package.json` `engines` 固定主版本，CI/Docker 不使用未声明的 `latest` |
| 2026-09-29 | 前端 Node 基线统一为 Node.js 24 LTS | 初始仓库的 Vite 8、TypeScript 6、`@types/node` 24 与 `.nvmrc` Node 20 不一致，且本机 Node 23 为非 LTS；统一 `.nvmrc`、`package.json`、CI/Docker 文档口径为 Node 24，npm 允许 10/11 |
| 2026-09-03 | Nginx 暂不作为初期必选组件，不永久排除 | Spring Boot 直接托管前端静态资源；出现 TLS/路由/负载均衡需求时再评估 |
| 2026-09-03 | 超产能硬阻断发布（Phase 1 历史） | 仅适用于旧周计划；Phase 2 MVP 不把旧周产能纳入日排产发布门禁或 checksum，终端单位/产线映射冻结后再另立决策 |
| 2026-09-03 | CRM 已出货数据 Phase 1 对接真实接口 | 先实现接口抽象层和 Excel 导入通道作为 fallback，真实 CRM 接口后续提供；TODO 记录待对接点 |
| 2026-09-03 | 用户/权限初始化使用 Flyway 种子数据 | 默认 ADMIN 用户和 5 角色（ADMIN/PLANNER/BUSINESS/WAREHOUSE/READONLY）通过种子 SQL 创建 |
| 2026-09-03 | 本地开发环境：Docker Compose 提供 MySQL 8.x + Redis；Java 21 需安装 | 后端编译和运行需要 JDK 21；本地已有 Docker/Node/npm/MySQL 客户端/Redis 客户端 |
| 2026-09-22 | Phase 2 正式纳入：整机经营计划递归展开 BOM，每层扣当前物料库存，只传播净需求 | 新增不可变 BOM 快照、逐层需求明细和末级需求模型；Phase 1 只读兼容 |
| 2026-09-22 | 物料分类角色、供应方式和下钻策略由业务版本化维护，外购/委外不自动等于 STOP | 预检缺失即阻断；系统不得按名称/分类前缀猜测 |
| 2026-09-22 | 缺库存记录不自动视为 0；当时共享库存分配由业务确认 | 缺记录决策仍有效；共享库存人工前置已被 2026-09-29 的经营计划顺序自动分配决策取代 |
| 2026-09-22 | 日排产是唯一事实；月末级需求按生产日历分到日，整体提前 7 个自然日 | 废止 Phase 2 W1-W4 carry；周/月为只读聚合 |
| 2026-09-22 | 提前后落非生产日进入待分配池，不自动移动 | 待分配未归零阻止发布 |
| 2026-09-22 | 排产页固定“来源整机、末级需求物料”两列，只展示最终排产日期 | 末级可为机头、PCBA、原材料或其他 STOP 节点 |
| 2026-09-22 | Phase 2 数量使用 BigDecimal / DECIMAL(18,6)，整数日分配使用最大余数法 | 禁止 longValue 截断；日/月/周守恒 |
| 2026-09-22 | 已出货本期标记 TODO，不参与公式、checksum、预检和发布 | shipment 字段仅预留，不影响本期结果 |
| 2026-09-22 | 新需求使用 10-19 独立阶段，多 Agent 文件所有权+独立 Review+失败修复闭环 | 00-09 历史 DONE 不改变；门禁 PASS 后自动继续 |
| 2026-09-22 | 仓库未发现可调用的 Superpowers 技能/CLI，`.superpowers/sdd` 仅作 SDD 证据目录 | 若运行时仍无真实能力，使用 Harness REVIEW/REPAIR/FINAL-AUDIT 等价闭环，禁止伪造命令 |
| 2026-09-22 | 库存快照不按库存明细行重映射工厂/库存组织；当时按快照头+物料+库存品质汇总 | `inventory_quality` 规则仍有效；快照组织结构已被 2026-09-29 的仓库范围设计细化为“快照+仓库+物料+品质” |
| 2026-09-22 | NC 同步按库存组织+父料号+子料号选择唯一最高有效 BOM 版本 | MRP 只读取固化 BOM 快照；重复最高版本、有效期重叠、库存组织不明确时阻断 |
| 2026-09-22 | MVP 不做计量单位换算，按现有数字和 `childQty/parentQty` 计算 | 不新增隐式换算；单位换算另立后续需求 |
| 2026-09-22 | 已执行/已锁定日不可重算覆盖；月中只重算未来未锁定日；当前执行状态由业务手动确认 | 重算创建新的计算 run；锁定日计划与实际不一致生成差异异常 |
| 2026-09-22 | 发布后采用修订版本，已发布批次不可原地修改 | 修订批次保留原版本和审计，只能调整未来未锁定日 |
| 2026-09-29 | 多整机共享库存按来源月+经营计划行顺序分配 | 经营计划保存 source_row_no/priority_seq；废止 HashMap/料号隐式顺序 |
| 2026-09-29 | 库存覆盖永惠、爱培科、惠州仓，工厂与仓库使用已发布一对一映射 | 快照按仓库+物料+品质汇总；工厂只扣映射仓库，不跨仓合并 |
| 2026-09-29 | 按仓库+物料版本化维护安全库存和预警库存 | 安全库存写入 pool reserved 作硬保底；低于预警/安全线显示风险 |
| 2026-09-29 | 日排产仍是唯一事实，业务按月发布 W1-Wn 日期范围 | 周汇总从日事实按业务周聚合，不再写死周一至周日 |

## 已关闭决策项与剩余阻断

以下决策已由业务确认并可进入实现：Q2-012、Q2-013、Q2-014、Q2-016，以及 Q2-015 的日锁定/重算/差异异常规则。

### DR-006 Q2-015 月中重算后的库存分配（仍阻断）

- **问题**：月中重算导致库存需求变化时，原库存分配是保留，还是全部重新分配。
- **影响阶段**：15、16；影响计算结果和业务责任边界。
- **当前处置**：在业务确认前，系统可以生成分析结果和差异预览，但不得发布受影响的正式计算结果；不得自行选择。

---

## 其他待决定事项

- 部署目标环境的 TLS/反向代理入口（09 阶段前确认）
- Phase 2 产线选择和超限自动平移（保持 TODO）；日产能已由下述 2026-10-14 确认替代

| 2026-10-08 | 菜单与权限页共用一份前端声明 | 侧边栏分组原先在 `App.vue` 手写，权限页按 `permission.parent_id` 建树，两套结构各写各的，且父子链在迁移里按自增 id 硬编码，无法安全重组。改为 `frontend/src/config/menu.ts` 单一声明：侧边栏按分组过滤它，权限页按它的 `code` 前缀分节。权限码与 `parent_id` 不动，只改显示名（V037：`mrp_workspace`→排产计划、`mrp_config`→计算配置）。新增页面只需在该声明加一项，权限页自动出现对应分节 |
| 2026-10-08 | 菜单按数据角色分组，产能配置并入计算配置 | 「计划」放排产计划、经营计划、库存快照；「基础信息」放物料管理、物料分类、BOM管理；「配置」只留计算配置，产能配置降为其中一张卡片；「系统」放用户与权限。取消原「数据」分组 |


## 2026-10-14 用户确认：物料策略缺失与物料选择

用户确认实施方案，并追加库存策略新增弹窗采用相同真实物料分页模糊选择。供应可写值仅自制/外购/委外，下钻可写值仅继续/停止，字段必填。按冻结 BOM 计算时缺下钻策略默认继续到叶子，缺供应不阻断，有效 STOP 保持生效；空策略表允许创建批次。排产完成保存去重缺失清单和完整来源，随 run 冻结及发布复制。历史未维护值兼容读取，结果内部供应哨兵显示未配置，不改写历史快照，不回填旧清单。该确认取代旧物料供应/策略缺失阻断决策；不增加依赖或修改技术栈。

## 2026-10-14 用户确认：生产日历与日产能改版

用户“好的执行”确认设计方案。两厂日产能同单位自制末级数量，退出当前周能力维护，移除新配置权重，以等权初排和最终日期预警人工调整闭环实现。超限不自动平移；异常发布展示明细并要求绑定当前结果的人工确认。V044新增能力单位/revision及物料单位快照，不改历史迁移/加权结果。未新增依赖和中间件，技术栈不变。独立验收报告：`.harness/reports/2026-10-14-daily-capacity-report.md`。

## 2026-10-14 用户更正日产能计量口径（C019）

用户明确生产日历日产能是整机产量台/日，撤回此前自制末级口径。验收发现现能力服务加总SELF_MADE末级，与新需求不符。决策：v3从既有根净需求生成独立整机日事实，日历固定台/正整数，按整机预警及守恒调整；末级与整机视图/汇总隔离，旧v2冻结兼容。沿用sourceDate−7，用户未授权整机产出日期与备料日期分离。不增加中间件或修改版本，不改已应用迁移。

C019实施验收：新建/修订补齐已维护的提前7天覆盖月业务周快照；重叠日期归较早周，后周保留未覆盖尾段，冻结定义不变。330后端/48前端测试、隔离真实API/浏览器/发布修订和主服务读回通过，独立复核PASS，证据见whole-machine-capacity-2026-10-14。

## 2026-10-16 B类缺陷修复：经营计划工厂维度

用户已批准测试发现项进入修复。新增V045将forecast_detail唯一键扩展为版本+工厂+整机+月份，保留全部旧数据，不修改已应用迁移。详情、分页计数及导入重复校验统一按工厂+整机；同SKU跨工厂允许、同厂重复行预览阻断。影响经营计划导入/详情与后续计算输入；不改变库存扣减、排产日期、权限码或发布规则，不增加依赖。先在隔离MySQL验证迁移及双工厂同SKU导入；主库91/run37保持不变。回滚应用前须评估新增双厂行与旧唯一键不兼容，数据恢复使用既有私有备份流程。

## 2026-10-16 用户确认：NC 同步仅保留上海 BOM（C021）

用户确认无锡组织不需要参与，批准在「从NC同步」过滤上海组织，并完成其余已审核缺陷修复。组织限制同时进入 NC 最高版本查询和最终查询；本地同步使用事务，空返回或写入失败保留原数据。新快照只读上海，新批次拒绝混合组织历史快照；不修改净需求公式和历史结果。真实主环境已同步3425父项、145808明细，生成READY快照34（121717边、零重复父子边）。隔离环境此前同步3422父项、145801明细，快照34为121711边；两次NC时间不同、数据库不同，不混用checksum。

本次另修复详情权限缺失、权限目录归组/勾选、密码hash序列化、错误模板/枚举校验、非法请求状态及双厂经营计划维度。定向验证、剩余门禁与保护指纹见 `test-kit/2026-10-16/修复完成报告.md`。不新增依赖或修改技术栈；阶段18/19继续REVIEW。

## 2026-10-16 排产读取优化与新增索引实施记录

用户授权继续修复并排查加载、滚动性能。为整机负荷和单格日期筛选新增V046索引`(run_id,terminal_type,schedule_date,factory_code)`；不修改已应用迁移或业务数据。摘要/整机负荷SQL聚合、筛选下推与前端日期列视窗不改变API结构、计算公式、日期范围、版本或发布规则，不新增缓存中间件及依赖。该B类结构变更的决策记录在交付时补齐，晚于实施，作为流程偏差保留；后续结构变更应先记录再实施。索引已在隔离MySQL及主环境应用，旧应用可继续运行，多一个索引有存储和写入维护成本；本轮无业务数据回滚需求。详见`test-kit/2026-10-16/排产性能与续修报告.md`，独立定向PASS，整体阶段18/19继续REVIEW。

## 2026-10-16 导入任务归属及用户内幂等键

已获用户授权修复权限缺陷。经营计划、结构库存和旧库存导入的预览、错误报告与确认按import_task.created_by校验当前登录人，并校验任务类型和入口前缀；功能权限继续由原安全链执行。文件去重键由全局文件checksum改为“入口+上传人ID+checksum”，防止相同文件跨用户共用暂存任务。旧键不回填，旧任务仍按created_by授权读取；created_by为空拒绝。该B类幂等键调整不改变数据库结构、正式导入数据、任务状态机或排产公式。本轮实施后补记，保留记录顺序偏差；并发确认/幂等状态闭环另列未完成，不能以本修复代替。

## 2026-10-16 未调整排产明细索引分页（实施前记录）

- B类数据库索引变更：新增V047，按run、日期、来源月、工厂、整机、末级、路径、类型、供应和system_quantity建立覆盖索引；不修改旧迁移、不改正式数量或日期。
- 隔离只读交错A/B见details-count-sort-ab.json：COUNT无稳定回退依据；宽投影全局排序仍为主要成本。针对无override批次采用同一REPEATABLE_READ事务内的存在检查与自动事实分页，先按八字段唯一身份取id，再按PK取当前页明细。
- 有任何override即回到现有完整有效投影，不改变虚拟调入、覆盖及六位精度语义。API、计算公式、checksum、发布和历史兼容不变。测试覆盖无调整、插入调整、删回无调整、分页oracle和两个真实大批次响应等价。
- 索引增加存储及写入成本；实际收益须三层验证和EXPLAIN证据确认。整体阶段18/19不据此关闭。

## 2026-10-16 旧排产导出任务授权与用户内去重（实施前记录）

- 已获用户授权继续修复权限缺陷。旧Phase1导出创建校验当前actor与版本/请求工厂；详情与下载按created_by及business_scope重新授权，历史无owner或无范围任务拒绝，保留不存在任务的400兼容行为。Phase2当前直接导出入口不改。
- B类兼容/去重键变更：POST /plans/{id}/exports使用schedule:export（原误用schedule:edit）；用户、规范化工厂、版本、字段顺序及两个选项全部纳入长度编码SHA256键，旧任务不回填但owner仍可读取。新文件使用UUID防止并发覆盖。
- 不改数据库结构/技术栈/计算公式/排产事实；旧Phase1同步导出状态机不在本补丁中重构。暂无owner共享授权，管理员也须是创建人才能下载任务文件。完整导出性能/并发与工厂数据域总门禁另验。

## 2026-10-16 当前工厂映射/生产日历HTTP工厂域（实施前记录）
- 真实验收ConfigMasterController/Service/Mapper：当前映射列表和日历接口没有工厂域授权。沿用既有user_factory_scope、ACTIVE角色FACTORY/ALL机制，在HTTP入口增加数据域服务；后台冻结配置及领域校验不依赖HTTP身份。
- B类兼容收紧：factories及当前映射列表仅返回有权工厂；日历显式工厂读取/导出/写入需授权，省略工厂的旧列表按有权工厂查询。按id更新/删除使用数据库原记录归属，不信任请求体；复制日历需同时授权来源和目标；两种导入在任何写入前检查文件全部工厂，越权整体403。
- 模板、纯文件解析预览/错误报告不读取服务器的工厂配置，保持原行为；提交仍独立授权。权限码保持现有，缺失资源原错误语义保留。无表结构/公式/技术栈变更，不改大数据事实。
- 限定当前主数据入口；旧版本配置接口、库存策略及全局物料策略/业务周的完整工厂域仍单独验收，CAP-1014-03整体不关闭。

## 2026-10-16 审计列表工厂域（实施前记录）

- 真实代码：AuditLogController仅参数化筛选，未检查工厂域，COUNT和items均全库；ConfigMasterService映射/日历行日志factory_scope为空，resource_id仍保留工厂业务键；删除后不能依赖当前表join恢复权限。
- B类API收紧，不改公式/数据：审计读改为MyBatis XML共用SQL权限谓词，并在只读RR事务执行COUNT/page；沿用user_factory_scope和ACTIVE角色FACTORY/ALL。ALL域账号保持完整可见；单厂/无工厂用户只见有权单厂日志和明确全局日志（USER/ROLE/PERMISSION/material_policy/business_week且无factory_scope）。未知或多厂ALL日志、无归属库存策略、旧IMPORT汇总对受限用户拒绝返回，保留给ALL域用户追溯；不能将null当全局开放。
- 历史映射/日历仅在scope缺失时按固定resource_id恢复工厂，IMPORT不猜测归属；不改写历史。新映射/日历行日志写入工厂scope，新导入汇总单厂写单厂，多厂写ALL。不依赖HTTP身份推测后台scope。
- 修改文件：AuditLogController、AuditLogMapper接口/XML、ConfigMasterService，新增AuditLogFactoryScopeIntegrationTest、隔离HTTP及浏览器测试工具/限定报告。无前端业务/迁移/依赖变更。
- 验证：保留before HTTP越权200返回证据；compile → 定向与限定回归 → 重启隔离与主服务 → HTTP及浏览器 → 主91/run37/快照33等SHA保护 → 原Reviewer独立复核。库存策略HTTP/旧版本配置域、历史未知审计归属、跨资源并发撤权仍另行验收；CAP-1014-03整体OPEN，阶段18/19 REVIEW。

## 2026-10-16 无覆盖整机产能确认读取优化（实施前记录）

A类性能修复，无新依赖/迁移/API变更。验收链路确认capacityReview全量SELECT *、SQL排序、日事实对象列表与checksum字符串列表同时存活；仅无override且v3/v4整机口径可省略有效投影。

实施范围：新增AutomaticCapacityReviewService、MrpDailyPlanDetailMapper Java/XML只读流式11字段投影，复用原长度编码/Java字典序/UTF8 checksum与DailyCapacityService；一次流读取保留checksum字符串、日期范围和工厂每日整机汇总，不保留全部日事实对象。BatchOrchestratorService的公开capacityReview使用RR事务覆盖批次/run/override判断/读事实；有override或旧末级口径仍原链路，发布仍原链路。不得用SQL排序替代Java排序，零数量、重复事实、空字段、Unicode及小数scale语义保持。

验证：新增真实MySQL投影兼容/空/大集合/override回退/历史回退/指纹发布兼容及事务快照测试；compile→专项→限定扩大回归→双环境重启→各3次大批只读HTTP逐字段比较和浏览器→主保护SHA→原Reviewer。主91/run37、隔离10/13不得调整、重算、取消或发布。测量RSS/CPU为进程观察，不能当作独占请求或峰值堆结论。性能仍须真实证据，整体TESTKIT-PERF-01不关闭。

### 2026-10-16 当前库存策略HTTP工厂域（实施前记录）
- 真实链路：ConfigMasterController库存策略list/page/warehouses及CRUD/import直接调用无scope的ConfigMasterService，SQL在分页前未过滤，写入可用伪造请求体掩盖原记录仓库。已审核CAP-1014-03子问题，按现有授权实施最小权限修复。
- 新StockPolicyAccessMapper/Service仅处理当前HTTP入口：读取/COUNT共用SQL工厂范围，仓库经当前映射恢复归属；ACTIVE角色ALL可见未知仓策略，两厂FACTORY授权不等同ALL；有归属但停用的策略仍按归属可读写，下拉/新增仍要求启用。
- CRUD按数据库原记录仓库授权，导入先授权全部目标仓库再调用原事务写入；RR事务覆盖授权与操作。业务公式、安全库存/预警数量及后台快照方法不变。请求内快照一致，不承诺并发撤权立即中断已开始事务。
- 新策略审计写精确工厂scope，单厂导入摘要写单厂、多厂摘要ALL；未知归属日志仅ALL可见，不回填历史。
- 文件：新增Mapper Java/XML、HTTP access service及真实MySQL集成测试；修改Controller接入和ConfigMasterService审计scope；新增隔离HTTP/browser工具及证据。无迁移/依赖/前端业务改动。不测试或改写物料/分类/BOM页面与主库91/run37，旧版本配置域单独继续。
- 验证：修复前隔离真实HTTP泄露证据；compile → 专项 → 限定扩大回归 → 重启双环境 → 隔离HTTP/浏览器与主admin只读浏览器 → 保护SHA → 原Reviewer独立复核。

## 2026-10-16 历史生产日历版本HTTP工厂域（实施前记录）

- 已验收MrpConfigController→MrpConfigurationService→ProductionCalendarVersion/DayMapper和ConfigSnapshotService。版本header保存单厂或ALL，ALL快照的日明细保存真实两厂。当前list/get/days/save/publish/delete及batch-creation-versions未检查工厂域。
- B类API兼容收紧：新增HTTP LegacyCalendarAccessService，现有后台/快照服务方法不变；版本列表在SQL过滤，详情和整版本写必须覆盖header和所有既有day工厂，绝不只返回/替换有权子集。已删除版本维持不可读取。
- concrete header需要其工厂授权并覆盖所有实际day工厂；ALL header只在实际明细非空且全部明确工厂有权时允许FACTORY域读取。空ALL、未知header/day只允许ACTIVE角色ALL数据域；两厂FACTORY不自动等于ALL角色域。
- 单厂草稿save仍按原服务将传入factory覆盖为原header，伪造payload不能改变归属。header为ALL的创建及写入仅ALL域，避免现有旧save将day强制设成ALL的行为被受限用户调用；不重设计旧版本编辑规则。
- 列表、详情、授权与操作使用RR事务同请求快照，不能宣称并发撤权中断已开始事务。batch-creation-versions仅替换生产日历版本为有权列表，全局分类/物料策略保持共享。
- 修改文件：ProductionCalendarVersionMapper.java/XML、新增LegacyCalendarAccessService、MrpConfigController、真实MySQL集成测试与隔离HTTP/浏览器测试工具。无迁移/新权限码/前端业务/计算/冻结快照改写。既有列表数组响应保留，不把分页兼容重设计混入权限补丁。
- 验证：compile→专项→限定扩大回归→双环境重启/HTTP/浏览器→主保护SHA→原Reviewer。主91/run37/224100事实/0override/快照33保留；写验证只使用Testcontainers和隔离临时版本，物料/分类/BOM页面不测试。

## 2026-10-16 库存策略保底文案纠错（A类）
- 验收真实链路：StockPolicyConfig.vue:92文案与C016/C020、原型config-stock/allocation不符；InventoryNormalizationService.buildCalculableInventoryMap按仓库先聚合GOOD，再max(0,库存-安全库存)，createPoolsFromSnapshot冻结reservedQty，previewAllocation再次执行硬保底。
- 仅修改StockPolicyConfig.vue辅助说明为“安全库存作为保底，不参与库存扣减；预警库存仅用于风险提示”。不修改导入、库存公式、数量、布局、权限和冻结版本。
- 验证：npm run build → npm test → vue-tsc；真实双环境只读API与浏览器，主保护SHA；独立Reviewer。低影响文案不新增镜像单测。阶段18/19和总缺陷保持开放。

## 2026-10-16 库存复合键分隔符纠错（A类）
真实链路：normalizeSnapshot以仓库|工厂|料号|品质拼接并split，buildCalculableInventoryMap以仓库|料号拼接/split，createPoolsFromSnapshot冻结策略也以同样拼接索引。4新增单测在修复前3FAIL/1ERROR；隔离真实HTTP重现仓库/料号/品质错位，证据inventory-key-before-retry-runtime.json。第一次脚本缺inventoryOrg造成400，失败证据保留。
将修改InventoryNormalizationService.java，将内部键改为record（WarehouseMaterialKey、BalanceKey），不改对外material|GOOD协议、公式/冻结输入/规则版本/数据库迁移。补充InventoryNormalizationServiceTest、真实MySQL集成与隔离运行工具。验证compile→专项/限定回归→隔离重启HTTP/browser→主重启只读与SHA→原Reviewer。主91/run37及隔离10/13绝不重算、发布、修改。物料/分类/BOM管理不测试。

## 2026-10-17 Worker心跳调度隔离（实施前记录）
真实链路：API/Worker均EnableScheduling；MrpTaskWorkerService.pollAndExecute在默认调度线程同步执行整次计算，heartbeat每30秒共用同一单线程。真实MySQL+实际Scheduled失败复现已保留worker-heartbeat-before-test.log：计算阻塞38秒，租约未增长。
A类缺陷修复，运行调度决定单独记录：显式taskScheduler使用现有Boot ThreadPoolTaskSchedulerBuilder保留原默认调度配置；mrpHeartbeatScheduler独立单线程，heartbeat通过scheduler限定符指定。必须同时定义默认scheduler，避免只增加心跳bean导致Boot自动配置退让后所有任务仍共用心跳线程。默认认领/清理保持原调度线程，不改变计算并发度、轮询/续租间隔、五分钟租约或状态机。
不做简单过期requeue：完整恢复需要完整claim fencing、候选run隔离及重试设计，WORKER-LEASE-RECOVERY-01保持OPEN。无新依赖/迁移/权限/API/UI/公式/冻结输入变更。仅修改SchedulingConfig.java、MrpTaskWorkerService心跳注解，新增真实调度MySQL专项及隔离运行证据。
验证compile→专项（长计算中仅owner RUNNING续租、终态不续租、一次执行）→用户允许范围限定回归→双后端真重启→真实隔离行锁前后续租与HTTP→双浏览器/主数据保护→原Reviewer。主91/run37/224100事实/0override/快照33、隔离10/13只读；物料/分类/BOM管理不测试。

## 2026-10-17 Worker失败清理批次状态保护（实施前记录）
真实链路已验收：executeCalculation拒绝非CALCULATING且未生成run即抛；失败任务CAS成功后cleanup在runId缺失/等于activeRun/无activeRun三个分支无条件updateStatusForce READY_TO_CALCULATE（version+1）。隔离worker-batch-state-before-runtime.json/log已复现PUBLISHED无runId、READY带activeRunId被降级；仅新35/36及新任务，六表清理及10/13保护通过。
A类最小缺陷修复：新增MrpCalcBatchMapper.resetAfterCalculationFailure显式SQL，匹配id、当前读取version、null-safe active_run_id，且仅CALCULATING/WAITING_DAILY_DISTRIBUTION/WRITING/FAILED允许恢复READY_TO_CALCULATE。前三者是计算过程状态，FAILED沿用原失败可重试语义；WAITING_DECISION/READY/PUBLISHED/STALE/CANCELLED/SUPERSEDED及其他状态禁止覆盖。三个分支统一调用保护方法，日志根据实际更新行数记录。候选run删除原逻辑保留，完整删除/activation竞态及claim成功/进度fencing另项OPEN，不把批次CAS误报为完整并发修复。
文件：MrpTaskWorkerService.java、MrpCalcBatchMapper.java/XML、MrpTaskWorkerServiceTest、真实MySQL Worker批次状态集成测试及隔离运行工具。无新依赖/迁移/API/UI/公式或冻结结果变更；先完成心跳独立Review再修改同一worker文件。编译→专项/限定回归→双后端真重启→真实隔离失败任务状态前后及合法失败恢复→双浏览器/主保护→原Reviewer。主91/run37/224100事实/0override/快照33、隔离10/13只读；物料/分类/BOM页面不测试。

## 2026-10-17 经营计划/库存确认幂等与提交后缓存清理（实施前记录）
验收实际代码：ForecastImportService.confirm、InventoryImportService.confirm/confirmItem先读取staging后写正式版本，最后普通select任务再updateResult且忽略行数，没有确认状态/行锁。成功后同文件upload重填cache可再生成版本；并发确认可能双写；cache.remove在事务提交前发生，外层回滚后预览不可重试。ImportTaskAccess owner/type/prefix校验及Controller权限必须保留。测试初始ITEMS fixture缺非空分类/供应商，失败日志保留，不算该流有效业务复现；修正后重新取before证据。
A类缺陷修复；B类缓存时点决定记录在此：确认先SELECT FOR UPDATE锁同一任务并验证owner/type/prefix及PENDING/SUCCEEDED。SUCCEEDED即使无cache也返回原资源，缺失/错误归属资源409；仅PENDING生成正式版本。新增completePending SQL同时匹配version/PENDING，检查影响行数，失败抛409回滚。事务afterCommit才条件移除对应staging值，rollback保留预览。既有updateResult方法保留兼容，不改任务状态机/权限码/API形状/数量公式/模板/解析器/迁移/依赖。
文件：ImportTaskMapper.java/XML、ImportTaskAccess.java、ForecastImportService.java、InventoryImportService.java；ImportConfirmationIntegrationTest（真实MySQL参数化覆盖三入口重复确认、并发、回滚、失败终态、丢失资源及写CAS失败），运行工具及报告。当前只解决同一任务确认幂等；跨任务/跨用户相同文件、不同任务forecast versionNo并发、upload的连接级LAST_INSERT_ID、进程重启staging恢复另OPEN。
验证compile→确认/归属/解析兼容专项→用户允许范围限定回归→双后端真重启health→隔离有效Excel真实HTTP重复/并发/重传/越权/终态及临时数据精确清理→双浏览器/最终主保护→原Reviewer。主91/run37/224100事实/0override/快照33、隔离10/13只读，物料/分类/BOM页面不测试。

## 2026-10-17 经营计划/库存上传任务ID与并发首次上传（实施前）
真实代码：ForecastImportService.upload、InventoryImportService.upload/uploadItem无事务，insert(import_task)与selectLastInsertId分为两个MyBatis调用，连接池可返回不同连接；LAST_INSERT_ID是连接局部值，响应/staging可能绑定无关任务。三入口先查requestKey再插入，两个首次上传同时查无记录后唯一键冲突未恢复，正常重复操作会失败。V001已有uk_it_request_key，保留现有用户+入口+checksum范围。
A类最小修复：新增ImportTaskCreation.createOrGet，插入后按唯一requestKey读取真实持久任务，完全不依赖连接局部ID；仅捕获DuplicateKeyException后按同key读取已提交胜出任务，校验owner/type/checksum/key一致，不匹配或缺失不吞异常/不改既有行。替换三个upload中insert+LAST_INSERT_ID+手工record重建，不改confirm（已独立PASS）、模板、解析器、字段、事务/状态机、迁移或依赖。Material导入不在本次范围。
测试新增ImportUploadIdentityIntegrationTest：真实MySQL LAST_INSERT_ID外来连接值故障注入，及barrier强制同文件两个首次查询同时无记录，三入口各覆盖响应/staging/owner与唯一行。独立文件名隔离文件写入，只验证数据库任务身份；相同路径并发文件写入、大文件/跨用户同文件及staging持久化仍OPEN。编译→新专项/原确认归属回归→限定回归→双重启health→有效Excel真实并发首次上传与确认/清理→双浏览器/最终主保护→原Reviewer。主91/run37/224100/0override/快照33和隔离10/13只读。

## 2026-10-17 经营计划物料规格复制字段容量（实施前B类记录）
真实大导入baseline：10000行forecast预览202/10000成功/0错误，但confirm500，后端DataTooLong平台列row211；事务回滚，临时正式行清理及隔离10/13与13事实SHA不变。真实链路ForecastExcelParser将material.specModel赋ForecastDetail.platform；V002源spec_model VARCHAR(256)，V001目标platform VARCHAR(128)。现有准确物料中规格最大215字符，故不是无效夹具或应裁剪的数据。material_name源256与forecast255另潜在边界保持OPEN，不混入本次实际根因。
B类非破坏性字段扩容：仅新增V048__forecast_platform_capacity.sql，将forecast_detail.platform从VARCHAR(128)扩至VARCHAR(256)，NULL属性和既有值保持；不修改任何已应用迁移，不修改material数据/表或BOM数据/公式/输入版本。沿用原规格到平台映射，不隐式截断，不改变API形状、导入模板或解析器。
文件：新迁移、ForecastMetadataCapacityIntegrationTest（本地mock只读物料来源元数据，真实Excel解析及真实MySQL确认，129/256多字节含补充字符边界）、大导入工具/报告。先保存before测试失败，再compile→专项/限定回归→双后端真实重启应用V048，验证47旧checksums与保护事实→真实大导入原规模重验、分组数量/内存/清理→双浏览器/最终主保护→原Reviewer。主91/run37/224100/0override/快照33及隔离10/13不重算/改写。
## 2026-10-19 发布与修订异步 API 返回契约
真实链路原同步请求持有发布完整事实复制和修订库存池重建。按项目“长任务不在 HTTP 等待”原则，复用 `calc_task` 与独立轮询 Worker，POST 返回 202/任务进度，GET lifecycle-task 查询状态与结果版本；不引入新中间件、迁移或权限码。同批次提交复用已有任务，发布/修订结果和 SUCCEEDED 同一事务提交。前端从服务端恢复加载态，状态未知时禁止再提交。此项为 B 类 API 契约变更，详见 `.harness/reports/2026-10-19-planning-lifecycle-background-report.md`。


## 2026-10-19 业务周均分与整体前滚确认（C022）

用户确认：月净需求按来源月实际冻结业务周数 N 均分，余数全部放第一份；第1份安排上月最后业务周，第2至N份安排本月第1至N-1业务周，本月最后业务周承接下月第1份。周内仍按本厂生产日均分，日余数从早到晚补齐，无额外减7自然日。

实现采用新规则 `v5-shifted-business-weeks`，新建批次使用新规则；v4及更早版本继续按原冻结规则重放，修订沿用原规则，避免更改已执行/锁定量的历史语义。数量全程 BigDecimal、来源月追溯及整机/末级分别守恒。无业务周、日历不全或整周停产的数量进入待分配池，不丢弃、不自动改投其他周。

## 2026-10-22 排产日历完整性与异常发布门禁（C024）

验收实际链路：生命周期任务提交仅校验状态；发布执行原来只检查OPEN问题、PENDING待分配和守恒/产能。物料策略默认下钻待确认、库存风险和无事实日期的日历缺失未进入统一门禁，库存风险也未随发布复制。旧transition接口可直接写PUBLISHED。

新增PlanningCalendarValidationService/PlanningReleaseValidationService；按批次工厂、正数来源月及v4/v5目标周或旧规则日期校验冻结日历，修订前检查当前日历。UI新增日历检查菜单、缺失警告及发布禁用；请求失败时禁止发布。前台可警告后生成修订草稿，发布任务提交/执行双检；直接transition到PUBLISHED拒绝。复制库存风险保留数量及resolved元数据。无迁移/依赖/权限码/版本配置变更。详见 `.harness/reports/2026-10-22-planning-release-gates.md`；状态保持REVIEW待独立复核。

## 2026-10-29 整机排产与物料缺料分岗闭环（C025，用户确认）

用户已认可 `docs/MRP整机排产与物料缺料拆分产品设计方案.md`。排产计划页只维护整机 MACHINE_OUTPUT；新增独立物料缺料页，负责末级净需求、库存风险、共享库存分配、异常决策与备料复核。两页共享 batchId、activeRunId、冻结输入与正式发布版本。目标流程为整机候选排产与确认，之后物料展开、处理及确认，异常回传排产员，调整后物控确认失效，双方确认且原有门禁通过后联合发布。整机库存净算一次，物料阶段从同一根净需求与排产 revision 展开。现有 v5 日期/数量公式、已发布历史、锁定日、库存保底和原子联动守恒不变。当前“缺料”口径是逐层净需求与库存风险，不含采购在途/到货覆盖；供给覆盖另行立项。整机候选结果可以先于下层缺库存决策生成，但最终发布不能绕过物料门禁。原单一顶层入口及两类数据混排的原型约定由本裁决取代。

## 2026-11-02 缺料只读列表历史聚合口径（B 类）

用户认可双工作台产品评审并授权执行。`GET /batches/{id}/shortage/page` 对所有有末级结果的批次，默认按 `run+工厂+物料+终点类型+供应方式` 服务端聚合与分页；`detail=true` 或指定来源整机仍返回原来源明细。历史批次的冻结事实、计算和发布均不改写。历史版库存池/风险字段可能缺少可复核依据，前端明确标为未评估，不把零计数称为无风险。来源月、整机或关键词筛选后的净需求是筛选范围贡献；库存池和风险仍为整批口径。该项变更默认只读列表响应的行粒度，调用方与导出后续批次需按新口径核对。

## 2026-11-02 排产与缺料页面严格分岗（C026，用户确认）

用户要求移除排产页双岗状态、生产日历检查和物料跳转，版本信息仅展示版本；缺料页独立说明能否计算及原因，不让物控查看排产网格。C025 中“两页互跳”和旧精简方案中的页内日历检查入口由本裁决覆盖。整机确认仍是与有效结果指纹绑定的复核记录，批次 `SCHEDULE_READY` 无须改状态；前端组合显示“整机已确认”。生产日历完整性在预检前阻断，冻结输入缺项需维护后新建批次或修订。生产执行独立入口保留锁日和实际完成量能力；最终发布的双岗确认与异常门禁继续执行。详见 `docs/MRP排产与缺料分岗页面裁决-2026-11-02.md`。

## 2026-11-02 排产员与物控员默认工厂域（用户确认）

PLANNER 和 MATERIAL 两个角色默认可看全部工厂。V064 仅当角色尚无数据域记录时写入 `role_data_scope=ALL`；已有单厂等自定义角色域保留，页面和动作权限继续独立。登录后的默认页面取首个获授权页面，物控直接进入缺料工作台。该授权扩大两角色在其已有功能权限内的数据可见范围，不授予对方页面权限。

## 2026-11-05 V001 初始管理员安全引导（B 类安全兼容决定）

V001 已应用，不能改动。API 和 Worker 在启动时仅对 `admin` 且密码哈希精确等于 V001 播种哈希的记录要求 `MRP_BOOTSTRAP_ADMIN_PASSWORD`，长度至少 24 位并含字母和数字；缺失则启动失败。启动引导通过带旧哈希条件的 SQL 原子替换为 BCrypt 哈希并标记首次改密。登录和 JWT 认证即使在引导前读到旧哈希，也拒绝该账户。已轮换的管理员哈希不变，引导环境变量不再需要。该安全修复改变固定默认口令的旧登录行为，不改变其他账号及 API 契约；正式环境部署前须由授权运维准备引导口令并核对已有管理员轮换状态，本轮不写正式库/Redis/卷或 5180。

隔离项目 `mrp-acceptance-20261104` 以随机引导口令替换旧哈希并完成首次改密，随后重建 API/Worker 清除引导环境变量；验收口令仅保存在本机权限 600 的 `/tmp/mrp-acceptance-admin-final-20261105.secret`，报告不记明文。V001-V064 checksum 与发布版 #2/run #2 不变。独立测试使用 Testcontainers 动态属性和明确测试口令，旧本地库依赖的 MaterialApiTest 改为隔离夹具。P2-I002 总项在正式现有凭据轮换、完整秘密扫描和独立复核前保持 FAIL。

## 2026-11-05 小团队角色配置与验收聚焦（B 类）

用户明确实际用户不超过 10 人，验收以数据一致性、计算逐键正确及每个角色的权限清晰配置为重点。原始 119 项 UAT 和阶段门禁不自动删除或批量改 PASS；重复的大并发、冷暖耗时及后台列表细节可合并取证或提交明确豁免，计算数据规模、数据守恒、权限与工厂边界不因用户少而缩减。阶段 18/19 保持 REVIEW。

角色配置页沿用既有功能权限树，增加角色 `ALL`、指定工厂或无工厂范围，前端一次提交、后端单事务更新权限和角色数据域，拒绝无效工厂和移除系统管理员全部授权。新增角色配置/工厂范围读取 API 只向具备角色配置或查看权限的用户开放；旧角色权限 API 保留。停用角色从有效权限 SQL 中排除，并清理关联用户缓存；前端刷新时从受保护的 `/auth/me` 重新读取权限，避免本地缓存显示旧授权。复制角色同步复制其工厂范围。无迁移和新依赖；发布、计算、库存与历史事实不变。完整角色接口矩阵及独立安全复核仍须验收。

2026-11-05 权限撤销缓存时序补充：`UserService.getEffectivePermissions` 在角色配置事务提交前可能回填旧权限到 Redis，导致已提交的撤权短暂失效。鉴于用户少于 10 人，鉴权每次从 MySQL 读取有效角色权限和用户直接授权；旧缓存写入/读取不再参与鉴权，避免 Redis 与事务提交竞态。角色变更清缓存调用保留兼容，不改变权限码、工厂域 SQL、迁移或技术栈。100 套件/685 项全量测试、隔离 API/Chrome 已验证；完整接口矩阵仍待验。
