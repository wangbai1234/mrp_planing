# 排产滚动改版实施报告

## 状态
REVIEW

## 本次完成

- 联动调整的整机及末级转移量统一校验为整数；比例建议使用 HALF_UP 四舍五入，并以来源日可转整数数量为上限。
- 保存时校验整机与全部关联末级明细，拒绝小数、超量、过期预览和锁定/已执行日期；整组写入保持事务原子性。
- SQL 虚拟目标行的 rowKey 纳入 source_month，与 Java 查询口径一致。
- 双厂版本的查询、明细和联动调整增加服务端工厂数据范围校验。
- 双厂库存风险按工厂和仓库隔离；旧 v2 批次修订保持历史规则兼容。
- 生成排产时要求为每个映射仓库明确选择一个已导入库存快照。

## 业务不变量

- `167 * 50 / 100` 的末级建议量为 `84`。
- 手工提交 `83.5` 在写入覆盖记录前拒绝。
- 联动调整保留原始 BOM 小数精度并守恒。
- 日产能只用于整机日产能预警，不限制初始排产。

## 验证命令与结果

```text
backend/./mvnw compile -q                         PASS
backend/./mvnw test -q                            PASS
backend/./mvnw test -q -Dtest=DailyCapacityTransferIntegrationTest,RollingPlanningIntegrationTest,StockRiskServiceTest  PASS
frontend/npm run build                            PASS
frontend/npm test                                 PASS, 10 files / 57 tests
curl http://localhost:8084/actuator/health        200, {"status":"UP"}
curl POST /api/v1/auth/login                       200
curl GET /api/v1/mrp/batches                       200, isolated UAT database empty
```

独立 MySQL 集成复核：`RollingPlanningIntegrationTest` 6 项通过，覆盖整数建议、83.5 拒绝、原子提交、守恒、六个月日期轴、全厂负荷和工厂权限隔离。

## 未完成与风险

- 隔离验收库没有双厂业务主数据，因此尚未声称真实 HTTP 创建 v4 批次并提交联动调整；真实服务健康、登录和批次列表接口已验证。
- 同物料编码存在多个供应方式/终点类型时，明细接口仍需补充 supplyMode 过滤参数，当前物料策略冻结通常保证单一类型。


## 2026-10-15 真实入口补充复验

用户反馈页面未变化后，发现 5180 代理的 8080 仍是旧进程，Spring 静态资源未同步，且 v2 计划被前端分流到旧组件。现已统一新版日/周/月布局、同步资源并重启真实主服务；两个入口均用浏览器验证。已发布 #86 及全部日事实保持原值。最新前端 61 项、后端 355 项测试通过。

详见 [运行入口修复报告](2026-10-15-planning-runtime-fix-report.md)。本补充不替代真实 v4 新建排产/联动调整的完整业务验收，整体状态仍为 REVIEW。
