# 任务报告

## 状态
DONE

## 本次完成
- 物料缺料页改为单一标签导航，移除重复按钮、待处理下拉、数据口径和手动刷新入口；确认物料分析后自动刷新批次状态。
- 为物料缺料和生产执行补齐中文面包屑。
- 生产执行接口从已发布整机日排产生成待执行行，并叠加已有锁定、实际完成记录；锁定日可直接确认实际完成量。
- 排产页明确说明处理物料反馈只记录结论，不更改排产或物料数量。
- 更新本地验收 API/Worker JAR 与镜像，不改变 MySQL/Redis 容器和数据卷。

## 修改文件
- `backend/src/main/java/com/mrp/mrp/domain/MrpDailyExecution.java`
- `backend/src/main/java/com/mrp/mrp/service/MrpExecutionService.java`
- `backend/src/test/java/com/mrp/mrp/service/MrpExecutionServiceTest.java`
- `frontend/src/config/pageLabels.ts`
- `frontend/src/views/mrp/ShortageWorkspace.vue`
- `frontend/src/views/mrp/MrpWorkspace.vue`
- `frontend/src/views/mrp/components/ExecutionConfirm.vue`
- `frontend/src/views/mrp/__tests__/ShortageWorkspace.test.ts`
- `frontend/e2e/two-workbench-compact.spec.ts`

## 业务不变量
- 物料反馈处理结论不隐式改动排产或物料计算结果；实际调整仍走排产调整与物料重算流程。
- 执行记录只对已发布日排产展示计划状态，实际完成量仍需人工确认；已执行事实不可重复确认。
- 已发布批次和验收数据库未重置。

## 验证命令与结果
```text
./mvnw compile -q                                PASS
./mvnw test -q（测试进程连接独立 mrp-testkit:3317） PASS
./mvnw package -DskipTests -q                    PASS
npm run build                                    PASS
npm test -- --run                                PASS，111/111
npx playwright test e2e/two-workbench-compact.spec.ts --grep '排产与物料工作台|生产执行' --workers=1 --reporter=line
                                                  PASS，2/2
GET /actuator/health                              200，UP
GET /api/v1/mrp/batches/5/execution               200，11 行；旧接口为 0 行
GET /api/v1/mrp/batches/2/execution               200，20 行；旧接口为 0 行
```

## 未完成与风险
- Docker Hub 当前超时，标准 `docker compose --build` 未能联网构建；已使用本地基础镜像和当前 JAR 离线更新 `mrp-acceptance-api:local`，并重建 API/Worker 验证。
- 处理物料反馈并不会自动修改计划；需要排产员实际调整日排产时，才应观察计划变化并重新走确认/物料分析。

## 决策/待确认
- 无。

## 下一步
- 业务侧可在 `http://127.0.0.1:5180/execution` 查看已发布整机日排产，并选择日期锁定或登记实际完成量。
