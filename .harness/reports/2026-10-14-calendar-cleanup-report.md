# 生产日历整数显示与历史配置清空报告

## 状态
DONE

## 本次完成
- 根据用户补充需求，日产能去除无意义小数尾零，月历、列表、编辑框均显示整数；整机口径为台/日。保留真实历史小数，避免舍入掩盖无效配置。
- 移除生产日历导出按钮及页面导出调用；同步原型及设计文档。
- 主库 mrp 的 mrp_production_calendar 全部425条配置已备份后通过现有审计DELETE接口清空。日期2026-07-25至2027-03-03，F001 50条、永惠191条、爱培科184条；剩余0条，新增425条删除审计。

## 修改文件
- frontend/src/views/mrp/ProductionCalendarConfig.vue
- frontend/src/views/mrp/__tests__/ProductionCalendarConfig.test.ts
- prototype/MRP日排产Phase2可交互原型.html（生产日历工具栏）
- docs/MRP生产日历与日产能改版设计方案.md

## 业务不变量
- 本次仅清空可维护的生产日历主数据。已有排产冻结日历、版本、单位快照及审计记录保留。
- 未修改Flyway迁移、技术栈配置、后端导出API；未引入依赖。
- 数值使用字符串格式化与原有BigInt精确汇总，不转浮点数。

## 验证命令与结果
- frontend npm run build：PASS；npm test：8文件48测试PASS。
- backend ./mvnw compile：PASS；./mvnw test：330测试PASS，无失败/错误/跳过。
- 前端构建产物已同步后端static，主8080服务按原参数重启。8080及5180真实API返回主配置空数组、两工厂正常、两工厂10月均31天未配置；后端首页与最新构建index一致。
- 浏览器：隔离5182环境中原1000.000000在月历/列表/编辑框显示1000，无导出按钮；主5180空日历正常加载，新增/导入可用，显示31天未配置及维护提示。
- git diff --check（本次业务文件）：PASS。
- 证据目录：test-evidence/calendar-cleanup-2026-10-14/，包含全部编译测试日志、API结果、浏览器DOM/AX和截图、删除结果。

## 删除过程与恢复信息
- 备份：/tmp/mrp-calendar-master-before-clear-20261014.json，权限600，保存原425条DB及API记录。
- 首次DELETE成功1条后，清理脚本读取Void响应的data字段失败；失败日志保留，改用get读取后继续剩余424条。原备份未覆盖，最终425条删除及审计均核对通过。
- 冻结数据hash采集从首条删除后开始，在剩余424条删除前后完全一致。代码调用链确认每次DELETE仅删除主表，并写审计；不把hash证据夸大为首条之前采集。

## 未完成与风险
- 独立Reviewer已复核PASS，报告：test-evidence/calendar-cleanup-2026-10-14/independent-review.md。
- 清空后须重新维护两工厂生产日和整机日产能，新计划才具备日历配置；既有排产冻结历史保留。
- 本任务不改变Phase 2整体阶段门禁。

## 决策/待确认
- 用户明确要求清空生产日历历史数据，已执行主配置全日期清空；保留已生成计划冻结记录供历史追溯。

## 下一步
- 本补充任务已完成，可重新维护两工厂生产日和整机日产能。
