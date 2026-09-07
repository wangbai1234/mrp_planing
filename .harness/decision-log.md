# MRP Harness 决策日志

本文件只记录会影响实现的架构或业务决策。新增记录格式：日期、提出问题、证据、决定、影响、决定人/来源。

## 已冻结决策

| 日期 | 决策 | 影响 |
|---|---|---|
| 2026-09-03 | 采用 Java 21 + Spring Boot 3.x + MyBatis XML + MySQL + Redis/Redisson + Spring Batch + POI SAX + Spring Security/JWT + JUnit 5 + Docker | 模块化单体，API/Worker 同镜像 |
| 2026-09-03 | 不引入 MQ、对象存储、独立调度中心、搜索引擎和微服务 | 异步任务使用 MySQL 任务表 + Worker 轮询 |
| 2026-09-03 | 排产系统和产能统一按周，不做日能力到周能力换算 | 使用 `weekly_capacity` |
| 2026-09-03 | 真实 Excel 只作为字段和布局参考；经营计划月份读取真实单元格年月 | 支持 2026-12 到 2027-01 跨年 |
| 2026-09-03 | 历史 R013 状态由较新需求裁决为负值归零 | 采用 `MAX(0, ...)` |
| 2026-09-03 | 不创建“滚动一周”按钮和独立系统设置页 | 固定12周横向查看，自动重算开关位于排产页 |
| 2026-09-03 | Maven 3.9+ / Maven Wrapper 纳入后端构建基线 | 所有构建、测试和打包优先使用 `./mvnw` |
| 2026-09-03 | Node.js LTS + npm 纳入前端构建基线 | 通过 `.nvmrc` 或 `package.json` `engines` 固定主版本，CI/Docker 不使用未声明的 `latest` |
| 2026-09-03 | Nginx 暂不作为初期必选组件，不永久排除 | Spring Boot 直接托管前端静态资源；出现 TLS/路由/负载均衡需求时再评估 |
| 2026-09-03 | 超产能硬阻断发布 | 发布 API 前校验超限，有超限则拒绝发布并返回异常和差额；计划员必须先调整再发布 |
| 2026-09-03 | CRM 已出货数据 Phase 1 对接真实接口 | 先实现接口抽象层和 Excel 导入通道作为 fallback，真实 CRM 接口后续提供；TODO 记录待对接点 |
| 2026-09-03 | 用户/权限初始化使用 Flyway 种子数据 | 默认 ADMIN 用户和 5 角色（ADMIN/PLANNER/BUSINESS/WAREHOUSE/READONLY）通过种子 SQL 创建 |
| 2026-09-03 | 本地开发环境：Docker Compose 提供 MySQL 8.x + Redis；Java 21 需安装 | 后端编译和运行需要 JDK 21；本地已有 Docker/Node/npm/MySQL 客户端/Redis 客户端 |

## 待决定事项

- 部署目标环境的 TLS/反向代理入口（09 阶段前确认）
