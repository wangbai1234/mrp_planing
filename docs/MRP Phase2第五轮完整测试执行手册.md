# MRP Phase 2 第五轮完整测试执行手册

> 执行对象：Mimo 或测试人员  
> 要求：逐项执行，不得跳过；失败不得写 PASS；每项必须提交命令、原始响应/日志、关键 SQL 结果和页面截图。

## 1. 证据目录

在 `test-evidence/phase2/round5/` 保存：

```text
00-environment.md
01-backend-build-test.log
02-frontend-build-test.log
03-docker-empty-db.log
04-api-evidence.md
05-daily-conservation.md
06-recalculation.md
07-inventory-allocation.md
08-execution-difference.md
09-ui-evidence.md
10-e2e.log
verification-report.md
screenshots/
sql/
responses/
```

禁止只写“已验证”“符合预期”。报告必须引用实际文件。

## 2. 环境与基础设施

### T01 Docker 连接

执行：

```bash
docker version
docker info
docker compose -f deploy/docker-compose.yml ps
```

通过标准：Client/Server 均可读取；MySQL、Redis 实际运行。Docker CLI 正常但 Testcontainers 失败时，必须保留完整 HTTP/API 错误，不得写“Docker 不可用”。

### T02 后端全量测试

```bash
cd backend
./mvnw compile
./mvnw test
```

通过标准：

```text
Tests run: 207, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

必须额外截取 Testcontainers 启动 MySQL 和 Flyway 完成 29 个迁移的日志。

### T03 前端验证

```bash
cd frontend
npm run build
npm test -- --run
npx vue-tsc --noEmit
```

通过标准：构建、15 项测试、类型检查全部成功。警告与失败分开记录。

## 3. Docker 空库和升级验证

### T04 空库迁移

使用独立临时数据库实例，不删除用户现有数据库。执行应用启动并验证：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

通过标准：V001 至 V029 全部成功；不得修改历史迁移 checksum。

### T05 既有库升级

从符合项目基线的旧版本备份恢复至独立测试实例，然后启动当前应用。

通过标准：Flyway 校验成功；原有 Phase 1 数据可读；新增 Phase 2 表和字段存在；不得用手工 ALTER 冒充迁移成功。

## 4. API 和权限

### T06 健康与登录

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  --data '{"username":"admin","password":"admin123"}'
```

保存完整 HTTP 状态、响应体和后续请求使用的脱敏 token。

### T07 权限反例

分别使用无权限、只读、管理员账号访问批次计算、决策维护、发布接口。

通过标准：无权限返回 403；只读账号不能写；管理员可按状态门禁执行。不得只测试管理员成功路径。

## 5. 日分配和守恒

### T08 日历未覆盖目标日期

准备：来源月需求 500、22 个来源生产日；提前 7 天后其中 5 天落到未覆盖日期。

验证 SQL 至少包括：

```sql
SELECT COUNT(*) row_count, SUM(final_quantity) quantity
FROM mrp_daily_plan
WHERE run_id = ? AND source_month = ?;

SELECT status, COUNT(*) row_count, SUM(quantity) quantity
FROM mrp_unallocated_quantity
WHERE run_id = ? AND source_month = ?
GROUP BY status;
```

必须得到：

```text
有效日计划：17 条，386.363640
PENDING 待分配：5 条，113.636360
合计：500.000000
```

通过标准：守恒接口通过数量守恒，但批次是 `WAITING_DECISION`，发布接口明确被 PENDING 待分配阻断。

### T09 日历覆盖完整目标范围

补齐目标日期为有效生产日后重新生成新 run。

通过标准：22 条日事实、0 条 PENDING 待分配、合计 500；满足其他门禁后才可 READY。

### T10 RESOLVED 不重复计数

将待分配记录经业务动作解决并落实为日计划。

通过标准：守恒查询只统计 `PENDING` 待分配；`RESOLVED` 记录不与已生成日计划重复相加。

### T11 精度与重复键

分别测试：

- `quantityScale=2` 且不能整除；
- 非等权重；
- 同一业务键存在多条终端需求；
- 0、负数、空权重按规则处理。

通过标准：最大余数补差稳定；总和严格守恒；无重复 `rowKey` 或唯一键冲突；所有数量使用 DECIMAL/BigDecimal。

## 6. 重算与版本

### T12 两次成功重算

同一冻结输入连续创建两个计算 run。

