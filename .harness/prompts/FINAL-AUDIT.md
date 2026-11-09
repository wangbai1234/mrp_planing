# Phase 2 最终交付审计 Prompt

请在阶段 19 完成并通过其独立 REVIEW 后，由另一独立 Reviewer 执行：

```text
对 MRP Phase 2 做最终只读优先审计。读取 AGENTS.md、project.md、所有 Harness 契约、workflow.yaml、10-19 阶段报告、v4.0需求、开发手册、规则库、追踪矩阵、问题池和 UAT；同时确认00-09历史状态未被篡改。

逐项验证：
1. 10-19 全部经过 IN_PROGRESS -> REVIEW -> 独立PASS -> DONE，依赖顺序正确；19 的 FINAL-AUDIT 不得作为 19 自身 DONE 的前置条件；
2. P2-R001至P2-R022每条都有真实代码、SQL、API/UI、自动化和运行证据；
3. A/B/C/D黄金数据、逐层扣库存、STOP/EXPLODE、共享库存、异常决策正确；
4. 日事实、生产日历、提前7天、待分配、日周月守恒和人工调整正确；
5. 已出货变化不影响本期公式、checksum和发布；
6. 所有输入/决策版本绑定，checksum稳定，任务幂等、并发、恢复和Writer回滚正确；
7. Phase1旧迁移未改，旧周数据/API只读兼容；
8. 权限码、工厂数据域、审计、上传/下载和秘密配置无高危问题；
9. 后端/前端全量测试、真实API/浏览器、Playwright、Docker空库/V020升级和UAT全部有PASS证据；
10. 性能数据真实、无N+1、分页/虚拟化和批量写符合契约；
11. 所有P0/P1及本需求阻断为0，TODO和已知限制明确；
12. 没有覆盖用户未提交修改、破坏主卷、伪造命令/测试或未经批准改变技术栈。

先枚举运行环境是否真实提供Superpowers能力。存在则记录真实名称、版本、调用和输出；不存在则以Harness REVIEW/REPAIR/FINAL-AUDIT和.superpowers/sdd证据完成等价审计，不得伪造命令。

输出最终PASS/FAIL和从“需求->代码->API/UI->测试->运行证据”的索引。FAIL时列阻断项、根因和应回到的阶段；Coordinator 将责任 DONE 阶段重开为 REVIEW，记录 revision/reason，触发 REPAIR-GATE，重跑受影响下游门禁并由同一 Reviewer 复核。PASS后生成最终交付报告，但不部署生产。
```
