# 生产执行页搜索与分页报告

## 状态

REVIEW（实现和自测完成，待独立复核）

## 本次完成

- 生产执行表格改用服务端分页，默认每页 20 条，分页位于表格右下角。
- 增加右侧关键词框，按回车搜索整机料号或工厂，支持模糊匹配；清空立即重查，查询重置到第一页。
- 日期筛选、翻页和每页条数变化沿用已提交关键词，异步旧响应不会覆盖新结果。
- 分页 SQL 包含普通日计划、跨日调整生成的虚拟行及已保存的执行状态。

## 修改文件

- `frontend/src/views/mrp/components/ExecutionConfirm.vue`
- `backend/src/main/java/com/mrp/mrp/{controller/MrpExecutionController,service/MrpExecutionService,repository/MrpDailyExecutionMapper}.java`
- `backend/src/main/resources/mapper/mrp/MrpDailyExecutionMapper.xml`
- `backend/src/test/java/com/mrp/mrp/{RollingPlanningIntegrationTest,service/MrpExecutionServiceTest}.java`

## 业务不变量

- 执行确认、日期锁定及解锁仍使用原有写接口，已执行记录的计划数量和实际数量按保存值显示。
- 虚拟计划行 rowKey 与 `DailyPlanningService.applyOverrides()` 投影一致。
- 无新增依赖、迁移或技术栈变更。

## 验证命令与结果

```text
backend/./mvnw compile                                                PASS
backend/./mvnw test -Dtest=MrpExecutionServiceTest,RollingPlanningIntegrationTest
                                                                      PASS，41 tests
backend/./mvnw test                                                  PASS，731 tests
frontend/npm run build                                                PASS
frontend/npm test -- --run                                            PASS，118 tests
git diff --check                                                       PASS
```

- 独立新后端 `http://127.0.0.1:8082` 启动成功，`/actuator/health` 返回 200。
- 真实 API `GET /api/v1/mrp/batches/120/execution/page` 返回 137 条、首页 20 条；第 2 页 20 条；料号片段 `AA800` 模糊匹配，日期 `2027-03-26` 返回 1 条。
- 浏览器访问 `http://127.0.0.1:5182/execution`：回车搜索不存在的料号得到 0 条，清空恢复 137 条，下一页显示第 2 页；未提交关键词不随每页条数变化进入请求。1440px 下分页右对齐，390px 下页面无整体横向溢出。

## 未完成与风险

- 待独立 Reviewer 复核后更新为 DONE。
- 版本选择器仍按现有接口最多加载前 100 个已发布版本；本次仅处理执行记录列表。

## 决策/待确认

- 无。

## 下一步

- 独立复核分页查询、执行权限和页面交互。
