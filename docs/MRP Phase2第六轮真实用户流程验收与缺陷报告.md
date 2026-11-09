# MRP Phase 2 第六轮真实用户流程验收与缺陷报告

## 状态

REVIEW

本轮不判定 Phase 2 最终验收通过。已修复用户现场发现的批次详情接口冲突和批次工作台主要可用性问题，但真实代码审计仍发现若干 P0/P1 业务闭环缺口，必须继续修复后再做全流程 UAT。

## 本次问题根因

### 1. 批次详情接口必现 500

`MrpBatchController` 与 `MrpBatchLifecycleController` 同时声明：

```text
GET /api/v1/mrp/batches/{id}/issues
```

Spring 无法裁决处理器，运行时抛出 `Ambiguous handler methods mapped`。本轮删除重复入口，只保留支持 `status` 筛选的生命周期接口。

### 2. 页面直接展示英文状态且没有流程指引

批次列表、工作台、决策中心和版本历史直接显示 `DRAFT`、`WAITING_DECISION`、`OPEN` 等内部枚举；工作台对所有状态固定展示“重算”，但后端状态机要求：

```text
创建批次 -> 预检分析 -> 业务确认 -> MRP 计算 -> 复核发布
```

这导致业务用户看不懂当前阶段，也无法判断下一步操作。

### 3. 前端计算请求与后端接口已经漂移

前端仍向计算接口传 `bomSnapshotId`、`inventorySnapshotId` 和空 `rootDemands`，后端真实接口只接收批次号并从批次锁定版本读取输入。已修正为 `POST /mrp/batches/{id}/calculate`。

## 本次完成

- 删除重复的 `GET /mrp/batches/{id}/issues` 映射；
- 批次 68 的问题接口由 500 恢复为 HTTP 200；
- 增加统一中文状态映射、状态说明和决策问题中文映射；
- 批次列表状态筛选、状态标签、问题列和下一步操作全部中文化；
- 批次工作台增加五阶段步骤条、当前状态说明、唯一主操作和门禁指标；
- 草稿显示“开始预检”，待决策显示“处理待办决策”，待计算显示“开始 MRP 计算”，待发布显示“发布排产结果”，已发布显示“生成修订版”；
- 无日排产事实时禁用日/周/月/执行标签，避免进入空白功能；
- 决策中心显示业务问题名称、涉及对象、中文严重程度和中文状态；
- 决策详情由无效消息提示改为真实抽屉；
- `/mrp/batches/{id}` 旧详情路径统一重定向到新工作台，避免两套详情页面继续分叉；
- 新增中文状态映射单元测试。

## 修改文件

- `backend/src/main/java/com/mrp/mrp/controller/MrpBatchController.java`
- `frontend/src/api/mrp.ts`
- `frontend/src/router/index.ts`
- `frontend/src/utils/mrpDisplay.ts`
- `frontend/src/utils/__tests__/mrpDisplay.test.ts`
- `frontend/src/views/mrp/MrpBatchList.vue`
- `frontend/src/views/mrp/MrpWorkspace.vue`
- `frontend/src/views/mrp/components/DecisionCenter.vue`
- `frontend/src/views/mrp/components/ExecutionConfirm.vue`
- `frontend/src/views/mrp/components/RevisionHistory.vue`

## 验证证据

### 后端编译与全量测试

```text
cd backend
./mvnw compile
./mvnw test
```

结果：207 项测试通过，0 failures，0 errors，0 skipped，BUILD SUCCESS。测试期间 Testcontainers 真实连接 Docker Desktop 29.4.3，并从空库执行 V001-V029。

### 前端构建、测试和类型检查

```text
cd frontend
npm run build
npm test -- --run
./node_modules/.bin/vue-tsc --noEmit
```

结果：构建通过；3 个测试文件、17 项测试通过；类型检查通过。主 chunk 大于 500 kB 仍为非阻断性能项。

### 真实 API

```text
GET /actuator/health                         -> 200, UP
POST /api/v1/auth/login                      -> 200
GET /api/v1/mrp/batches/68                   -> 200, DRAFT
GET /api/v1/mrp/batches/68/issues            -> 200, []
GET /api/v1/mrp/batches/68/summary           -> 200
```

