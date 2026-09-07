# 进入下一阶段 Prompt

请复制下方正文，在当前阶段复核 PASS 后使用：

```text
读取 project.md、.harness/workflow.yaml 和上一阶段最终报告。

确认上一阶段状态为 DONE、门禁复核为 PASS、当前没有未解决的 BLOCKED 决策。然后找到 workflow.yaml 中下一个依赖已满足且状态为 TODO 的阶段。

只执行该阶段对应的 Prompt：
- 开始前将该阶段状态设为 IN_PROGRESS；
- 先输出范围、明确不做、修改文件、业务不变量和验证计划；
- 按 Prompt 实施；
- 报告写到 workflow.yaml 声明的 output；
- 门禁全部通过后改为 REVIEW，先不要改为 DONE；
- 完成后暂停，等待阶段复核 Prompt。

如果没有可执行阶段，说明具体依赖或决策阻塞，不得跳过阶段。
```
