# 服务脚手架模板 · 设计定稿

| 项 | 内容 |
|---|---|
| 文档版本 | v2.1（个人使用版 · 双端） |
| 状态 | 定稿，作为实施依据 |
| 更新日期 | 2026-11-06 |
| 定位 | **个人自用的工具站骨架**：一个后端 API + 一个前端工程（公开展示页 + 登录管理端） |

> v1.0 按团队项目尺度写（RBAC 五表、动态路由、双令牌、Spring Security 全家桶），属于过度设计，已整体瘦身。v2.1 起补充双端结构。详见第 12 章变更记录。

---

## 1. 定位与边界

### 1.1 这个模板解决什么

把每个新项目都要重写一遍的**枯燥部分**一次性做完，之后只管写业务。成品是一个「工具站」：

- **展示端（公开）**：工具入口墙，按分类分组的卡片，点进去用
- **管理端（登录）**：维护工具清单——新增、编辑、删除、上下架、排序
- **后端**：统一响应、全局异常、参数校验、分页、登录鉴权、Swagger、表结构迁移
- **规则**：详细中文注释、命名与分层约定

判断标准：**这份代码如果我自己重写要花半小时以上，就放进模板；否则不放。**

### 1.2 双端的分界

| | 展示端 | 管理端 |
|---|---|---|
| 访问 | 任何人 | 需登录 |
| 路径 | `/` | `/admin/**` |
| 接口 | `/api/portal/**`（公开） | `/api/admin/**`（需令牌） |
| 数据 | 只读，只看到上架的工具 | 全部，含下架的 |
| 布局 | 顶部导航 + 卡片网格 | 左侧栏 + 内容区 |

演示流程：打开首页看到工具卡片 → 点「管理」进登录页 → 登录后维护工具 → 回首页看到变化。

### 1.3 明确不做

考虑到使用者只有我一个人、部署在一两台机器上、没有第二个人看代码：

权限体系（角色/菜单/按钮级鉴权）、动态路由、多令牌刷新、缓存中间件、消息队列、对象存储、多租户、代码生成器、定时任务、WebSocket、国际化、部门组织树、数据字典、操作日志、CI/CD、Docker 编排、暗色主题、Excel 导出。

### 1.4 不追求的东西

- **不追求"功能齐全"**：模板越大，半年后自己都看不懂，还不敢升级
- **不追求"架构正确"**：分层够用就行，不为未来可能的拆分预留抽象
- **不追求"生产级"**：单人使用，不做高可用、不做灰度、不做多实例一致性

---

## 2. 技术栈

| 层 | 选型 | 版本 | 备注 |
|---|---|---|---|
| JDK | Temurin | 21 | 当前机器只有 JDK 8，需另装 |
| 构建 | Maven | 3.9.x | 现有可用 |
| 框架 | Spring Boot | 4.1.x | 当前唯一有 OSS 支持的稳定线 |
| 持久 | MyBatis-Plus | 3.5.x | starter 名必须带 `4`：`mybatis-plus-spring-boot4-starter` |
| 数据库 | MySQL | 8.0.46（本机） | 直连本机实例，不跑 Docker |
| 迁移 | Flyway | 随 Boot 管理 | 需额外引 `flyway-mysql` |
| 密码 | spring-security-crypto | 随 Boot 管理 | 只取 `BCryptPasswordEncoder`，不引 Security 框架 |
| 令牌 | JJWT | 0.12.x | `jjwt-api` + `jjwt-impl` + `jjwt-jackson` |
| 文档 | springdoc-openapi | S1 实测确定 | ⚠️ 唯一不确定项 |
| 前端 | Vue 3.5 + Vite + TS | | 包管理用 **npm**，不折腾 pnpm |
| UI | Element Plus | | 两套布局共用同一个组件库 |
| 状态/路由 | pinia + vue-router 4 | | 路由写死，不做动态生成 |

**后端依赖（10 个）**

