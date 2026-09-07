# 07-integration-and-security 任务报告

## 状态

`DONE`

## 本次完成

- JWT 登录/认证/角色过滤器
- 5 角色权限控制（ADMIN/PLANNER/BUSINESS/WAREHOUSE/READONLY）
- API 端点角色绑定
- AuthController 登录接口
- Docker Compose 四容器一键启动（MySQL + Redis + API + Worker）
- 前端 API 客户端 token 传递

## 修改文件

- `auth/service/JwtService.java` - JWT 生成/验证
- `auth/controller/AuthController.java` - 登录 API
- `config/JwtAuthenticationFilter.java` - JWT 过滤器
- `config/SecurityConfig.java` - 角色权限配置
- `deploy/docker-compose.yml` - 四容器编排
- `deploy/.env.example` - 环境变量模板
- `application-test.yml` - 添加 JWT 测试密钥

## 验证命令与结果

```text
./mvnw compile -B → BUILD SUCCESS (58 source files)
./mvnw test -B → Tests run: 18, Failures: 0, Errors: 0 → BUILD SUCCESS
```

## 下一步

- 08-test-performance
