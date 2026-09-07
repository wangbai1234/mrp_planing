# 阶段复核 Prompt

请复制下方正文，在 Mimo 完成任一阶段后使用：

```text
现在只复核刚完成的 Harness 阶段，不要开始下一阶段，也不要增加新功能。

读取：
- project.md
- .harness/workflow.yaml
- 当前阶段 Prompt
- 当前阶段报告
- 当前阶段引用的契约
- .harness/test-and-gates.md
- .harness/traceability-matrix.md

逐项核验：
1. 实际修改是否超出当前阶段；
2. 是否引入未批准的中间件或依赖；
3. 报告中的命令是否真实运行、结果是否可复核；
4. 业务规则是否与 source-precedence.md 一致；
5. 数据库、计算、API、权限或前端是否存在隐藏的临时实现；
6. 当前门禁是否全部 PASS；
7. 失败时是否错误地标为 DONE；
8. 是否更新了 workflow.yaml、阶段报告和追踪矩阵。

输出：PASS 或 FAIL；列出证据、阻断问题和最小修复清单。FAIL 时把阶段状态设为 REVIEW，不得进入下一阶段。PASS 时可以保持 DONE，但仍然暂停等待我的指令。
```