```
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-actuator          # 只要 health
mybatis-plus-spring-boot4-starter
mysql-connector-j
flyway-core + flyway-mysql
spring-security-crypto                # 仅 BCrypt
jjwt-api / jjwt-impl / jjwt-jackson
springdoc-openapi-starter-webmvc-ui
lombok（可选，不用就删）
```

**已排除**：`spring-boot-starter-security`（用拦截器替代）、`spring-boot-starter-cache` + caffeine（直接查库）、任何 MQ / 存储 SDK、EasyExcel（导出砍了）。

### 2.1 已知风险

| 风险 | 对策 |
|---|---|
| springdoc 对 Boot 4 的支持未确认 | **S1 第一件事验证**；不行降级为 Knife4j 或手写 Markdown 文档 |
| MyBatis-Plus 的 Boot 4 starter 较新 | S1 跑通"启动 + 一次查询"即可确认 |
| 本机无 Docker | 不写 Dockerfile，部署方式就是 `java -jar` |

---

## 3. 目录结构

```
ServiceTemplate/
├── backend/
├── frontend/
├── scripts/
│   └── init-db.sql              # 建库建账号，root 执行一次
├── docs/
│   ├── DESIGN.md                # 本文档
│   └── conventions.md           # 命名与注释速查
└── README.md                    # 3 分钟跑起来
```

没有 `deploy/` 目录。部署就是 `mvn package` 出一个 jar、`npm run build` 出一堆静态文件，扔到服务器上跑，具体写法进 README 即可。

**一个前端工程，两套布局**，不拆成两个工程。两个工程意味着两份依赖、两套 vite 配置、两次构建、两个部署目录；当前规模下这些成本换不来收益。将来真要拆，把 `views/portal` 和 `views/admin` 各自搬出去即可。

### 3.1 后端包结构

根包 `com.touki`（提供重命名脚本替换）。

```
backend/src/main/java/com/example/template/
├── TemplateApplication.java
├── common/                     # 与业务无关的通用件
│   ├── R.java                  # 统一响应体
│   ├── PageResult.java         # 分页出参
│   ├── PageQuery.java          # 分页入参基类
│   ├── ErrorCode.java          # 错误码枚举
│   ├── BizException.java
│   ├── GlobalExceptionHandler.java
│   ├── BaseEntity.java         # 创建/更新时间，自动填充
│   └── UserContext.java        # ThreadLocal 存当前登录用户
├── config/
│   ├── WebConfig.java          # 拦截器注册 + Jackson 配置
│   ├── MybatisPlusConfig.java  # 分页插件 + 字段自动填充
│   └── OpenApiConfig.java
├── auth/
│   ├── JwtUtil.java            # 签发与解析
│   ├── JwtInterceptor.java     # 只拦 /api/admin/**
│   ├── AuthController.java     # login / me / password
│   └── dto/
├── user/
│   ├── SysUser.java · SysUserMapper.java · SysUserService.java
│   └── vo/
└── tool/                       # 工具（本项目的核心业务）
    ├── Tool.java
    ├── ToolMapper.java
    ├── ToolService.java
    ├── ToolController.java     # /api/admin/tools
    ├── PortalController.java   # /api/portal/tools
    └── dto/ vo/
```

**没有 `modules/` 中间层**。业务模块直接在根包下平铺，一个模块一个包。

**分层职责（单向依赖）**

```
Controller → Service → Mapper → DB
```

- Controller：收参数、调 Service、返回 `R<T>`，不写业务逻辑
- Service：业务逻辑与事务，返回实体或 VO，不返回 `R`
- Mapper：只做数据访问

### 3.2 前端目录结构

```
frontend/src/
├── api/
│   ├── request.ts              # axios 实例 + 拦截器
│   ├── auth.ts
│   └── tool.ts
├── assets/
├── components/
├── layouts/
│   ├── PortalLayout.vue        # 顶部导航 + 内容容器
│   └── AdminLayout.vue         # 侧边栏 + 内容区 + 顶部用户区
├── router/
│   └── index.ts                # 静态路由 + 守卫
├── stores/
│   └── user.ts
├── types/
│   └── index.ts                # Tool、User、分页等类型
├── views/
│   ├── portal/
│   │   └── HomeView.vue        # 工具卡片墙
│   ├── admin/
│   │   ├── LoginView.vue
│   │   ├── ToolListView.vue
│   │   └── ToolFormDialog.vue
│   └── NotFoundView.vue
├── App.vue
└── main.ts
```

