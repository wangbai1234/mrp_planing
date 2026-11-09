# 经营计划工厂导入提示修复报告

## 状态
DONE。模板、解析器、页面提示、编译、全量测试和真实 HTTP/浏览器均已验收；独立专项复核 PASS。

## 本次完成
- 经营计划下载模板第一列明确只填“爱培科”或“永惠”，示例改为这两个实际排产工厂，并统一按整机形态展示。
- 工厂仓库映射模板与页面导入规则同样明确可用工厂；映射模板示例明确“示例：永惠”，按现有解析器“示例”前缀跳过。
- 经营计划上传预览对非规范工厂（如 YONGHUI、F001）产生 INVALID_FACTORY 行错误，原始行保留供错误报告，整份文件无法确认导入。中文合法工厂不受影响。
- 经营计划页面导入规则写清工厂值；审查物料、库存、库存策略、物料策略、生产日历、业务周等其他模板，未发现与本次“工厂代码示例不匹配排产工厂”相同的问题。库存模板的 `XA378_YONGHUI` 是仓库编码，不是工厂字段。

## 修改文件
- backend/src/main/java/com/mrp/importexport/excel/TemplateGenerator.java
- backend/src/main/java/com/mrp/importexport/excel/ForecastExcelParser.java
- frontend/src/views/forecast/ForecastImport.vue
- frontend/src/views/mrp/FactoryWarehouseMappingConfig.vue
- backend/src/test/java/com/mrp/forecast/ForecastFactoryRegressionTest.java
- backend/src/test/java/com/mrp/forecast/ForecastExcelParserTest.java
- backend/src/test/java/com/mrp/api/ImportApiTest.java
- backend/src/main/resources/static、frontend/dist（构建同步）

## 业务不变量
- 合法工厂为“爱培科”与“永惠”；不对任意英文/旧别名做猜测映射。
- 已确认的经营计划版本不修改；主库版本 1221 仍为 YONGHUI 六个月，用户需修正源 Excel 并重新导入生成新版本。
- 未修改已应用迁移、规则版本、排产日期或计算结果。

## 验证命令与结果
- `cd backend && ./mvnw compile -q`：PASS。
- `cd backend && ./mvnw -q -Dtest=ForecastFactoryRegressionTest,ConfigExcelParserTest test`：PASS。
- `cd frontend && npm run build`：PASS；`npm test -- --run`：81/81 PASS。
- 本地隔离服务 8180：下载经营计划/映射模板均 200；上传 YONGHUI 预览 202 且 INVALID_FACTORY，永惠预览 202 无错误。第二条整机示例更新后，8080/8180 下载经营计划模板均 200，B4=整机。
- 真实 Chrome 页面 `/forecast`、`/mrp/factory-warehouse-mappings` 的导入弹窗均显示新规则；主服务 8080 重启后两个模板下载 200 且内容正确。
- 全量后端 `./mvnw test`：第一轮 561/561 PASS；第二条示例形态调整后重新执行 `./mvnw compile -q && ./mvnw test -q`，最终轮 561/561 PASS，失败 0。

## 未完成与风险
- 独立专项复核见 `2026-10-20-factory-import-template-review.md`。
- 历史非规范工厂版本不会自动修正，也不会被默认映射为永惠。

## 决策/待确认
- 无；以当前排产与映射已实施的两工厂约定为准。

## 下一步
- 本修复已通过门禁并记录为 DONE；旧版经营计划需由用户修正工厂列后重新导入。
