# 经营计划版本标题栏调整报告

## 状态
REVIEW：三层自测通过，待独立阶段复核。

## 本次完成
- 按 `prototype/MRP原型图-飞书风格.html:800-875` 的单表布局，以及业务确认，将版本号、版本详情与待发布预览收进表格标题栏，去掉占空间的元信息小字。
- 历史版本才显示“返回最新版本”；顶部不再显示全局日志，行内去掉版本差异，保留统一入口。
- 版本详情由真实版本元数据接口提供，创建人/生效人显示用户姓名，时间按来源类型正确格式化；新导入确认填充生效人和时间。
- 草稿差异忽略旧版逐月份的行号变化误报，摘要按不同工厂与料号计数；发布时保留未变月份的原优先级。

## 修改文件
- `frontend/src/views/forecast/ForecastImport.vue`；`frontend/e2e/forecast-version-heading.spec.ts`。
- `backend/src/main/java/com/mrp/forecast/{controller/ForecastWorkspaceController.java,repository/ForecastWorkspaceMapper.java,service/ForecastWorkspaceService.java,service/ForecastImportService.java}`。
- `backend/src/main/resources/mapper/forecast/ForecastWorkspaceMapper.xml`；`backend/src/test/java/com/mrp/forecast/ForecastWorkspaceIntegrationTest.java`。

## 业务不变量
- 生效版本与待发布草稿分离；MRP 引用生效版本，不直接引用预览。
- 未变更的原计划月份保留原排产优先级；正式版本和审计历史不原地修改。
- 历史版本仅可查看；操作日志仍从各行访问。

## 验证命令与结果
```text
cd frontend && npm run build                       通过
cd frontend && npm test                            100 项通过
cd backend && ./mvnw compile                       通过
cd backend && ./mvnw test                          611 项通过，0 失败/错误
cd backend && ./mvnw -q -Dtest=ForecastWorkspaceIntegrationTest,ForecastMetadataCapacityIntegrationTest test   通过
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=8082   已启动
经 8082 带认证 GET /api/v1/forecast-workspace/current、/draft、/draft/1/summary、/versions/1223/metadata   均返回成功；草稿摘要 changedLines=0，排除旧的虚假优先级提示
cd frontend && MRP_VITE_API_TARGET=http://127.0.0.1:8082 npm run dev -- --port 5192 --host 127.0.0.1   已启动
cd frontend && npx playwright test e2e/forecast-version-heading.spec.ts --reporter=line   1 项通过
```

## 未完成与风险
- 尚未进行独立 Reviewer PASS；因此保持 REVIEW，不将整体 Phase 2 标记 DONE。
- 现有仓库工作树已有大量其他未提交改动；本次未清理或覆盖。

## 下一步
- 独立复核后再更新阶段状态；若需要继续改造编辑权限，请另按确认的经营计划交互方案实施与验收。