---

## 4. 数据库设计

### 4.1 环境

本机 MySQL 8.0.46，服务 `MySQL80`，端口 3306，已运行。dev 直连本机，不跑 Docker。

| 库 | 用途 |
|---|---|
| `template_dev` | 开发 |

字符集 `utf8mb4` / `utf8mb4_0900_ai_ci`。

`scripts/init-db.sql`（root 手动执行一次）：

```sql
CREATE DATABASE IF NOT EXISTS template_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'template_app'@'localhost' IDENTIFIED BY '自行修改';
GRANT ALL PRIVILEGES ON template_dev.* TO 'template_app'@'localhost';
FLUSH PRIVILEGES;
```

### 4.2 命名规范（强制）

> ⚠️ 本机 `my.ini` 有 `lower_case_table_names=1`（Windows 默认）。开发时写 `SELECT * FROM Sys_User` 能跑，但代码搬到 Linux（该值为 0）会直接报表不存在。这个坑不值得踩第二次。

- 库名、表名、字段名、索引名 **一律小写 + 下划线**
- 主键统一 `id`，类型 `BIGINT`（雪花 ID）
- 布尔语义用 `TINYINT` 存 `1`/`0`
- 时间用 `DATETIME`（不用 `TIMESTAMP`）
- 每张表显式 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`

### 4.3 表（两张）

**`sys_user`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 雪花 ID |
| `username` | VARCHAR(50) NOT NULL | 登录名，唯一 |
| `password` | VARCHAR(100) NOT NULL | BCrypt 密文 |
| `nickname` | VARCHAR(50) | 显示名 |
| `status` | TINYINT DEFAULT 1 | 1 正常 / 0 禁用 |
| `create_time` / `update_time` | DATETIME | 自动填充 |

索引：`uk_username (username)`

> 用**物理删除**，唯一索引不需要带 `deleted` 字段，也不存在"删除后重建同名"的冲突——这一刀省掉了一整套边界处理。

**`tool`**（核心业务）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(50) NOT NULL | 工具名称 |
| `description` | VARCHAR(200) | 一句话简介 |
| `icon` | VARCHAR(50) | 图标，直接用 emoji（如 🧰）或 Element Plus 图标名 |
| `link` | VARCHAR(500) NOT NULL | 跳转地址，见下方约定 |
| `category` | VARCHAR(50) DEFAULT '默认' | 分类名，字符串而非外键 |
| `sort` | INT DEFAULT 0 | 排序，升序 |
| `status` | TINYINT DEFAULT 1 | 1 上架（前台可见）/ 0 下架 |
| `create_time` / `update_time` | DATETIME | 自动填充 |

索引：`idx_category_sort (category, sort)`、`idx_status (status)`

**`link` 的两种形态**

- `http://` 或 `https://` 开头 → 外链，前端 `window.open(link, '_blank')`
- `/` 开头 → 站内路径，前端 `router.push(link)`，对应页面需在 `router/index.ts` 里注册

所以「工具入口」既可以是外部网站，也可以是本站的一个功能页（如 JSON 格式化）。后者只需写一个 .vue 再在路由表加一行，机制上不冲突。

**为什么 `category` 用字符串不建分类表**：多一张表就是多一套 CRUD、多一个外键、多一层删除约束，只为换一个下拉框。当前规模下字符串足够——前端拿全量数据后按 `category` 分组渲染，连分类接口都不需要。

### 4.4 迁移

**表结构只由 Flyway 管**，MyBatis-Plus 的自动建表一律关闭。

