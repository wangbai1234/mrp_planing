# 任务报告：业务日志呈现与测试环境误报修正

## 状态
DONE（本次增量任务；不改变 Phase 2 阶段状态）

## 本次完成
- 日志弹窗统一为操作者、时间、操作摘要和字段前后值，配置、经营计划和后台审计页面复用同一呈现组件。
- 使用用户提供的图标作为 favicon，浏览器标签标题按当前路由显示页面名称。
- 消除测试 profile 隐式指向 `localhost:3307/mrp_test` 的默认连接；相关 Spring 测试改用现有 Testcontainers 集成基类提供的动态 MySQL 地址。
- 在 `AGENTS.md` 中补充环境故障证据判定，明确 Docker 凭据助手的匿名镜像提示不能单独作为 Docker Hub 故障证据。

## 业务不变量
- 审计原始前后值仍由后端保存和查询，前端只转换展示形式。
- 后端测试使用独立 MySQL 容器；真实联调使用独立验收库，不接触业务库。
- 未更改 Java、Node、Spring Boot 或依赖版本。

## 验证命令与结果
```text
frontend: npm run build；npm test；Playwright 日志呈现用例 1 passed
backend: ./mvnw test；Surefire 727 tests, 0 failures, 0 errors, 0 skipped
backend: ./mvnw compile；BUILD SUCCESS
HTTP: 首次登录后修改初始密码；GET /api/v1/audit-logs -> 200
HTTP: 新增验收工厂后，按资源过滤日志 -> 200、total=1、action=CREATE
HTTP: Vite /api 代理查询同一日志 -> 200、total=1
HTTP: 前端页面及 favicon -> 200
```

## 未完成与风险
- 本次运行使用独立验收库 `mrp_audit_verify_20261109`；未验证用户现有业务库的数据内容。
- Testcontainers 仍依赖可运行的 Docker 服务与公开 MySQL 镜像；凭据助手提示本身不影响匿名拉取和容器启动。

## 决策/待确认
- 无。

## 下一步
- 后续遇到启动失败时，按实际 profile、连接地址、端口和错误码分类，再决定是否属于版本环境问题。
