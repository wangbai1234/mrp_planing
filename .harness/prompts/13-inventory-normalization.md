# 阶段 13：库存规范化与库存池骨架

## 目标

使库存覆盖 BOM 全层级并形成唯一计算真源，建立永惠/爱培科/惠州仓等仓库范围、工厂仓库一对一映射、安全/预警库存和共享库存池骨架。依赖深层 gross 的 allocation demand 由阶段 14 生成，阶段 13 不提前生成需求分配。

## 必读

`.harness/import-rules.md`、`assets/import-design-rules.md`、`data-contract.md`、开发手册第 5/6.1/9 节及现有两条库存导入链路。

## 执行

1. 明确 `inventory_item` 为原始/适配数据，确认后规范化到唯一计算快照；禁止两表相加。
2. 模板/解析器使用表头+提示行+示例，保存 rawRows/headers，填充稀疏列，数量 BigDecimal，错误原因最后一列。
3. 建立快照组和仓库范围；每份导入文件在范围头选择一个仓库，不按明细行重新映射工厂/库存组织；模板和预览/错误报告必填 `inventory_quality`。
4. 按快照+仓库+物料+库存品质汇总；GOOD 进入 MRP，DEFECTIVE 只追溯；建立已发布工厂仓库一对一映射。
5. 区分“有记录且为 0”和“无记录”；无记录生成 INVENTORY_MISSING。
6. 按仓库+物料版本化维护安全库存和预警库存；安全库存写入 pool reserved，`allocatable=MAX(0,GOOD-safety)`，风险投影不阻止生成需求缺口。
7. 建立库存池和分配约束 API。本阶段只提供池、约束和提交能力，不生成依赖下游 gross 的最终 demand；阶段 14 按经营计划优先级生成自动分配。
8. 校验不超 gross、不超扣除 reserved 后的池余额、revision 409 和上游变化失效。
9. 所有列表服务端分页，所有写入有权限和审计。

## 门禁

- Excel 正常/稀疏/错误/大文件测试；
- 整机、机头、PCBA、原材料库存均可规范化；
- 缺行确认0、修正快照、库存池骨架和并发 revision 测试；
- available=0、单一需求、available 足够/不足覆盖总毛需求的库存池骨架测试；不足且多来源时由阶段 14 按来源月+经营计划顺序自动分配；
- compile/test + 实际 API/curl；若本阶段有前端页面，执行 build/浏览器验证。

完成后 REVIEW，PASS 后进入 14。
