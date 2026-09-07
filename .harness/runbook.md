# Mimo 执行 Runbook

## 开始一次任务

将下面的公共前缀与一个阶段 Prompt 拼接后发送给 Mimo：

```text
你正在执行 MRP Harness 项目。先读取根目录 project.md，再读取本 Prompt 引用的全部契约、需求、规则和资产。不要臆造规则，不要引入未冻结的中间件。先给出实施范围、将修改文件、业务不变量和验证命令，再开始工作。完成后必须按 .harness/task-state.md 输出任务报告，真实记录命令、结果、风险和下一步。门禁未通过时不能报告 DONE。
```

开发执行顺序：`00` → `01` → `02` → `03` → `04` → `05` → `06` → `07` → `08` → `09`。

每个阶段的管理循环：

```text
执行阶段 Prompt
    ↓
阶段状态进入 REVIEW
    ↓
执行 REVIEW-STAGE.md
    ├─ FAIL -> REPAIR-GATE.md -> 再复核
    └─ PASS -> 当前阶段 DONE
                    ↓
          CONTINUE-NEXT-STAGE.md
```

全部阶段完成后执行 `FINAL-AUDIT.md`。

## 阶段切换

只有当前 Prompt 的门禁通过并经 `REVIEW-STAGE.md` 复核后，才发送 `CONTINUE-NEXT-STAGE.md`。若 Mimo 报告 BLOCKED：

1. 要求它保留失败现场；
2. 让它只做诊断和最小修复，不扩展范围；
3. 同一阻塞连续三次仍无法解决，记录 `decision-log.md` 并交由项目负责人决策；
4. 不得跳过阻塞任务继续开发依赖它的阶段。

## 推荐发送格式

```text
[公共前缀]

现在执行：.harness/prompts/01-backend-bootstrap.md
完成后将状态设为 REVIEW 并暂停，不要自动开始下一个阶段。
```

## 每阶段复核问题

- 是否只修改了本阶段范围内的文件？
- 是否引入新中间件或隐藏依赖？
- 是否有业务规则写在多个地方导致漂移？
- 是否绑定了输入版本和 rule_version？
- 是否有测试证明数据准确，而不是只证明接口返回 200？
- 是否留下可重跑、可定位、可恢复的任务现场？
- Java 构建是否使用提交到仓库的 Maven Wrapper，而不是机器上的未知全局 Maven？
- 前端构建是否使用 `.nvmrc` 或 `package.json` `engines` 声明的 Node.js LTS 主版本？
- Docker/生产入口是否明确由外部网关、Ingress、负载均衡或受控反向代理承担 HTTPS；是否错误地裸露 HTTP？

## 交付报告结构

每个阶段报告至少包含：状态、完成项、修改文件、依赖变化、业务不变量、验证命令与结果、未完成项、风险、待决策和下一步。最终报告附 UAT 证据索引和 Docker 启动命令。

## 第一次启动

第一次交给 Mimo 时，直接使用 `.harness/prompts/START-MIMO.md`；以后使用阶段执行/复核/修复/继续 Prompt，不需要每次重新组织完整上下文。

如果会话中断或更换模型，先按 `.harness/handoff.md` 恢复。开放问题以 `.harness/issue-log.md` 为准，聊天记录不作为唯一进度来源。
