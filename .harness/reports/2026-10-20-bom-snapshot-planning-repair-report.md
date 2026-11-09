# 从 NC 同步后生成排产的 BOM 快照修复报告

## 状态

REVIEW：代码、自测与实际接口验证完成，待独立复核。

## 根因

- 10 月 20 日的 NC 同步任务成功更新 bom_detail，但未生成不可变 bom_snapshot。
- 当前快照 SQL 按 effective_at DESC 排序，导致 9 月 24 日的旧快照 #27 被排产选择；该快照仅有 3 条 UAT 组织数据。
- 原上海快照 #34 建于 10 月 16 日，早于本次同步；新同步数据是上海组织的 146451 条明细。

## 本次完成

- 将当前 READY 快照改为按 id DESC 选择，避免有 effective_at 的历史快照压过最新生成的快照。
- BOM 同步提交事务内，在标记成功之前生成 READY 的上海组织快照；快照生成失败时事务回滚，Worker 记录失败。
- 用现有的快照 API 为最近一次已成功的同步补建快照 #35；122239 条有效明细，非上海组织 0 条。
- 重启 8080，5180 代理返回当前快照 #35；受控批次创建请求通过 BOM 校验并在刻意设置的无效角色版本处返回 INVALID_CATEGORY_ROLE_VERSION，未产生批次。

## 修改文件

- backend/src/main/resources/mapper/mrp/BomSnapshotMapper.xml
- backend/src/main/java/com/mrp/bom/service/BomSyncCommitService.java
- backend/src/test/java/com/mrp/bom/service/BomSyncCommitServiceTest.java

## 业务不变量

- 历史快照不修改；批次依然锁定不可变快照。仅上海组织参与新快照。
- 同步成功表示本地 BOM 与排产所用快照一同就绪。

## 验证命令与结果

- backend: ./mvnw compile -q 通过。
- backend: ./mvnw -q -Dtest=BomSyncCommitServiceTest,BomSnapshotServiceTest test 通过。
- backend: ./mvnw test -q 退出码 0；79 份报告，566 项测试，0 failures、0 errors。
- 实际 8080 /actuator/health 返回 200；5180 代理的 /api/v1/mrp/bom-snapshots/current 返回 #35 READY。
- 实际 MySQL 查询：快照 #35 共 122239 条有效明细，非上海组织 0 条。
- 浏览器打开 5180 排产页面，生成排产弹窗正常加载。

## 未完成与风险

- 未代用户真正创建排产批次，以免在业务数据中生成额外版本。
- 独立复核仍待执行。

## 下一步

- 刷新排产页面后重新选择工厂、经营计划、库存版本并确认。
