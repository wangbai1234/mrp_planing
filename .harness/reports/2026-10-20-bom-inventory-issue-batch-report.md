# BOM 冻结口径、缺库存预检与批量确认修复报告

## 状态

REVIEW；待独立 Reviewer 复核，不自行标记阶段 DONE。

## 本次完成

- 核对批次 #104：BOM 管理当前树有 `6810AA800157 → 5051AA800042`，该关系 `is_default=N`、版本 1.1；BOM 快照 #35 按默认件筛选，因此没有这条边。纠正先前“042 不属于该整机”的错误解释。
- 树节点展示 NC 原始“非默认”字段；该字段不决定当前排产是否沿此关系下钻。
- 批次 #104 锁定库存快照 #6；该快照包含 6 个不同料号、7 行库存。沿冻结 BOM 和有效下钻策略访问 22 个独立料号，预检给出 16 条缺库存问题。`6210AA800340` 为 STOP，其下 37 条不继续检查；`5052AA800025` 经 `5070AA800080` 进入有效路径且有 GOOD 库存 500。
- 修正缺库存节点导致下游漏检：记录异常后继续沿净需求检查子项。`SO-VX00-99` 在 BOM 数据中存在、本地物料管理未维护，按物料主数据缺失提示，不再误报角色缺失。
- 异常列表支持关键词查询和当前页勾选；仅开放的缺库存问题可批量确认按零库存，需填写原因；后端事务校验同批次、问题类型、状态、ID 不重复及最多 100 项。

## 修改文件

- `backend/src/main/java/com/mrp/mrp/engine/NetRequirementEngine.java`
- `backend/src/main/java/com/mrp/mrp/service/DecisionService.java`
- `backend/src/main/java/com/mrp/mrp/controller/MrpBatchLifecycleController.java`
- `backend/src/main/java/com/mrp/mrp/repository/MrpDecisionIssueMapper.java` 及其 XML Mapper
- `backend/src/main/java/com/mrp/config/SecurityConfig.java`
- `backend/src/main/java/com/mrp/bom/domain/BomTreeNodeV2.java`、`backend/src/main/java/com/mrp/bom/service/BomService.java`
- `frontend/src/api/mrp.ts`、`frontend/src/views/mrp/components/DecisionCenter.vue`、`frontend/src/views/bom/BomView.vue`
- 相关 `BomServiceTest`、`MissingInputIssuesTest`、`DecisionServiceTest`

## 业务不变量

- 缺库存表示本批次锁定的工厂库存快照中没有该料号的库存行；与“有库存行但数量为 0”不同。
- 批量确认只记录本批次业务决策，不向库存快照写入库存行，不改变 BOM、主数据或其他批次。
- 全部 OPEN 问题处理完才能进入待计算，正式计算仍受待分配量等门禁约束。

## 验证命令与结果

- `cd backend && ./mvnw compile -q`：通过。
- `cd backend && ./mvnw -q -Dtest=MissingInputIssuesTest,BomServiceTest,DecisionServiceTest test`：通过。
- `cd frontend && npm run build`：通过；`npm test -- --run src/utils/__tests__/mrpDisplay.test.ts`：通过。
- `git diff --check`：通过。
- Spring Boot 8280 健康接口 HTTP 200。管理员登录后真实 API 返回：当前 BOM 树 24 个直接子项，其中 042 为 `isDefault=N`；批次 #104 异常 17 条，含 16 条缺库存和 1 条物料主数据问题；042 无异常。
- Chrome 访问对接 8280 的 Vite 5182：异常抽屉显示勾选列与批量按钮；BOM 树展开后 042、025 等非默认子项显示“非默认”。
- 批量接口成功路径此前使用测试问题验证，并精确回滚测试决策与状态；复核批次 #104 仍为 WAITING_DECISION、16 条未解决。非法请求返回 400，无数据库变更。
- `cd backend && ./mvnw -q test`：最终代码全量回归退出码 0。

## 未完成与风险

- 独立 Reviewer 尚未复核，保持 REVIEW。
- `SO-VX00-99` 仍未在本地物料管理维护；已解决问题只是批次决策，不会自动补建主数据。
- 现有 5181 开发页面代理旧 8080 服务；此次浏览器验证使用 5182 对接最新 8280 服务，其他运行实例需按部署流程更新。

## 决策/待确认

- 2026-10-20 用户已确认：新排产直接沿最新同步的全部有效 BOM 关系下钻，NC 默认件字段不参与筛选；旧批次不改写，需重新创建批次才能按新规则预检。

## 下一步

- 交独立 Reviewer 复核；在可创建新批次的环境按 C023 核对全部当前 BOM 关系与缺库存清单。
