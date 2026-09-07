# MRP 排产系统

## 项目简介

MRP（Material Requirements Planning）排产系统，将线下 Excel 经营计划→排产流程系统化。

**当前阶段**：系统开发准备（Harness 约束已冻结，Phase 1）

技术栈中的 Maven 3.9+ / Maven Wrapper 是后端必需的构建与依赖管理工具，不是运行时中间件。初期运行组件保持精简：API（同时托管 Vue 静态资源）、Worker、MySQL、Redis；Nginx 预留为后续按 TLS、路由或负载均衡需求评估的可选组件。

## 快速开始

1. 查看需求文档：`docs/MRP需求文档v3.0（Phase 1）.md`
2. 查看推荐原型：`prototype/MRP滚动排产可交互原型.html`（可直接在浏览器打开）
3. 了解业务规则：`assets/MRP业务规则库.md`
4. 查看操作说明：`docs/MRP排产页面操作手册.md`
5. 下载导入/排产模板：`templates/`
6. 开始系统开发：先读取 `project.md`，再按 `.harness/runbook.md` 顺序把 Prompt 交给 Mimo

## 项目结构

```
MRP新版/
├── README.md                    # 项目说明（本文件）
├── CLAUDE.md                    # 项目配置
├── docs/                        # 需求文档
│   └── MRP需求文档v3.0（Phase 1）.md
├── prototype/                   # 原型图
│   ├── MRP原型图.html           # 原型图（Ant Design 风格）
│   ├── MRP原型图-飞书风格.html  # 原型图（飞书 + B端 SaaS 风格）
│   ├── MRP原型图-企业级风格.html # 原型图（企业级 ERP 风格，基于旧版 SKILL.md）
│   └── MRP滚动排产可交互原型.html # 推荐：数据可复核、可操作的 12 周滚动排产原型
├── assets/                      # 核心资产文件
│   ├── MRP业务知识库.md         # 已确认的业务事实和规则
│   ├── MRP需求基线.md           # 已确认的需求
│   ├── MRP待确认问题池.md       # 待确认问题
│   ├── MRP业务规则库.md         # 已确认的业务规则
│   ├── MRP变更影响矩阵.md       # 变更影响分析
│   ├── MRP-UAT验收案例库.md     # 验收测试用例
│   └── 2026-08-28.md            # 工作日志
├── mrp-ui-design/               # 前端设计规范
│   └── SKILL.md                 # MRP/ERP 企业管理系统前端设计约束
├── templates/                   # Excel 标准模板
│   ├── MRP经营计划导入模板.xlsx
│   ├── MRP库存快照导入模板.xlsx
│   └── MRP十二周排产计划模板.xlsx
├── scripts/                     # 模板生成脚本
│   └── generate_mrp_templates.py
├── project.md                   # Harness 项目总入口
├── AGENTS.md                    # 代理强制入口
├── .harness/                    # 架构契约、Prompt、门禁和状态
├── .gitignore                   # Git 忽略文件
└── .claude/
    └── memory/                  # 持久化记忆
```

## 核心业务规则

### 排产核心算法

**月可排量公式**：
```
月可排量 = MAX(0, 月需求 − 整机库存 − 已出货数量)
```

**月拆周规则**：
- 默认平均分摊到该月前 3 周
- 第 4 周空出（用于承接下月第 1 份）
- 余数放到第一周，尽量往前期排产

**提前一周滚动**：
- 下个月第 1 周的排产提前到上个月第 4 周承接
- 形成 12 周连续滚动，无空档

这里的“滚动”是排产计算规则，不是页面上的“滚动一周”按钮；页面固定展示当前周起连续 12 周，通过横向滚动查看后续周。

## 角色定义

| 角色 | 人员 | 职责 |
|------|------|------|
| 商务人员 | - | 维护经营计划表 |
| 计划人员 | 肖芳（永惠）、徐燕平（爱培科） | 审核排产、微调 |
| 仓库人员 | 慧琴 | 每日提供库存表 |
| 物料管理人员 | 曹玉琴 | 缺料核算（Phase 2） |
| 管理人员 | 晓燕姐 | 需求决策与审批 |
| 系统管理员 | 王立勋 | 系统开发、配置 |

## 开发阶段

1. **需求与原型阶段**（已完成）：确认规则、模板和交互原型
2. **Harness 设计阶段**（已完成）：冻结架构、契约、Prompt 和门禁
3. **开发实现阶段**（下一步）：按 `.harness/workflow.yaml` 顺序执行
4. **UAT 验收阶段**：基于验收案例库和 Docker 环境测试

## 相关文档

- [需求文档](docs/MRP需求文档v3.0（Phase 1）.md)
- [原型图](prototype/MRP原型图.html)
- [飞书风格原型图](prototype/MRP原型图-飞书风格.html)
- [企业级风格原型图](prototype/MRP原型图-企业级风格.html)
- [滚动排产可交互原型](prototype/MRP滚动排产可交互原型.html)
- [排产页面操作手册](docs/MRP排产页面操作手册.md)
- [业务规则库](assets/MRP业务规则库.md)
- [验收案例库](assets/MRP-UAT验收案例库.md)
- [前端设计规范](mrp-ui-design/SKILL.md)
- [Harness 项目总约束](project.md)
- [Harness 执行 Runbook](.harness/runbook.md)
- [Harness 阶段工作流](.harness/workflow.yaml)

## 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)

---

*最后更新：2026-09-03*
