# 工厂接口运行环境修复报告

## 状态
DONE：修复、编译、测试、真实 API/浏览器验收与独立复核全部通过。本任务不改变 Phase 2 整体门禁。

## 本次完成
- 验收真实调用链：两个页面调用 `mrpApi.getFactories()`，后端 `ConfigMasterController` 提供 `/api/v1/mrp/factories`；5180 前端代理至 8080。
- 原 8080 进程启动于改版前，Spring 上下文没有加载新增工厂路由；同一最新代码在隔离验收 8082 返回正常。原主服务工厂接口 404、月份日历接口 500。
- 主库已应用至 V044。备份主库后，沿用原 Java 21、启动参数、环境及数据库，编译最新代码并重启 8080 主服务；同步此前已通过构建验收的前端 dist 到后端静态资源。
- 主服务健康检查 UP；工厂、月日历、仓库映射接口及两个实际页面恢复。

## 修改文件
- `backend/src/main/resources/static/`：同步既有已验证前端构建产物；本修复未修改 Java/Vue 业务源码或版本配置。
- 本报告、报告索引、工作流和缺陷记录；`test-evidence/factories-runtime-fix-2026-10-14/` 保存验收证据。

## 业务不变量
- 保留原业务库与原工厂仓库映射：永惠 → XA371 永惠仓库，爱培科 → XA368 爱培科仓库。
- 页面验收只读取业务数据，新增弹窗查看工厂选项后取消，未保存配置；未填充当前日历。
- 未修改已应用迁移、技术栈及历史验收 8081 服务。
- 重启前主库备份：`/tmp/mrp-factories-main-before-restart.sql`，506663253 字节，权限 600；未将凭据或数据库内容写入报告。

## 验证命令与结果
```text
./mvnw compile -q（静态资源同步前、后）
PASS，退出码 0
./mvnw test -q
PASS，39 套件、304 测试、0 失败、0 错误、0 跳过
```
- 主后端 8080 与前端 localhost:5180、127.0.0.1:5180 共 9 项认证读取验证全部 HTTP 200：工厂 2 条，2026-10 月日历 31 天，既有仓库映射 2 条。
- 实际 Chrome 原 5180 页面刷新后，生产日历正常显示；工厂仓库映射正常显示两条已有映射；新增弹窗工厂选项显示爱培科、永惠，取消退出。
- 证据：`api-results.json`、`backend-test-summary.json`、`calendar-restored.png`、`mapping-restored.png`、`factory-options-restored.png`。
- 独立 Reviewer PASS：复核实际调用链、权限、9 项 API 归档、三个浏览器截图、39 个 Surefire 套件；64 个前端构建文件与后端静态资源逐文件 hash 一致。结论归档 `independent-review.md`。

## 未完成与风险
- 本次解决旧运行进程导致的接口缺失；CAP-1014-03 既有工厂数据域隔离债务及 Phase 2 整体 REVIEW 保持原状。

## 决策/待确认
- 无需业务决策。

## 下一步
- 用户刷新原页面即可使用。