- 位置：`backend/src/main/resources/db/migration`
- 命名：`V001__init_schema.sql`、`V002__init_data.sql`
- 已有脚本只增不改，改字段一律新增脚本
- 配置：`baseline-on-migrate=true`、`clean-disabled=true`

`V002` 初始化：一个 `admin` 账号（密码 `admin123` 的 BCrypt 密文）+ 8 条示例工具（覆盖多个分类、含外链与站内两种形态、含 1 条下架数据用于验证前台过滤）。

---

## 5. 后端约定

### 5.1 统一响应体

```json
{ "code": 0, "message": "ok", "data": {} }
```

- `code = 0` 成功，非 0 失败
- HTTP 状态码同时保持语义（400 参数错误 / 401 未登录 / 403 无权限 / 500 异常）
- 前端按 `code` 判断业务成败，按 `message` 直接提示（后端返回的就是人话）

**没有** `traceId`、`timestamp` 字段——个人排查问题看日志时间足够。

### 5.2 错误码

一个枚举，够用就行，不做分段：

| code | 含义 |
|---|---|
| 0 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未登录或令牌失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 系统异常 |
| 1001 | 用户名或密码错误 |
| 1002 | 账号已被禁用 |
| 1003 | 原密码不正确 |
| 2001 | 工具不存在 |
| 2002 | 同一分类下工具名称重复 |

新业务模块按 3xxx、4xxx 往后顺延即可。

### 5.3 异常处理

三类，统一由 `GlobalExceptionHandler` 收口：

| 异常 | HTTP | code | 对外消息 |
|---|---|---|---|
| `BizException` | 200 | 自带 | 自带的 message |
| `MethodArgumentNotValidException` | 400 | 400 | 字段错误拼接：`工具名称不能为空` |
| 其他 `Exception` | 500 | 500 | `系统异常，请联系管理员`（堆栈只打日志） |

Controller 里**不写 try-catch**，异常直接抛。

### 5.4 参数校验

- DTO 上用 Jakarta Validation 注解，controller 参数加 `@Valid`
- 修改与新增共用 DTO 时用分组校验（`@Validated(Update.class)`）
- **校验消息必须写中文**：`@NotBlank(message = "工具名称不能为空")`
- `link` 字段用 `@Pattern` 校验：必须以 `http://`、`https://` 或 `/` 开头

### 5.5 分页

管理端列表分页。入参继承 `PageQuery`（`pageNum` 默认 1、`pageSize` 默认 10，上限 200，超出静默截断）。

出参：

```json
{ "list": [], "total": 128, "pageNum": 1, "pageSize": 10 }
```

用 MP 的 `PaginationInnerInterceptor`，禁用字符串拼 `LIMIT`。

展示端不分页——工具总量就几十个，一次全返回，前端分组渲染即可。

### 5.6 登录与鉴权

**单令牌，7 天有效，不做刷新。**

```
POST /api/auth/login  → { token, user: { id, username, nickname } }
```

- JWT payload 只放 `userId`、`username`、`exp`
- 前端把 token 存 `localStorage`，每次请求带 `Authorization: Bearer {token}`
- `JwtInterceptor` **只拦截 `/api/admin/**`**；`/api/auth/login` 与 `/api/portal/**` 全部放行，Swagger 相关路径也放行
- 校验通过 → 用户 ID 放入 `UserContext`（ThreadLocal），`afterCompletion` 里 `remove()`
- 校验失败 → 抛 `BizException(401)`，由全局异常处理器返回统一 JSON

**不做的**：刷新令牌、令牌黑名单、多设备管理、角色鉴权。

**密码**：`BCryptPasswordEncoder`，来自 `spring-security-crypto`（独立小包，不含过滤器链）。

> 拦截器按前缀判断而不是全量拦截，是因为展示端必须匿名可用。这条边界改动时最容易出错：新增 `/api/portal/**` 下的接口不需要任何配置，新增 `/api/admin/**` 下的接口自动受保护。

### 5.7 JSON 序列化

在 `WebConfig` 里统一配置，不在各处加注解：

