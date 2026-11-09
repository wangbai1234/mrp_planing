# 门禁失败修复 Prompt

请复制下方正文，在阶段复核失败后使用：

```text
当前任务是修复上一阶段门禁失败，只处理复核报告列出的阻断问题，不开始下一阶段，不扩大业务范围。

先读取 project.md、source-precedence.md、change-control.md、原阶段 Prompt、原阶段报告和复核结论。

执行要求：
1. 为每个失败项定位根因；
2. 给出最小修复方案和将修改文件；
3. 保留原失败证据，不通过清空数据库、删除测试或降低断言绕过问题；
4. 重新运行原门禁和受影响回归测试；
5. 更新原阶段报告，附“修复前/修复后”证据；
6. 修复 Agent 只能保持 REVIEW；必须由原独立 Reviewer 再次 PASS 后，Coordinator 才改为 DONE；
7. 如果需要业务决定或新中间件，改为 BLOCKED 并创建 decision request。

完成后自动请求原 Reviewer 再次复核。同一根因连续三轮仍无法推进时，按 task-state 记录 BLOCKED 并停止等待用户决策。
```