通过标准：结果 checksum 一致；历史 run 保留；新的成功 run 才切换 `active_run_id`。

### T13 一次失败重算

先生成成功 active run，再制造确定性失败（例如缺少必要业务决策），执行新 run。

必须查询：

```sql
SELECT id, active_run_id, status FROM mrp_batch WHERE id = ?;
SELECT id, status, error_code, error_message FROM mrp_calculation_run WHERE batch_id = ? ORDER BY id;
```

通过标准：失败 run 有失败状态和原因；旧 active run 不变；失败结果不能 READY、不能发布、不能覆盖历史事实。

### T14 月中重算与锁定日

准备已执行、已锁定、未来未锁定三类日期。

通过标准：已执行/已锁定日不被覆盖；仅未来未锁定日期生成新结果；实际完成量与锁定计划不一致时生成差异异常；业务必须明确选择保留原库存分配或重新分配。

### T15 发布修订

发布后修改应创建修订版本。

通过标准：原发布版本只读；修订版有父版本关系；日计划、分配、决策引用、异常和审计等必要事实完整复制或重新绑定，不能只复制展示表。

## 7. 库存和 BOM

### T16 库存质量字段及模板

下载库存模板并验证良品/不良品字段、列顺序、提示行、示例行、解析器索引、稀疏行、错误报告。

通过标准：库存快照按物料汇总，同时区分质量状态；缺失/非法质量状态进入错误报告；错误原因位于最后一列；有错误时禁止确认导入。

### T17 共享库存业务分配

通过真实页面完成保留原分配与重新分配两种操作。

通过标准：自动值、人工值、最终值分开；总分配不超过可用库存；并发提交只有一个成功或得到明确冲突；有审计记录。

### T18 BOM 有效版本阻断

覆盖：

- 正常最高有效版本；
- 同一库存组织、父料号、子料号出现多个最高版本；
- 有效期重叠；
- 库存组织为空；
- BOM 循环；
- 下层无 BOM 的结构叶子。

通过标准：正常数据固化快照；歧义数据阻断计算并给出可操作提示；计算只读已固化 BOM 快照。

### T19 多层选中分类

准备父节点和子节点分别属于已选分类的 BOM。

通过标准：明确选择多个层级时各层均可成为排产节点；未选择的兄弟分类不自动命中；不存在分类编码返回校验错误。

## 8. 页面与浏览器

### T20 日网格

使用真实 API 数据验证至少 28 天，并扩展验证 84 天窗口。

逐项检查：

- 整机料号/名称列；
- 当前排产物料料号/名称列；
- 每个自然日列；
- 周汇总由日事实聚合；
- 跨月周和一个自然月有 5 个周一；
- 非生产日、锁定日、执行日、人工调整状态；
- 空数据、加载、错误提示；
- 横向滚动和固定列；
- 服务端分页。

每项必须提供截图与对应 API 响应。不得用静态假数据截图。

### T21 外购/委外停止层级

通过业务维护页面设置自制、外购、委外、未维护及 STOP/EXPLODE。

通过标准：外购/委外是否停止由已维护策略决定，系统不猜测；STOP 可停在机头/PCBA；EXPLODE 继续到结构叶子；未维护进入业务决策工作台。

## 9. E2E 强制要求

### T22 清理弱断言

审计：

```bash
rg -n "skip|skipping|console\.log|if \(.*visible|if \(.*enabled" frontend/e2e
```

通过标准：关键业务步骤不存在“按钮不见就打印后通过”“数据缺失就跳过”。前置数据必须由测试创建；无法满足前置条件应明确失败。

### T23 Playwright

运行项目定义的完整 E2E 命令。至少覆盖：登录、批次创建、预检、计算、待决策阻断、决策完成、重新计算、READY、发布、日计划查看、人工调整、锁定/执行、失败重算。

必须保存 trace、失败截图、视频（若配置）和原始日志。

## 10. 最终报告规则

`verification-report.md` 必须包含：

1. 环境版本和实际命令；
2. 每个 T01-T23 的 PASS/FAIL/BLOCKED；
3. HTTP 请求与响应文件；
4. SQL 查询与结果文件；
5. 页面截图路径；
6. 失败的复现步骤、根因、影响范围；
7. 不得把 BLOCKED 写为 PASS；
8. 不得以“代码已实现”替代真实流程证据；
9. 明确保留 `TODO-SHIPMENT`；
10. 最终结论只能是“可签收”“有条件签收”或“不可签收”，并列出依据。