| 规则 | 原因 |
|---|---|
| `Long` → 字符串 | JS Number 只精确到 2^53，雪花 ID 必丢精度 |
| `LocalDateTime` → `yyyy-MM-dd HH:mm:ss` | 统一观感 |
| `null` 字段保留 | 前端拿到稳定字段集 |

### 5.8 日志

- 用 SLF4J + Logback 默认配置，不写 `logback-spring.xml`
- 生产默认 INFO，MP 的 SQL 日志通过 `log-impl` 开关控制，默认关
- 密码、令牌**禁止打印**

### 5.9 接口文档

springdoc + Swagger UI，路径 `/swagger-ui.html`。

- 每个 Controller 加 `@Tag`，每个方法加 `@Operation(summary = "中文说明")`
- 认证方式标注 Bearer Token
- 生产环境通过配置关掉

---

## 6. 前端约定

### 6.1 路由（静态，写死）

```ts
/                    → PortalLayout → portal/HomeView      公开
/admin/login         → admin/LoginView                     公开
/admin               → AdminLayout
  └─ /admin/tools    → admin/ToolListView                  需登录
/404                 → NotFoundView
```

**加一个站内工具页**的完整步骤（三步，不涉及配置）：写 `views/portal/JsonToolView.vue` → 路由表加一行 → 管理端新增一条工具、`link` 填 `/json-tool`。

**不做动态路由**：改菜单就改一行代码，不值得为此维护菜单表和接口。

### 6.2 路由守卫

只拦 `/admin` 开头（除 `/admin/login`）：

```
有 token → 放行
无 token → 跳 /admin/login?redirect=当前路径，登录后跳回
```

已登录还访问 `/admin/login` → 直接跳 `/admin/tools`。展示端不做任何拦截。

### 6.3 请求封装

`api/request.ts` 一个 axios 实例：

1. 请求拦截：从 `localStorage` 取 token 塞进 `Authorization`
2. 响应拦截：`code !== 0` → `ElMessage.error(message)` 并 reject
3. **401** → 清 token、跳 `/admin/login?redirect=当前路径`
4. 超时 10 秒，失败提示"网络异常"

没有令牌刷新队列——单令牌方案下 401 就是登录过期，直接跳登录页。

### 6.4 两套布局

**PortalLayout**：顶部一条窄导航（左侧站名，右侧一个不显眼的「管理」入口），主体是内容区，底部一行简单页脚。不折叠、不吸顶、不做主题切换。

**AdminLayout**：左侧固定侧边栏（菜单数组渲染，当前只有「工具管理」一项）+ 右侧内容区，顶部右侧显示昵称与「退出登录」。侧边栏可折叠（占地方时有用），其余不做。

### 6.5 展示页要点

- 一次性拉全量上架工具，前端按 `category` 分组，每组一个标题 + 卡片网格
- 卡片：图标 + 名称 + 简介，整卡可点
- 顶部搜索框：**纯前端过滤**（按名称/简介包含），不打接口
- 空状态：没有工具时显示一句提示，不搞插画

### 6.6 状态管理

只有一个 store：

| store | 内容 |
|---|---|
| `user` | token、用户信息、`login()` / `logout()` |

不做 `permission`、`app` 两个 store。

### 6.7 环境与代理

- `vite.config.ts` 代理 `/api` → `http://localhost:8080`
- 前端代码里不出现后端域名
- 生产由 nginx 反代，配置写在 README 里

### 6.8 工程化

只保留两个：**ESLint（flat config）+ Prettier**。

不做 husky、lint-staged、commitlint——自己写代码不需要提交卡口。TS 开 `strict`，不用 `any`。不做 mock。

---

## 7. 接口清单

统一前缀 `/api`，不加版本号。

### 7.1 公开（无令牌）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/portal/tools` | 全部上架工具，按 category、sort 排序 |

返回原始数组（不分页、不分组），前端自行分组。

### 7.2 认证

| 方法 | 路径 | 令牌 |
|---|---|---|
| POST | `/api/auth/login` | 无 |
| GET | `/api/auth/me` | 需要 |

