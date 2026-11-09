# 工厂与仓库主数据及库存导入改造报告

## 状态
DONE（工程与测试门禁）：实现与三层自测通过，独立 Reviewer 于 2026-10-28 给出 PASS；产品经理仍需核验业务口径及历史仓库属性。

## 本次完成
- 新增工厂主数据维护：编码、名称必填，支持新增、编辑、删除、停用和操作日志。已有业务引用的工厂禁止物理删除。
- 工厂下拉改从已维护且启用的主数据加载；工厂仓库映射由一厂一仓改为一厂多仓，仓库编码全局唯一，包含管理员、供应商、良品属性、仓库属性、可选正整数优先级。
- 良品仓按已冻结的数字优先级升序扣减；未分配优先级的良品仓按首次维护或导入顺序扣减；不良品仓不参与扣减。批次快照保存优先级和维护顺序，历史批次不追随主数据变化。
- 库存导入模板不再要求品质列。上传及确认时验证仓库启用状态和良品属性，并由仓库主数据赋予 GOOD/DEFECTIVE；错误进入预览，整份校验不通过则禁止确认。
- 修正审计日志工厂范围过滤中写死两家工厂的判断，增加多仓优先级、冻结快照和动态审计范围回归测试。

## 主要修改文件
- `backend/src/main/resources/db/migration/V056__factory_warehouse_master_and_priority.sql`：新增主数据与一厂多仓字段，保留历史映射快照。
- `backend/src/main/java/com/mrp/mrp/service/FactoryMasterService.java`、`ConfigMasterService.java`、`BatchOrchestratorService.java` 及相应 Controller、Mapper、XML：维护、权限、冻结与扣减。
- `backend/src/main/java/com/mrp/inventory/service/InventoryImportService.java`、`backend/src/main/java/com/mrp/importexport/excel/InventoryItemExcelParser.java`、`TemplateGenerator.java`：导入校验与模板。
- `frontend/src/views/mrp/FactoryWarehouseMappingConfig.vue`、`frontend/src/views/inventory/InventoryImport.vue`、`frontend/src/api/mrp.ts`：维护和导入交互。
- `backend/src/main/resources/mapper/audit/AuditLogMapper.xml` 及相关回归测试。
- `project.md`、`.harness/import-rules.md`、`.harness/calculation-contract.md`、`.harness/source-precedence.md`、`.harness/workflow.yaml`：同步新业务口径和任务状态。

## 业务不变量
- 同一仓库只属于一个工厂；跨工厂库存不得扣减。
- 库存按仓库和物料隔离，安全库存仍作硬保底；仅确认过良品属性的仓库允许新库存导入。
- 已应用的 V056 不再改写；已生成的历史批次保持原冻结配置和计算结果。

## 验证命令与结果
```text
cd backend && ./mvnw compile                        通过
cd backend && ./mvnw test                           618 项通过，0 失败/错误，BUILD SUCCESS
cd frontend && npm run build                        通过
cd frontend && npm test                             100/100 通过
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=8080  已启动
带认证 GET /api/v1/mrp/factory-master                200
带认证 GET /api/v1/mrp/factory-warehouse-mappings    200
带认证 GET /api/v1/mrp/factories                     200
带认证 GET /api/v1/inventory-imports/template        200，表头七列、无品质列
带认证 GET /api/v1/mrp/factory-warehouse-mappings/template  200
未知/未确认仓库库存上传预览返回 WAREHOUSE_NOT_CONFIGURED；确认返回 400，未写入正式库存。
浏览器访问 /mrp/factory-warehouse-mappings 与 /inventory：列表、维护弹窗和导入规则正常显示。
```

## 未完成与风险
- 独立 Reviewer PASS；最终后端全量 625/625、前端 101/101、Chromium 4/4 通过，详见本次需求变更测试报告。整体 Phase 2 状态不因本事项改动。
- 迁移后的两条历史仓库记录良品属性为“待确认”；业务维护属性后方可用于新库存导入。
- 工作区原有大量其他未提交改动，本次未清理、覆盖或重置。

## 决策/待确认
- 用户已确认按正整数优先级扣减，未填优先级按首次维护/导入顺序。无新增业务决策待确认。

## 下一步
- 业务管理员确认历史仓库的良品属性和仓库属性；产品经理核验需求稿。