### 真实浏览器

使用本机 Google Chrome + Playwright：

- admin 登录成功；
- `/mrp/batches/68` 正确重定向至 `/mrp/workspace/68`；
- 页面显示“待预检”而不是 `DRAFT`；
- 显示五阶段操作步骤和“开始预检”主操作；
- 点击“待办决策”成功加载，无 `/issues` 500；
- 控制台错误、页面错误、HTTP 4xx/5xx 均为 0；
- 1440x900 下四个指标卡横向布局正常。

## 尚未达到最初项目需求的缺陷

### P0：必须在最终验收前修复

1. **库存组织口径仍违背业务确认规则**  
   `BatchOrchestratorService` 和 `MrpCalculationService` 仍使用 `inventoryOrg = factoryCode`，而业务已确认库存快照按物料汇总，不应按明细行把工厂映射为库存组织。

2. **失败重算仍存在 active run 污染风险**  
   `MrpCalculationService` 在计算过程内提前切换 `activeRunId`；失败清理对“失败 run 等于 active run”的场景保留数据，只重置状态，不能满足“失败不影响上一次成功运行”的业务不变量。

3. **终端需求仍有批次维度跨 run 聚合风险**  
   `getTerminalDemandsAggregated()` 调用 `selectAggregatedByBatchId(batchId)`，历史 run 可能被混入当前 active run。

4. **发布版本事实复制不完整**  
   发布目前主要复制日计划，未证明库存分配、业务决定、异常、执行确认、人工覆盖和审计事实都与发布修订版本完整绑定。

5. **决策动作尚未按问题类型实现**  
   前端当前仍用通用 `MANUAL_CONFIRM` 和文本原因处理所有问题，没有为“缺库存按 0 / 切换快照 / 维护分类角色 / STOP 或 EXPLODE / 外购委外 / 共享库存分配”等问题提供结构化候选动作，无法驱动确定性重分析。

### P1：核心业务页面未闭环

1. **日排产单元格仍不可编辑**  
   点击单元格只显示提示消息，未打开调整抽屉，也没有完成人工值、系统值、最终值、原因、锁定校验和恢复自动值闭环。

2. **日排产页面未使用服务端日网格接口**  
   后端已有包含分页、日历列和自然周分组的 grid 接口，但前端仍拉取明细后在浏览器分组并伪分页；28/84 天、自然周表头和大数据性能尚未达到约定。

3. **前端数量仍使用 JavaScript number**  
   库存分配、日/周/月汇总和执行确认使用 `number`、`Number()` 和浮点相加，存在 DECIMAL(18,6) 精度损失风险。

4. **E2E 存在条件跳过与弱断言**  
   `business-flow.spec.ts`、`phase2-mrp.spec.ts` 大量使用“按钮不可见/无数据就跳过并通过”，不能作为完整业务验收证据。

5. **库存分配页面仍展示库存组织**  
   页面与 API 类型仍保留 `inventoryOrg` 列，与“库存快照按物料汇总、区分良品/不良品”的最新业务规则不一致。

### P2：可用性和维护性

- 前端仍存在未再使用的旧 `MrpBatchDetail.vue` 文件，路由已隔离但应在确认无外部引用后清理；
- 批次创建弹窗的版本选项只有内部编号，后续应补充版本日期、数据范围、发布人等业务识别信息；
- 版本历史当前按全量批次前端过滤，需改为后端按修订链分页查询；
- 前端主 bundle 约 875 kB，需按页面拆包优化。

## 验收结论

本轮修复后，用户指出的“详情报错、英文状态、看不懂下一步”已闭环，并有真实 API 与浏览器证据。但 Phase 2 整体仍不满足最终 DONE 定义，当前应保持 REVIEW。下一轮建议严格按 P0 顺序修复：

1. active run 事务切换与失败重算隔离；
2. 库存按物料/质量汇总口径；
3. 所有结果查询绑定 active run；
4. 发布修订版完整事实复制；
5. 结构化业务决策及受影响波次重分析；
6. 日排产 grid/编辑闭环和强 E2E。
