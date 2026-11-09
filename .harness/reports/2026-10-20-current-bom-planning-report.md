# 新批次使用当前同步 BOM 修复报告

## 状态
REVIEW；待独立复核。

## 根因与业务裁决
- 旧批次 #104 的 BOM 快照 #35 生成时过滤 NC 的 `is_default=N`，因此 `6810AA800157 → 5051AA800042` 没有进入该批次，尽管当前 BOM 树确有此关系。不能推断 NC 将其设为非默认的业务原因。
- 用户 C023 确认：新批次使用当前同步的全部有效关系；只有配置 `STOP` 才停止下钻。库存快照继续按版本锁定；无对应工厂仓库库存行才算 `INVENTORY_MISSING`。

## 修改文件与内容
- `BomSnapshotService`、`MrpBatchController`、`BatchOrchestratorService`、`MrpCalculationService`、`MrpCalcBatch`、Mapper、V052 新迁移：新批次以当前 BOM 和校验码为输入；历史批次仍读原快照。
- `NetRequirementEngine`：缺角色节点继续检查下级，但尊重 `STOP`；缺本地物料主数据报 `MATERIAL_MISSING`。
- `SecurityConfig`、`MrpBatchController`、`frontend/src/api/mrp.ts`：创建页 BOM 状态改用排产权限接口。
- `BatchCreateDialog.vue`、`MrpWorkspace.vue`、`DecisionCenter.vue`、BOM 页面和相关测试、契约与原型文案同步更新；缺库存支持批量确认按零，仅本批次生效。
- 两项历史集成测试夹具补建历史快照编号，符合旧批次兼容路径。

## 业务不变量
- 不因 NC `HBDEFAULT/is_default` 过滤新批次关系；不修改旧批次的 BOM 输入或既有结果。
- 当前 BOM 被重新同步后，预检、计算或发布不得悄悄沿新关系继续。
- `STOP` 的子孙不预检；确认缺库存不改写库存快照，只把该批次缺失库存按零处理。

## 验证命令与结果
- `cd backend && ./mvnw compile -q`：退出 0。
- `cd backend && ./mvnw test -q`：退出 0；80 个测试报告、578 项、0 失败、0 错误。专项测试覆盖当前 BOM、STOP、权限和历史夹具。
- `cd frontend && npm run build`：退出 0；`npm test -- --run src/views/mrp/__tests__/BatchCreateDialog.test.ts src/api/__tests__/mrp-query.test.ts`：17 项通过。
- 8381 真实服务：健康和首页 200，未认证状态接口 401；管理员登录后新 BOM 状态 200、133162 条当前有效关系。当前树整机 6810AA800157 有 24 个直接下级，含 `5051AA800042/isDefault=N`。库存版本 #6 有 7 行，含 5052AA800025 的永惠 GOOD 500，无 5051AA800042。旧批次 #104 有 16 条缺库存，其 `SO-VX00-99` 同时报缺库存和物料主数据缺失。
- 5183 新 Vite 页面连接 8381，工作台及异常抽屉正常加载，展示缺库存定义、勾选与批量按钮；`git diff --check` 通过。

## 未完成与风险
- 主库存在未完成的历史批次 #105（WAITING_DECISION），未取消或改写，因此未在主库新建批次复核新异常数量；应在隔离环境或完成该批次后执行新批次完整预检。
- 独立 Reviewer 未复核，保持 REVIEW。`SO-VX00-99` 本地物料管理仍未维护，业务需补充主数据。

## 下一步
- 独立复核后，在可创建批次的隔离库验证 042 缺库存及完整 MRP 闭环。
