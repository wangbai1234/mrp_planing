# MRP Phase 2 第五轮代码修复与复验报告

> 复验日期：2026-09-24  
> 依据：`test-evidence/phase2/verification-report.md`、当前真实代码、实际 Docker/MySQL/Redis/API/浏览器运行结果

## 1. 结论

本轮已修复并闭环以下阻断项：

1. Docker Desktop 正常，但 Testcontainers 1.20.4 默认探测 Docker API v1.24；当前 Docker Engine 最低支持 v1.40，导致 `/v1.24/info` 返回 HTTP 400。集成测试基类现显式使用 API v1.40，不升级依赖、不修改技术栈。
2. `integration` 测试上下文缺少飞书配置，容器成功启动后仍会因 `FEISHU_APP_ID`、`FEISHU_APP_SECRET` 未定义而失败；现已注入仅用于测试的占位值。
3. `BomExplosionServiceTest` 与 Flyway 初始化数据冲突：测试重复插入已由 V012 固化的物料分类。现改为复用已有分类，不修改已应用迁移。
4. 4 个 BOM 测试断言要求存在 `MAT-A -> MAT-B/C/D/E`，但原夹具没有创建这些 BOM。现由各测试显式建立所需结构，消除虚假失败。
5. BOM 展开增加目标分类存在性校验；选择多个目标层级时，命中上层是排产节点但不隐式阻止继续下钻，从而能返回所有明确选择的层级。
6. 日排产守恒以“有效日事实 + `PENDING` 待分配量”为完整口径。`RESOLVED` 待分配记录不重复计入守恒。
7. 当提前 7 天后的目标日期无有效生产日历时，数量进入待分配池；不得为了凑足日计划条数自动平移。存在 `PENDING` 待分配时，批次不得进入 READY，也不得发布。

## 2. 对 Mimo 报告的纠正

Mimo 对 500 数量仅生成 17 条日计划的根因判断不正确。真实口径为：

```text
500 = 386.363640（17 条有效日计划）
    + 113.636360（5 条 PENDING 待分配）
```

5 条待分配来自月初生产日整体提前 7 个自然日后落入上月，而对应目标日期缺少有效生产日历。按冻结规则，系统必须进入待分配池，不能自动平移或强补为 22 条日计划。

因此：

- 数量守恒可以通过；
- 业务决策门禁不通过；
- 批次状态应为 `WAITING_DECISION`；
- 发布必须被阻断。

只有补齐有效目标日期的生产日历或由业务完成待分配决策后，才允许重新计算并进入 READY。

## 3. 实际修改文件

- `backend/src/test/java/com/mrp/BaseIntegrationTest.java`
- `backend/src/test/java/com/mrp/bom/BomExplosionServiceTest.java`
- `backend/src/main/java/com/mrp/bom/service/BomExplosionService.java`
- `backend/src/main/java/com/mrp/mrp/engine/DailyDistributionEngine.java`
- `backend/src/main/java/com/mrp/mrp/service/DailyPlanningService.java`
- `backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java`
- `backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java`
- `backend/src/main/java/com/mrp/mrp/repository/MrpUnallocatedQuantityMapper.java`
- `backend/src/main/resources/mapper/mrp/MrpUnallocatedQuantityMapper.xml`
- 对应后端测试文件

未修改任何已应用 Flyway 迁移，也未升级 Java、Spring Boot、Node、Testcontainers 或其他技术栈版本。

## 4. 三层验证证据

### 4.1 编译

```bash
cd backend
./mvnw compile -q
```

结果：PASS。

```bash
cd frontend
npm run build
```

结果：PASS，Vite 成功构建 1707 个模块。存在大 chunk 警告，但不是本轮引入的编译错误。

### 4.2 自动化测试

```bash
cd backend
./mvnw test
```

结果：PASS。

```text
Tests run: 207, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试期间 Testcontainers 已真实完成：

- 连接 Docker Desktop 29.4.3；
- 启动 `testcontainers/ryuk:0.11.0`；
- 启动 `mysql:8.0`；
- 空库执行 29 个 Flyway 迁移；
- `BomExplosionServiceTest` 14 项全部通过。

```bash
cd frontend
npm test -- --run
npx vue-tsc --noEmit
```

结果：2 个测试文件、15 项测试全部通过，类型检查通过。

### 4.3 真实服务与浏览器

```bash
curl -i http://localhost:8080/actuator/health
```

结果：HTTP 200，`{"status":"UP"}`。

```bash
curl -i http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  --data '{"username":"admin","password":"admin123"}'
```

结果：HTTP 200，返回有效 token、ADMIN 角色和权限集合。

浏览器实际访问 `http://localhost:5180/login`，完成登录并进入 `/planning`；侧边栏、排产页面、空数据状态、历史版本表格正常显示，未出现白屏或控制台阻断错误。

## 5. 当前签收边界

本轮代码和基础三层验证已通过，但不能把以下项目凭空写成已验收：

- 真实 Phase 2 全业务数据下的两次成功重算和一次失败重算；
- 失败重算是否完整保留旧 `activeRunId`；
- 28/84 天日网格的完整编辑、锁定和跨月显示；
- 共享库存实际分配操作；
- 发布修订版本对全部事实表的完整复制；
- 大规模数据性能；
- Playwright E2E 中原有条件跳过和弱断言的清理。

以上必须按照配套测试手册逐项执行并留存证据，不能因编译或单元测试通过而自动签收。

## 6. 已知非阻断项

- 前端生产构建提示主 chunk 超过 500 kB，应作为性能优化项登记，不影响本轮正确性验收。
- 测试日志中 Mockito 动态加载 agent 的 JDK 提示属于未来兼容性提醒，本轮测试实际通过。
- 已出货继续保持 `TODO-SHIPMENT`，不得加入当前公式、checksum、预检或发布门禁。

