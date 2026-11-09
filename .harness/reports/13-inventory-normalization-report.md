# 13 inventory-normalization 任务报告

## 状态

`DONE`

## 本次完成

- InventoryNormalizationService: 库存快照规范化、库存池创建、分配预览/提交/更新/重置
- MrpInventoryController: 全部库存池和分配 API
- 修复 MrpInventoryPoolMapper.xml version=#{version+1} bug
- 增加 selectByBatchAndMaterial、selectAggregatedBySnapshotId、selectById、selectByBatchId 查询

## 修改文件

| 文件 | 操作 |
|---|---|
| `backend/src/main/java/com/mrp/mrp/service/InventoryNormalizationService.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/controller/MrpInventoryController.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpInventoryBalanceMapper.java` | 修改 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpInventoryPoolMapper.java` | 修改 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpInventoryAllocationMapper.java` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpInventoryBalanceMapper.xml` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpInventoryPoolMapper.xml` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpInventoryAllocationMapper.xml` | 修改 |

## 验证命令与结果

```text
$ ./mvnw clean compile -q → BUILD SUCCESS
$ ./mvnw test -pl . -Dtest='!BomExplosionServiceTest' → 124/124 PASS

$ curl -X POST ".../inventory/normalize/1?factoryCode=F001&inventoryOrg=ORG1"
→ success=True, count=396 (materials normalized)

$ curl ".../inventory/balances/1"
→ total=396, e.g. 4850AA800002: 85.0

$ curl -X POST ".../inventory/pools?batchId=1&snapshotId=1&factoryCode=F001"
→ success=True, count=396 (pools created)

$ curl -X POST ".../inventory/allocations/preview?poolId=1&demandKey=test&rootMaterialCode=4850AA800002&sourceMonth=2026-10-01&grossQty=50"
→ allocatedQty=50 (min of gross 50, available 85)

$ curl -X POST ".../inventory/allocations/commit" -d '{"poolId":1,...,"allocatedQty":50,...}'
→ id=1, allocatedQty=50

$ curl -X POST ".../inventory/allocations/commit" -d '{"poolId":1,...,"allocatedQty":100,...}'
→ MRP_ILLEGAL_STATE: "Allocation exceeds available balance: requested=100, available=35"

$ curl -X PUT ".../inventory/allocations/1?allocatedQty=60"
→ success, pool version=2, allocatedQty=60

$ curl -X POST ".../inventory/allocations/1/reset"
→ success, pool version=3, allocatedQty=0
```

## 业务不变量

- 不超可用余额：commit 时校验 available-reserved-allocated ≥ requested
- 乐观锁：pool.version 递增校验
- 分配预览不持久化：preview 返回计算结果，commit 才入库
- 缺库存处理：available=0 时 allocatedQty=0

## 未完成与风险

- Q2-012 库存组织与可用量映射未冻结，当前使用 shippingAvailableQty 作为可用量
- 实际库存组织映射规则待业务确认

## 决策/待确认

- Q2-012: inventory_org 和 available_quantity 的映射规则

## 下一步

进入阶段 14：BOM 快照与净需求引擎
