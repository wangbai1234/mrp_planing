# Prompt 09：Docker 交付和 UAT 收口

前置：所有前置阶段门禁通过。

读取 `project.md`、`security-and-ops.md`、`test-and-gates.md`、UAT 和全部任务报告。完成：

- 编写生产可用 Dockerfile 和 `deploy/docker-compose.yml`；API 镜像直接托管 Vue 构建产物，配置 SPA fallback 和静态资源缓存头，初期不添加 Nginx 容器；
- 若目标环境没有外部 TLS/反向代理入口，必须在报告中明确公网 HTTPS 入口方案；不得把“无 Nginx”误解为可以裸露 HTTP 服务；
- Docker 多阶段构建使用仓库锁定的 Maven Wrapper、Java 21 和 Node.js LTS 主版本；不得使用未声明的 `latest` 镜像标签；
- MySQL、Redis、API、Worker 从空目录启动；
- 迁移自动执行且重复启动安全；
- 健康检查、日志、文件卷、备份/恢复说明；
- 按 UAT 案例库逐条执行并记录证据；
- 运行手册、环境变量说明、故障恢复、计算任务重试和已知限制；
- 检查 Phase 2 功能没有伪装成已交付；
- 生成最终交付报告和发布检查清单。

交付前必须确认：无真实密钥、无高危漏洞、无控制台错误、无 READY 半成品版本、规则 checksum 可复核、部署可从空库重建。自测通过后状态设为 REVIEW；只有阶段复核 PASS 才可标为 DONE，随后执行最终交付审计。
