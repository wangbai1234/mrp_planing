# 阶段 19：Docker、UAT 与最终审计

## 目标

在隔离环境完成空库、升级、真实运行、UAT 和最终闭环，不部署生产。

## 必读

`test-and-gates.md`、`runbook.md`、`FINAL-AUDIT.md`、Phase 2 UAT、完整开发手册完成定义。

## 执行

1. 先审查并修复 Docker/UAT 脚本中的错误拼写、虚假 PASS 和破坏性 `down -v`。
2. 使用独立 compose project、端口和卷，不触碰用户主环境。
3. 验证空库 V001->最新和从 V020 升级；重复启动安全。
4. 启动 MySQL、Redis、API、Worker 和前端静态资源，验证 health、SPA refresh、任务消费。
5. 按 `reports/stage18-19-five-hour-acceptance.md` 的 300 分钟清单执行各功能模块的代表性闭环与 P0/P1 正反例，并把 119 项逐项映射到直接证据、有效复用证据、经裁决过时或延期；不得把映射、过时或延期当作 PASS。
6. 浏览器检查 Console/Network 并保存关键截图/trace。
7. 枚举真实 Superpowers 能力；存在则记录并执行，不存在则按 SDD + Harness review/repair/final audit。
8. 任何失败先保留现场、追踪根因并做定向回归；同批修复完成后做一次全量三层验证和受影响浏览器/API 复核，独立 Reviewer 重验。
9. 更新追踪矩阵为真实 IMPLEMENTED/VERIFIED，不能批量虚标。

## 最终门禁

- 本批编译、单元/集成、定向 API/浏览器、Docker 和覆盖映射中的全部 P0/P1 门禁 PASS；过时/延期项有明确裁决和风险接受，不得虚报原 119 项全 PASS；
- P0/P1 和本需求阻断项为 0；
- migration/checksum/权限/性能/安全证据齐全；
- `.harness/reports/19-*`、UAT 记录和 `.superpowers/sdd/2026-09-22-phase2-daily-mrp/` 完整；
- 本阶段实现、Docker/UAT 门禁和独立 REVIEW PASS 后，Coordinator 才把 19 置 DONE；随后单独执行 FINAL-AUDIT。FINAL-AUDIT 失败时，Coordinator 将责任阶段从 DONE 重开为 REVIEW，记录 revision/reason，执行 REPAIR-GATE、受影响下游门禁和最终审计；未通过不得报告完成。