### 7.3 管理端（均需令牌）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/tools` | 分页 + 按名称/分类/状态筛选 |
| GET | `/api/admin/tools/{id}` | 详情 |
| POST | `/api/admin/tools` | 新增 |
| PUT | `/api/admin/tools/{id}` | 修改 |
| DELETE | `/api/admin/tools/{id}` | 删除 |
| PATCH | `/api/admin/tools/{id}/status` | 上下架切换 |
| PUT | `/api/auth/password` | 修改自己的密码 |

---

## 8. 中文注释规范（强制）

> 硬性要求：**没有注释的代码等同于没写完。** 这部分不因项目规模缩小而放松——单人项目半年后回头看，注释是唯一的记忆。

### 8.1 必须写注释的位置

| 位置 | 要求 |
|---|---|
| 类 / 接口 / 枚举 | Javadoc 块：职责、使用场景、关键约束 |
| 所有 `public` 方法 | Javadoc：`@param`、`@return`、`@throws` |
| 复杂私有方法 | 说明思路或算法 |
| 业务分支 | 行内注释，说明**为什么**这么判断 |
| 常量 / 枚举项 | 行尾注释：含义与取值 |
| Mapper 接口方法与 XML | 说明查询用途与关键条件 |
| 配置项 | 每个非默认项说明作用与取值范围 |
| 前端函数 / 组合式函数 | JSDoc：`@param`、`@returns` |
| 模板中的复杂表达式 | 上方注释说明意图 |
| 正则、魔法数字 | 必须注释 |

### 8.2 写什么

**注释解释"为什么"，代码本身已经说明了"是什么"。**

- ✅ 设计意图、业务规则来源、边界条件、踩过的坑
- ✅ 不直观的取值（"这里必须是 0，因为历史数据如此"）
- ✅ 与外部系统或其他模块的隐式约定
- ❌ `// 设置名称`、`// 循环遍历`（重复代码）
- ❌ 注释与代码不一致（比没有注释更糟，改代码必须同步改注释）

### 8.3 后端示例

```java
/**
 * 分页插件配置。
 * <p>
 * 注意单页上限：前端传多大 pageSize 就查多大，一次误操作可能把整表拉走。
 * 这里统一限制 200，超出静默截断而不报错，避免前端调整分页大小时意外失败。
 */
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
    // 200 是经验值：一屏表格通常不超过 100 行，留一倍余量
    pagination.setMaxLimit(200L);
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
}
```

```java
/**
 * 查询展示端可见的工具列表。
 * <p>
 * 与 {@link ToolController#page} 的区别有两处，改代码时别混用：
 * 1. 只返回 status = 1 的记录，下架工具不能出现在前台
 * 2. 不分页——工具总量很小，一次全返回，前端按 category 分组渲染
 *
 * @return 按 category、sort 升序排列的上架工具
 */
public List<PortalToolVO> listVisible() { ... }
```

### 8.4 前端示例

```ts
/**
 * 按分类把工具列表分组。
 *
 * 用 Map 而不是 reduce 成对象，是为了保留分类首次出现的顺序——
 * 对象在 JS 里虽然也保持插入顺序，但一旦分类名是纯数字（如 "2024"），
 * 会被 JS 引擎按整数键重新排序，卡片顺序就乱了。
 *
 * @param tools 后端返回的全量上架工具（已按 sort 升序）
 * @returns 分组结果，顺序即渲染顺序
 */
export function groupByCategory(tools: Tool[]): Array<{ category: string; items: Tool[] }> { ... }
```

### 8.5 语言

统一简体中文。专有名词、标识符、路径、命令、报错信息保留原文，不翻译不音译。

---

## 9. 命名与编码约定

### 9.1 命名

