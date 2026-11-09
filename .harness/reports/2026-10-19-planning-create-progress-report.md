# 排产创建范围与计算进度报告

## 状态

REVIEW

## 本次完成

- “生成排产”弹窗仅保留工厂、经营计划和库存版本三个字段；工厂支持全部、爱培科、永惠，默认全部。库存版本只选择一次统一导入的版本。
- 创建接口仅提交这三个参数；服务端拒绝多个库存版本，并在创建时锁定最新 READY 的上海 BOM 和最新 PUBLISHED 的分类角色配置。
- 按所选工厂过滤经营计划和库存明细；库存依据明细仓库映射工厂，仅良品参与扣减，保留安全库存，不良品只供追溯。全部工厂仍按厂独立计算。
- 在顶部蓝色“当前排产”状态栏显示紧凑进度条、真实任务百分比和中文阶段文字；检查期间和任务进度暂不可读取时显示不确定进度，刷新失败时保留此前进度并提示重试。

## 修改文件

- `frontend/src/views/mrp/components/BatchCreateDialog.vue`
- `frontend/src/views/mrp/components/CalculationProgress.vue`
- `frontend/src/views/mrp/MrpWorkspace.vue`
- `frontend/src/api/mrp.ts`
- `backend/src/main/java/com/mrp/mrp/controller/MrpBatchController.java`
- 对应前后端测试及 `test-kit/2026-10-19/` 联调脚本。

## 业务不变量

- 一次排产只锁定一个统一库存版本；爱培科与永惠不互借库存。
- 仅所选工厂的经营计划行进入计算；全部工厂计算中的每厂结果应与单厂计算完全一致。
- 进度百分比取自服务端任务阶段，不按前端等待时间虚构。
- 主环境现有已发布批次不因验证而改变。

## 验证命令与结果

```text
cd backend && ./mvnw compile
通过

cd backend && ./mvnw test
543 项，0 failures，0 errors，0 skipped

cd frontend && npm run build
通过

cd frontend && npm test -- --run
12 个测试文件，74 项通过

curl http://127.0.0.1:8080/actuator/health
curl http://127.0.0.1:8180/actuator/health
两个服务均返回 {"status":"UP"}

cd frontend && UAT_PASSWORD=... ./node_modules/.bin/playwright test --config=../test-kit/2026-10-19/e2e/playwright.config.mjs
1 项真实浏览器端到端测试通过，分别计算爱培科、永惠、全部工厂。

python3 test-kit/2026-10-19/verify-scope-results.py
8 组精确逐行比较通过，单厂批次无异厂结果行。
```

隔离库使用同一经营计划 #18、库存 #8、BOM #34、分类角色 #9。测试批次 #50 爱培科、#51 永惠、#52 全部均计算至 READY，守恒差异 0，随后取消并保留计算事实供复核。每厂库存池 217 行、需求 1,524 行、末级需求 1,554 行、日排产 19,425 行；单厂与全部工厂对应切片逐字段一致。多库存版本请求返回 400 `SINGLE_INVENTORY_VERSION_REQUIRED`。任务实际经过 0%、10%“计算物料需求”、65%“生成日排产”、100%“计算完成”。

证据：`test-kit/2026-10-19/evidence/create-scope-progress-runtime.json`、`test-kit/2026-10-19/evidence/scope-exact-comparison.json`、`test-kit/2026-10-19/evidence/browser-results/`。

主环境浏览器只读核对新弹窗三个字段；API 再核对当前 #92 仍为 PUBLISHED，经营计划 #914、库存 #5 未变。

## 未完成与风险

- 进度是服务端阶段进度，不能表示 BOM 行或日排产单元格的精确完成比例。
- 主环境默认选中的最新已导入 `test.xlsx` 没有两厂经营计划行；弹窗提示并禁用确认。用户需选有数据的经营计划版本。此轮未在主环境创建新批次。
- 该任务已完成自测，仍待独立阶段复核后转为 DONE。

## 决策/待确认

- 无。

## 下一步

- 独立复核通过后关闭任务；需要排产时在弹窗中选有效经营计划及统一库存版本。
