# 技术栈基线与依赖边界

## 1. 运行中间件

生产运行只依赖：

| 组件 | 是否独立运行 | 用途 |
|---|---:|---|
| MySQL 8.x | 是 | 业务事实、版本、任务、批处理元数据、审计 |
| Redis | 是 | 缓存、进度、幂等和锁辅助 |
| Docker | 容器运行环境 | 打包、启动和隔离 |

Spring Boot API 和 Worker 属于本项目应用，不属于外部中间件。前端构建产物初期由 API 直接托管，不单独运行 Nginx。

Maven 不列入上表，因为它是 Java 项目的构建工具而不是运行时中间件；但 Maven 3.9+ / Maven Wrapper 是强制的后端工程入口，必须提交 Wrapper 文件，并用于开发机、CI 和 Docker 构建。

Node.js LTS + npm 同理属于前端构建工具，不是运行时中间件；必须通过 `.nvmrc` 或 `package.json` `engines` 固定主版本，CI 和 Docker 构建不得使用未声明的 latest。

## 2. 应用内框架和库

- Java 21、Maven 3.9+ / Maven Wrapper、Spring Boot 3.x；
- Spring Web、Validation、Security、Batch、Actuator；
- MyBatis + XML、MySQL JDBC；
- Redisson；
- Apache POI SAX/Event；
- JWT/JOSE 实现；
- Flyway；
- JUnit 5；
- Vue 3、TypeScript、Vite、Element Plus、Vue Router。

这些是随应用编译或打包的库，不增加独立服务。Mimo 不得把它们擅自替换成新的基础设施。

## 3. 默认不使用

- 前端 Axios：原生 fetch 足够；
- Pinia：先使用组件/组合式函数和明确的 feature service；
- MQ：使用 MySQL 任务表；
- 调度中心：使用 Spring `@Scheduled`；
- 对象存储：使用 Docker 文件卷；
- 独立可观测平台：先使用结构化日志和 Actuator；
- Kubernetes：初期不需要；
- Nginx：初期不需要；Spring Boot 直接托管前端静态资源。出现 TLS 终止、域名/路径路由、静态资源性能瓶颈或多实例负载均衡需求时，按变更控制评估加入。

如果部署环境没有现成的云负载均衡、Ingress 或统一网关，而系统需要直接对公网提供 HTTPS，则必须在上线前补充一个受控的反向代理/TLS 入口；Nginx 是候选实现之一，但不是唯一指定实现。

## 4. 增加条件

只有出现可测量问题且现有方案不能解决时，才考虑新增运行中间件。申请必须提供：数据规模、失败现象、替代方案、引入成本、退出路径和用户确认。Maven Wrapper 属于必须提交的构建工具，不需要按运行中间件审批；新增普通 JavaScript/Java 库仍需按依赖变更规则记录。