| 类别 | 规则 | 示例 |
|---|---|---|
| 包名 | 全小写 | `com.touki.tool` |
| 类名 | 大驼峰 | `ToolService` |
| 方法/变量 | 小驼峰 | `pageTools` |
| 常量 | 全大写 + 下划线 | `DEFAULT_PAGE_SIZE` |
| DTO / VO | 业务名 + 后缀 | `ToolCreateDTO`、`PortalToolVO` |
| 数据库字段 | 小写下划线 | `create_time` |
| 前端组件 | 大驼峰 | `ToolFormDialog.vue` |
| 前端页面 | 大驼峰 + `View` | `ToolListView.vue` |
| 组合式函数 | `use` 前缀 | `useUserStore` |

### 9.2 后端

- 方法不超 50 行，Controller 方法不超 10 行
- `@Transactional(rollbackFor = Exception.class)` 必须指定 `rollbackFor`
- 时间一律 `java.time`，禁用 `Date`/`Calendar`
- 集合返回空集合而非 `null`
- VO 投影显式列字段，不写 `SELECT *`

### 9.3 Git

- 分支就 `main` 一个，需要时开 `feature/xxx`
- 提交信息：`<type>: <中文描述>`，如 `feat: 工具列表增加分类筛选`
- type 限 `feat` / `fix` / `docs` / `refactor` / `chore`

---

## 10. 实施计划

四步，每步都能独立跑起来看效果。

| 步骤 | 内容 | 验收标准 |
|---|---|---|
| **S1** 后端骨架 | Maven 工程、Flyway 建表、统一响应、全局异常、MP 配置、Swagger | `mvn spring-boot:run` 启动成功；Swagger 能打开；库里两张表由 Flyway 建好 |
| **S2** 认证与工具接口 | JWT 登录、拦截器、工具 CRUD、上下架、portal 查询 | Swagger 里登录后能跑通 CRUD；不带令牌访问 `/api/admin/tools` 返回 401；`/api/portal/tools` 匿名可取 |
| **S3** 前端 | Vite 工程、请求封装、守卫、展示页、登录页、管理列表 + 表单弹窗 | 浏览器：首页看卡片 → 进管理登录 → 增删改 → 回首页看到变化 |
| **S4** 收尾 | `conventions.md`、README、重命名脚本 | 按 README 能在 3 分钟内跑起来 |

**S1 第一个任务**：验证 springdoc + MyBatis-Plus 的 Boot 4 starter 能不能正常启动。这是唯一有不确定性的地方，先打掉再铺代码。

---

## 11. 运行方式

```bash
# 一次性准备
# 1. 装 JDK 21，设 JAVA_HOME
# 2. root 执行 scripts/init-db.sql
# 3. 复制 application-local.yml.example 为 application-local.yml，填数据库密码

# 后端（8080）
cd backend && mvn spring-boot:run

# 前端（5173）
cd frontend && npm install && npm run dev
```

访问：展示页 `http://localhost:5173/`，管理端 `http://localhost:5173/admin`，接口文档 `http://localhost:8080/swagger-ui.html`，初始账号 `admin` / `admin123`。

**配置分层**：`application.yml`（公共）+ `application-local.yml`（本机密码，**进 .gitignore**）。不搞 dev/prod/local 三件套。

**部署**：`mvn package` 得到 jar，服务器 `java -jar` 跑；前端 `npm run build` 出 `dist/`，nginx 托管 + 反代 `/api`。具体配置写 README。

---

## 12. 变更记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-06 | v1.0 | 初稿（按团队项目尺度：RBAC 五表、动态路由、双令牌、Spring Security、Docker 编排） |
| 2026-11-06 | v2.0 | **按个人使用定位瘦身**：表 6→2、后端文件 ~70→~25、依赖 15→10；删 RBAC 与动态路由、双令牌改单令牌、Security 换拦截器、逻辑删除改物理删除、删缓存与 traceId |
| 2026-11-06 | v2.1 | **双端结构**：展示页（公开）+ 管理端（登录），认证边界改为只拦 `/api/admin/**`；`demo_product` 换成真实业务 `tool` 表；一个前端工程两套布局；砍掉 Excel 导出；实施计划 S1-S3 拆成 S1-S4 |
