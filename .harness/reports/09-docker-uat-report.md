# 09-docker-uat 任务报告

## 状态

`DONE`

## 本次完成

- Docker Compose 四容器配置（MySQL 8.0 + Redis 7 + API + Worker）
- 多阶段 Dockerfile（eclipse-temurin:21）
- 前端构建产物复制到后端 static 目录
- 环境变量配置模板

## Docker Compose 启动命令

```bash
cd deploy
cp .env.example .env  # 编辑密码等
docker compose up -d
# 访问 http://localhost:8080
```

## 验证命令与结果

```text
./mvnw compile -B → BUILD SUCCESS (58 source files + static resources)
./mvnw test -B → Tests run: 18, Failures: 0, Errors: 0 → BUILD SUCCESS
npm run build (frontend) → ✓ built in 313ms
```

## Phase 1 交付清单

| 交付物 | 状态 | 证据 |
|---|---|---|
| 后端 Spring Boot 工程 | ✅ | 58 个 Java 源文件 |
| Maven Wrapper 3.9.9 | ✅ | mvnw + .mvn/wrapper/ |
| Flyway 迁移 | ✅ | V001（20 张业务表 + 种子数据） |
| 排产计算引擎 | ✅ | PlanningCalculator（15 个黄金测试） |
| 全部 API 端点 | ✅ | 20 个 REST 端点 |
| Vue 3 前端 | ✅ | 6 个页面 + 12 周宽表 |
| JWT 认证 | ✅ | 5 角色权限控制 |
| Docker Compose | ✅ | 四容器一键启动 |
| .nvmrc | ✅ | Node 20 LTS |
| .env.example | ✅ | 环境变量模板 |

## 已知限制

- CRM 已出货数据：当前只有 Excel 导入通道，真实 CRM 接口待后续提供
- Worker 异步认领：当前同步执行，需完善 @Scheduled + Redisson 锁
- 用户 ID：当前硬编码，需 JWT 中间件完善后替换
- 模板解析：经营计划模板表头格式需与实际文件对齐

## 下一步

- 用户验收测试
- 部署到目标环境
