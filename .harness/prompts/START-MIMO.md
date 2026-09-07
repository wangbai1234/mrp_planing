# 第一次交给 Mimo 的启动 Prompt

请复制下方正文给 Mimo：

```text
你将负责完整实现 MRP Phase 1 项目，但必须使用仓库内 Harness 管理流程。

第一步，只执行 .harness/prompts/00-discovery.md，不要开始编码。

执行前必须完整读取：
1. project.md
2. AGENTS.md
3. .harness/README.md
4. .harness/runbook.md
5. .harness/source-precedence.md
6. .harness/technology-baseline.md
7. .harness/prompts/00-discovery.md 及其中引用的全部业务文档和资产

约束：
- 技术栈以 project.md 为唯一冻结基线；Maven Wrapper 是必须提交的 Java 构建入口；
- 前端构建使用 Node.js LTS + npm，并通过 `.nvmrc` 或 `package.json` `engines` 固定主版本；
- 不引入 MQ、XXL-JOB、MinIO、Elasticsearch、微服务等未批准中间件；
- 初期运行形态为 API（直接托管 Vue 静态资源）+ Worker + MySQL + Redis；Nginx 不是必选项。若目标环境没有现成 TLS/反向代理入口且需要公网 HTTPS，先在 Prompt 09 中提出受控入口方案并按变更控制记录；
- 系统和产能统一按周，不做日能力到周能力换算；
- Phase 1 不做 BOM 缺料和 PCBA 排产；
- 遇到历史文档冲突先按 source-precedence.md 已裁决项执行，未裁决冲突才发起决策；
- 开始任务时把 workflow.yaml 的 00 状态改为 IN_PROGRESS；
- 完成后将报告写到 workflow.yaml 声明的 output 路径；
- 自测完成后将 00 改为 REVIEW；只有阶段复核 PASS 后才改为 DONE；
- 完成 Prompt 00 后暂停，等待我确认，不得自动执行 Prompt 01。

先回复你已读取的文件、当前任务范围、明确不做项和验证方法，然后再执行。
```
