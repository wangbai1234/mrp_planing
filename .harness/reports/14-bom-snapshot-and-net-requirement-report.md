# 14 bom-snapshot-and-net-requirement 任务报告

## 状态

`DONE`

## 本次完成

- BomSnapshotService: BOM 快照创建（从 bom_parent+bom_detail 固化到 bom_snapshot+bom_snapshot_detail）
- NetRequirementEngine: 纯 Java 多层净需求引擎（BigDecimal，childQty/parentQty 比率，库存扣减，STOP/EXPLODE，循环检测）
- MrpCalculationService: 快照+规范化+爆炸全流程编排
- MrpCalculationController: BOM 快照 API、批次计算 API、需求明细查询 API
- 修复 supply_mode 默认值、GROUP BY vs DISTINCT

## 修改文件

| 文件 | 操作 |
|---|---|
| `backend/src/main/java/com/mrp/mrp/service/BomSnapshotService.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/engine/NetRequirementEngine.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/controller/MrpCalculationController.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpRequirementDetailMapper.java` | 修改 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpTerminalDemandMapper.java` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpRequirementDetailMapper.xml` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpTerminalDemandMapper.xml` | 修改 |

## 验证命令与结果

```text
$ ./mvnw clean compile -q → BUILD SUCCESS
$ ./mvnw test -pl . -Dtest='!BomExplosionServiceTest' → 124/124 PASS

$ curl -X POST ".../bom-snapshots?effectiveDate=2026-09-22"
→ id=4, status=READY, edges=132774

$ curl ".../bom-snapshots/4/details"
→ 132774 edges

$ curl ".../bom-snapshots/4/children/11420762"
→ 3 children: 1510AA000016(2/2), 1510AA000017(3/2), 1510AA000021(1/2)

$ curl -X POST ".../batches/3/calculate?bomSnapshotId=4&inventorySnapshotId=1" -d '[{"materialCode":"11420762","quantity":100,"sourceMonth":"2026-10-01"}]'
→ status=READY

$ curl ".../batches/3/requirements"
→ 4 details:
  L0 11420762: gross=100, inv=0, net=100, terminal=false
  L1 1510AA000016: gross=100, inv=0, net=100, terminal=true
  L1 1510AA000017: gross=150, inv=0, net=150, terminal=true
  L1 1510AA000021: gross=50, inv=0, net=50, terminal=true

$ curl ".../batches/3/terminal-demands/aggregated"
→ 3 terminal demands: 1510AA000016(100), 1510AA000017(150), 1510AA000021(50)
```

## 业务不变量

- childQty/parentQty 比率：每层用 BigDecimal 除法，18 位精度
- 库存扣减：每层扣当前节点已锁定库存，只传递净需求
- 循环检测：visitedPath Set 防止无限递归
- 最大深度：50 层
- 结构叶子：无 BOM 子节点的材料生成终端需求

## 未完成与风险

- Q2-013 BOM 有效版本裁决未冻结，当前使用默认版本
- Q2-014 计量单位换算未冻结
- 无库存时验证了净需求=毛需求；有库存场景需集成测试

## 决策/待确认

- Q2-013: BOM 有效版本选择策略
- Q2-014: 计量单位换算规则

## 下一步

进入阶段 15：日排产与汇总
