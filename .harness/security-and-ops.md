# MRP 安全与运行约束

## 1. JWT 和角色

- 登录后返回短时效 access token；
- 密码使用 BCrypt；
- JWT 中只放用户 id、角色和 token 版本，不放完整工厂数据；
- 服务端校验 token 签名、过期时间和 token 版本；
- 角色最小集合：`ADMIN`、`PLANNER`、`BUSINESS`、`WAREHOUSE`、`READONLY`；
- 工厂范围保存在 `user_factory_scope`，所有业务查询服务端强制过滤。

## 2. 审计

审计字段至少包括：操作者、角色、工厂范围、动作、资源类型、资源 id、前值、后值、输入版本、trace id、IP（若合规允许）和时间。密码、JWT、原始文件内容不能写日志。

## 3. 配置

所有环境差异通过环境变量或 profile 文件注入：MySQL、Redis、JWT 密钥、文件卷、任务轮询间隔、批次大小、日志级别。仓库只提交 `.env.example`，不得提交真实密钥。

## 4. 备份和恢复

- MySQL 使用定期逻辑备份或公司现有备份策略；
- Redis 不作为恢复依据；
- 文件卷备份与数据库元信息同时保留；
- 恢复演练必须验证：输入版本、排产版本、审计记录和导出文件元信息可关联。

## 5. 运行检查

健康检查至少区分：应用存活、MySQL 连通、Redis 连通、Worker 是否在消费任务。计算任务失败时记录失败原因和 trace id，不自动静默重试无限次。

初期可以由 Spring Boot 直接托管 Vue 静态资源，但生产流量必须位于受控网络入口之后。若没有云负载均衡、Ingress 或公司统一网关，必须在部署报告中落实 HTTPS/TLS 终止和反向代理方案；Nginx 只是候选实现，不因“少一个容器”而牺牲公网安全。

## 6. 依赖和数据安全

上传文件限制扩展名、大小、sheet 数量和行数；拒绝宏执行；导出文件使用随机文件名；下载接口校验用户数据域。SQL 必须参数化，禁止字符串拼接用户输入。

---

## 7. 权限开发规则（强制执行）

### 7.1 新增页面/按钮必须定义权限码

开发任何新页面或关键操作按钮时，必须：

1. **定义权限码**：在 `permission` 表中插入对应记录
   - 页面权限：`{module}:view`（如 `report:view`）
   - 按钮权限：`{module}:{action}`（如 `report:export`）

2. **前端使用权限组件**：
   ```vue
   <PermissionButton permission="report:export">导出</PermissionButton>
   ```

3. **路由配置页面权限**：
   ```typescript
   { path: '/reports', meta: { permission: 'report:view' } }
   ```

4. **后端 API 鉴权**：
   ```java
   @PreAuthorize("hasPermission('report:export')")
   ```

### 7.2 权限码命名规范

- 格式：`{module}:{action}`
- 模块名小写，动作名小写，用下划线分隔单词
- 示例：`schedule:view`、`inventory:template_download`、`user:assign_role`

### 7.3 权限分配流程

| 步骤 | 谁做 | 做什么 |
|------|------|--------|
| 1 | 开发者 | 在代码中使用权限码（前端+后端） |
| 2 | 开发者 | 通过 Flyway 迁移在 `permission` 表中插入权限码定义 |
| 3 | 管理员 | 在权限管理页面给角色分配权限 |
| 4 | 系统 | 用户刷新后自动生效 |

**禁止：**
- ❌ 在代码中硬编码角色判断（如 `if (role === 'ADMIN')`）
- ❌ 使用中文名称作为权限判断条件
- ❌ 跳过权限码定义直接在页面做判断
