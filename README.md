# ServiceTemplate · 工具站

> **分支说明**：本分支（`feature/toolbox`）是基于脚手架实现的**业务项目**——一个工具站，包含公开展示页与管理端。
> 骨架本身在 `main` 分支，只有基础能力和一个极简示例（`demo_item`）。
> 只想拿脚手架开始新项目的话，切到 `main`。

一个可以拿来即用的个人工具站。后端 Spring Boot 4 + MyBatis-Plus + Flyway，前端 Vue 3 + Element Plus + TypeScript。

功能很简单：展示页把常用工具按分类铺成卡片墙，点进去就用；管理端登录后维护这份清单——增删改、上下架、排序。通用能力（认证、分页、异常、迁移、前端封装）都来自 `main` 的脚手架，这边只写业务。

> 刻意没有做多模块、没有做 RBAC、没有引入 Redis 和消息队列。要企业级那套东西的话，这个项目不适合你。

---

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 运行时 | OpenJDK | 21 |
| 后端框架 | Spring Boot | 4.1.2 |
| ORM | MyBatis-Plus | 3.5.18.1 |
| 数据库迁移 | Flyway | Boot 管理（12.4.x） |
| 接口文档 | springdoc-openapi | 3.2.0 |
| 认证 | JJWT + BCrypt | 0.12.6 |
| 数据库 | MySQL | 8.0+ |
| 前端框架 | Vue | 3.5.x |
| 构建 | Vite | 8.x |
| 路由 / 状态 | vue-router 5 / pinia 4 | — |
| UI | Element Plus | 2.14.x |
| 语言 | TypeScript | 5.9 |

---

## 目录结构

```
ServiceTemplate/
├── backend/                        后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/template/
│       │   ├── TemplateApplication.java
│       │   ├── common/             统一响应、异常、分页、上下文
│       │   ├── config/             MyBatis-Plus、拦截器、OpenAPI、初始化
│       │   ├── auth/               登录、JWT、密码修改
│       │   ├── user/               用户实体与查询
│       │   └── tool/               工具业务（示例）
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml   本机配置（已 gitignore）
│           └── db/migration/       Flyway 迁移脚本
├── frontend/                       前端
│   └── src/
│       ├── api/                    axios 封装与接口定义
│       ├── layouts/                展示端 / 管理端两套布局
│       ├── router/                 路由表与守卫
│       ├── stores/                 pinia
│       ├── types/                  与后端 VO 对应的类型
│       ├── utils/
│       └── views/                  页面
├── scripts/
│   ├── init-db.sql                 建库建账号
│   └── rename-project.ps1          改包名/项目名
└── docs/
    ├── DESIGN.md                   设计说明（含注释规范）
    └── conventions.md              开发约定与扩展指南
```

---

## 快速开始

### 1. 环境要求

- **JDK 21**（必须是 21，模板用了 records、sealed 等特性，且 Boot 4 最低要求 17）
- **Maven 3.9+**
- **MySQL 8.0+**
- **Node 20+**（推荐 22）

### 2. 建库

```bash
mysql -u root -p < scripts/init-db.sql
```

脚本会创建：

- 数据库 `template_dev`（utf8mb4）
- 账号 `template_app` / `ChangeMe_123456`（只授权本库）

如果你不想用这个账号，直接改 `backend/src/main/resources/application-local.yml` 里的 `spring.datasource` 即可。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

首次启动时 Flyway 会自动执行迁移：建表、插入 8 条示例工具数据。`DataInitializer` 检测到用户表为空，会创建默认管理员 `admin / admin123`（密码在代码里用 BCrypt 现场加密后写入，不写死在 SQL 里）。

启动后：

- 接口：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- 接口文档：`http://localhost:8080/swagger-ui.html`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发期 `/api` 由 vite proxy 转发到 8080，前端代码里只写相对路径，不需要配环境变量。

---

## 默认账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |

> 这是个人项目模板，账号表里没有角色字段——能登录的就是管理员。首次登录后建议在管理端改掉密码。

---

## 常用命令

```bash
# 后端
cd backend
mvn spring-boot:run            # 启动（默认 local profile）
mvn clean package              # 打包
mvn test                       # 跑测试

# 前端
cd frontend
npm run dev                    # 开发服务器
npm run build                  # 类型检查 + 生产构建
npm run preview                # 预览构建产物
```

---

## 改成自己的项目

模板里所有包名都是 `com.touki`、项目名都是 `ServiceTemplate`。改成你自己的：

```powershell
pwsh scripts/rename-project.ps1 -Package com.yourname.yourproject
```

脚本会做三件事：

1. 把 `backend/src/main/java/com/example/template/` 目录整体移动到新包路径
2. 替换所有 `.java` 文件里的 `package` 与 `import` 声明
3. 同步修改 `pom.xml` 的 `groupId`、`artifactId`、`name`

执行完记得重新 `mvn clean`，否则 target 里的旧 class 会干扰。

---

## 为什么这么设计

几个可能反直觉的决定，理由都写在 `docs/DESIGN.md` 里，这里先说结论：

- **拦截器拦 `/api/**` 全部，而不是只拦 `/api/admin/**`**：新加接口默认是受保护的，忘了加注解不会漏权限；公开接口必须显式加进白名单。
- **只用 2 张表，工具的分类存字符串不建分类表**：个人工具站分类就十来个，建表反而要在列表页做一次关联查询。
- **物理删除，没有逻辑删除**：删了就是删了，管理端有二次确认。逻辑删除会让每个查询都得记得加 `deleted = 0`。
- **单令牌 JWT，没有刷新令牌和黑名单**：7 天有效期，过期就重新登录。个人项目不需要那套复杂度。
- **不引入 Spring Security**：只用了它的 `BCryptPasswordEncoder`，其余用一个拦截器搞定。

---

## 已知问题

- Flyway 执行时可能打印 `Cannot convert string '图标...' from utf8mb4 to utf8mb3` 警告。原因是 JDBC URL 里的 `characterEncoding` 被驱动映射成 utf8mb3，导致含中文的 `COMMENT` 转换告警。已把配置改成 `UTF-8`，但**已经执行过的迁移不会重跑**，所以个别表注释可能缺字。不影响任何功能，想彻底干净就 drop 库重来一次。

---

## 文档

- `docs/DESIGN.md` —— 完整设计说明：数据模型、接口清单、错误码、注释规范
- `docs/conventions.md` —— 开发约定：新增接口 / 新增页面 / 新增表的固定步骤
