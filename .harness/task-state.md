# Harness 任务状态与报告模板

## 1. 状态

```text
TODO -> IN_PROGRESS -> REVIEW -> DONE
                         └-> BLOCKED
```

- `TODO`：尚未执行；
- `IN_PROGRESS`：正在实现；
- `REVIEW`：代码和自测已完成，等待独立阶段复核；
- `DONE`：门禁全部通过、阶段复核 PASS 并有报告；
- `BLOCKED`：同一阻塞条件已连续三次检查仍无法推进，或需要用户作出业务决策。

## 2. 每个任务的最小报告

```markdown
# 任务报告

## 状态
DONE / REVIEW / BLOCKED

## 本次完成
- 

## 修改文件
- 

## 业务不变量
- 

## 验证命令与结果
```text
command
result
```

## 未完成与风险
- 

## 决策/待确认
- 

## 下一步
- 
```

## 3. 失败处理

失败时先保留失败现场和日志，禁止删除数据库或重置工作区来“让测试通过”。如果必须重新生成测试数据，使用独立 schema 或 Docker volume，并在报告中写清楚。

跨会话停止和恢复遵循 `.harness/handoff.md`。所有未解决缺陷写入 `.harness/issue-log.md`，不能只留在聊天记录里。
