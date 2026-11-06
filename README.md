# ServiceTemplate · Java + Vue 服务脚手架

一个可以直接拿来用的个人项目骨架。后端 Spring Boot 4 + MyBatis-Plus + Flyway，前端 Vue 3 + Element Plus + TypeScript。

**脚手架只包含基础能力，不包含任何具体业务。** 里面有一个极简的示例模块（一张表 + 一个 CRUD + 一个列表页），作用是演示"一个功能要写哪些文件、按什么顺序写"，开始做真实业务时把它整块删掉即可。

> 想要看一个完整业务长什么样（公开展示页 + 管理端 + 工具卡片墙），切到 `feature/toolbox` 分支。

---

## 包含什么

| 能力 | 说明 |
|---|---|
| 统一响应 | `R<T>`，`code = 0` 成功，前端按 code 判断 |
| 全局异常 | 业务异常 / 校验失败 / 未预期异常三类分流 |
| 参数校验 | Jakarta Validation，消息写中文 |
| 分页 | `PageQuery` / `PageResult`，单页上限 200 |
| 认证 | JWT + 拦截器（不用 Spring Security），BCrypt 密码 |
| 权限边界 | 拦截 `/api/**`，白名单只有登录接口 |
| 数据库迁移 | Flyway，启动自动建表 |
| 接口文档 | springdoc + Swagger UI，带 bearerAuth |
| 前端封装 | axios 实例、token 注入、code 解包、401 跳登录 |
| 前端布局 | 展示端（公开）+ 管理端（登录）两套布局，均已做移动端适配 |
| 示例模块 | `demo_item` 表 + 完整 CRUD + 列表页 + 弹窗 |

## 不包含什么

角色权限、部门、字典、Redis、消息队列、对象存储、定时任务、国际化、多租户、代码生成器。

这些都是"需要时再加"，`docs/conventions.md` 写了怎么加。

---

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 运行时 | OpenJDK | 21 |
| 后端框架 | Spring Boot | 4.1.2 |
| ORM | MyBatis-Plus | 3.5.18.1 |
| 数据库迁移 | Flyway | 随 Boot 管理 |
| 接口文档 | springdoc-openapi | 3.2.0 |
| 认证 | JJWT + BCrypt | 0.12.6 |
| 数据库 | MySQL | 8.0+ |
| 前端框架 | Vue | 3.5.x |
| 构建 | Vite | 8.x |
| 路由 / 状态 | vue-router 5 / pinia 4 | — |
| UI | Element Plus（按需引入） | 2.14.x |
| 语言 | TypeScript | 5.9 |

---

## 目录结构

```
ServiceTemplate/
├── backend/
│   └── src/main/
│       ├── java/com/touki/
│       │   ├── TemplateApplication.java
│       │   ├── common/             统一响应、异常、分页、上下文
│       │   ├── config/             MyBatis-Plus、拦截器、OpenAPI、初始化
│       │   ├── auth/               登录、JWT、密码修改
│       │   ├── user/               用户实体与查询
│       │   └── demo/               示例模块（可整块删除）
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml   本机配置（已 gitignore）
│           └── db/migration/       Flyway 迁移脚本
├── frontend/
│   └── src/
│       ├── api/                    axios 封装与接口定义
│       ├── layouts/                展示端 / 管理端两套布局
│       ├── router/                 路由表与守卫
│       ├── stores/                 pinia
│       ├── types/                  与后端 VO 对应的类型
│       ├── utils/                  响应式断点判断
│       └── views/                  页面
├── scripts/
│   ├── init-db.sql                 建库建账号
│   └── rename-project.ps1          改包名/项目名
└── docs/
    ├── DESIGN.md                   设计说明
    └── conventions.md              开发约定：新增接口/页面/表
```

---

## 快速开始

### 1. 环境要求

- **JDK 21**（Boot 4 最低要求 17，本项目用 21）
- **Maven 3.9+**
- **MySQL 8.0+**
- **Node 20+**（推荐 22）

### 2. 建库

```bash
mysql -u root -p < scripts/init-db.sql
```

脚本会创建数据库 `template_dev`（utf8mb4）和账号 `template_app`。
不用这个账号的话，改 `backend/src/main/resources/application-local.yml` 里的数据源即可。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

首次启动时 Flyway 自动建表并插入 3 条示例数据；`DataInitializer` 检测到用户表为空，会创建默认管理员 `admin / admin123`。

- 接口：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- 接口文档：`http://localhost:8080/swagger-ui.html`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发期 `/api` 由 vite proxy 转发到 8080。

---

## 默认账号

| 用户名 | 密码 |
|---|---|
| `admin` | `admin123` |

首次登录后建议在管理端改掉。

---

## 开始写自己的业务

示例模块涉及这些文件，**可以整体删除**：

```
后端：backend/src/main/java/com/touki/demo/   （7 个类）
      迁移脚本里的 demo_item 建表与初始数据
前端：src/api/item.ts
      src/views/admin/ItemListView.vue
      src/views/admin/ItemFormDialog.vue
      src/types/index.ts 里的 DemoItem 系列类型
路由：src/router/index.ts 里 /admin 的子路由指向
菜单：src/layouts/AdminLayout.vue 的 menus 数组
```

新增自己的模块时，照着示例的结构写一遍即可，步骤见 `docs/conventions.md`。

---

## 改成自己的项目

模板的包名是 `com.touki`、项目名是 `ServiceTemplate`。要改的话：

```powershell
pwsh scripts/rename-project.ps1 -Package com.yourname.yourproject -WhatIf   # 先看
pwsh scripts/rename-project.ps1 -Package com.yourname.yourproject           # 真改
```

改完记得 `mvn clean`，否则 `target/` 里的旧包名 class 会干扰。

---

## 分支说明

| 分支 | 内容 |
|---|---|
| `main` | 脚手架骨架：基础能力 + 极简示例 |
| `feature/toolbox` | 一个完整的实际项目（工具站：公开展示页 + 管理端），基于 main 开发 |

业务分支的改动不会污染脚手架；脚手架修了通用问题，可以 merge 到业务分支。

---

## 已知问题

- Flyway 执行时可能打印 `Cannot convert string '...' from utf8mb4 to utf8mb3` 警告。原因是 JDBC URL 里的 `characterEncoding` 被驱动映射成 utf8mb3，导致含中文的 `COMMENT` 转换告警。配置里已改成 `UTF-8`，但**已执行过的迁移不会重跑**，个别表注释可能缺字，不影响功能。想彻底干净就 drop 库重来。

---

## 文档

- `docs/DESIGN.md` —— 设计说明：分层、数据模型、接口约定、注释规范
- `docs/conventions.md` —— 开发约定：新增接口 / 页面 / 数据表的固定步骤
