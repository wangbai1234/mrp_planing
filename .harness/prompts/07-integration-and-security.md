# Prompt 07：前后端联调与安全加固

前置：前端页面和 API 均可独立运行。

读取 `security-and-ops.md`、`api-contract.md`。完成：

- JWT 登录、过期、撤销/版本控制；
- ADMIN、PLANNER、BUSINESS、WAREHOUSE、READONLY 角色；
- 服务端工厂数据域过滤；
- 导入、人工调整、发布、产能配置权限；
- 前端路由守卫和 401/403 处理；
- 上传扩展名、大小、sheet、行数和宏风险校验；
- SQL 参数化、敏感信息脱敏日志、CORS/CSRF 策略；
- API 错误、任务状态和审计记录联调；
- Docker Compose 下 API（直接托管 Vue 静态资源）、Worker、MySQL、Redis 一键启动；初期不添加 Nginx。

必须写未授权访问、跨工厂查询、重复确认、重复重算、过期 token 和非法文件测试。不要引入网关、MQ 或额外认证服务。完成后状态设为 REVIEW，等待复核。
