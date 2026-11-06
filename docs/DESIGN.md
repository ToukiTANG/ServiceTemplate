# 服务脚手架 · 设计说明

| 项 | 内容 |
|---|---|
| 文档版本 | v3.0 |
| 适用分支 | `main`（脚手架骨架） |
| 更新日期 | 2026-11-06 |
| 定位 | 个人自用的 Java + Vue 服务骨架，只含基础能力与一个可删除的示例模块 |

> 本文档只讲 `main` 分支的脚手架。基于它实现的完整业务（工具站：公开卡片墙 + 管理端）在
> `feature/toolbox` 分支，那边的设计细节看该分支自己的文档。

---

## 1. 定位与边界

### 1.1 解决什么

把每个新项目都要重写一遍的**枯燥部分**先做完，之后只管写业务：

- 后端：统一响应、全局异常、参数校验、分页、登录鉴权、Swagger、表结构迁移
- 前端：请求封装、401 处理、路由守卫、两套布局（公开 / 管理）、响应式适配
- 一个**极简示例模块**，演示一个功能要从哪几个文件、按什么顺序写

判断标准：**这份代码如果我自己重写要花半小时以上，就放进脚手架；否则不放。**

### 1.2 不包含什么

角色权限、部门组织树、数据字典、多租户、Redis、消息队列、对象存储、定时任务、WebSocket、国际化、代码生成器、Excel 导出、CI/CD、Docker 编排。

这些都属于"具体业务或具体部署形态"，需要时按 `docs/conventions.md` 的方式加。

### 1.3 为什么不引入 Spring Security

本项目的鉴权需求只有一个判断：**这个请求有没有登录**。引入 Security 要配过滤器链、
`AuthenticationEntryPoint`、`AccessDeniedHandler`、`SecurityContext` 传递才能达到同样效果。
现在是一个 `HandlerInterceptor` 加 40 行代码，行为透明、好调试。

只从 `spring-security-crypto` 这个独立小包里取了一个 `BCryptPasswordEncoder`，没有引入框架本体。

将来真需要角色权限，再上 Security 或 Sa-Token，`JwtInterceptor` 可以整体替换，业务代码不受影响。

---

## 2. 技术栈

| 层 | 选型 | 版本 | 备注 |
|---|---|---|---|
| 运行时 | OpenJDK | 21 | Boot 4 最低 17，用 21 |
| 构建 | Maven | 3.9.x | |
| 框架 | Spring Boot | 4.1.2 | |
| 持久 | MyBatis-Plus | 3.5.18.1 | starter 名带 `4`；另需 `mybatis-plus-jsqlparser` |
| 迁移 | Flyway | 随 Boot 管理 | 需 `spring-boot-starter-flyway` |
| 文档 | springdoc-openapi | 3.2.0 | |
| 令牌 | JJWT | 0.12.6 | |
| 密码 | spring-security-crypto | 随 Boot 管理 | 只取 BCrypt |
| 数据库 | MySQL | 8.0+ | |
| 前端 | Vue 3.5 + Vite 8 + TS 5.9 | | |
| 路由 / 状态 | vue-router 5 / pinia 4 | | |
| UI | Element Plus 2.14（按需引入） | | |
| 包管理 | npm | | 项目级 `.npmrc` 指向 npmmirror |

### 2.1 Boot 4 的四个坑（都踩过）

1. **Jackson 3**：包名 `com.fasterxml.jackson` → `tools.jackson`，定制入口是
   `JsonMapperBuilderCustomizer`。危险之处是 jjwt 会把 Jackson 2 带进 classpath，
   旧写法**能编译通过但运行时不生效**。
2. **MyBatis-Plus jsqlparser**：3.5.9 起分页插件依赖拆成可选依赖，缺了报"找不到符号"。
3. **Flyway 自动配置独立成模块**：只加 `flyway-core` 会**启动正常但不建表**，必须 `spring-boot-starter-flyway`。
4. **JDK 21 注解处理器**：javac 不再自动运行类路径上的 processor，pom 里要配 `annotationProcessorPaths`（Lombok）。

---

## 3. 目录结构

```
ServiceTemplate/
├── backend/
│   └── src/main/
│       ├── java/com/touki/
│       │   ├── TemplateApplication.java
│       │   ├── common/           R、ErrorCode、异常、分页、BaseEntity、UserContext
│       │   ├── config/           Web（拦截器/BCrypt/Jackson）、MyBatis-Plus、OpenAPI、DataInitializer
│       │   ├── auth/             JwtUtil、JwtInterceptor、AuthService、AuthController、dto/vo
│       │   ├── user/             SysUser、SysUserMapper、vo/UserVO
│       │   └── demo/             示例模块（可整块删除）
│       └── resources/
│           ├── application.yml           公共配置
│           ├── application-local.yml     本机密码（gitignore）
│           └── db/migration/             V001 建表、V002 示例数据
├── frontend/
│   └── src/
│       ├── api/                  request（拦截器）、auth、item
│       ├── layouts/              PortalLayout（公开）、AdminLayout（登录）
│       ├── router/               路由表与守卫
│       ├── stores/               user
│       ├── types/                与后端 VO 对齐
│       ├── utils/                responsive（断点判断）
│       └── views/                portal/、admin/
├── scripts/                      init-db.sql、rename-project.ps1
└── docs/                         DESIGN.md、conventions.md
```

**分层职责（单向依赖）**：`Controller → Service → Mapper → DB`。

- Controller：收参数、调 Service、返回 `R<T>`，方法体不超过十行
- Service：业务与事务，返回实体或 VO，**不返回 `R`**
- Mapper：只做数据访问

---

## 4. 数据模型

| 表 | 用途 |
|---|---|
| `sys_user` | 用户（id、username、password、nickname、status、时间戳） |
| `demo_item` | 示例（id、name、status、remark、时间戳），可删除 |

约定：

- 主键统一 `BIGINT` 雪花 ID，由应用生成，不用自增
- 所有表都有 `create_time`、`update_time`，由 MyBatis-Plus 自动填充
- 状态字段 `TINYINT` 存 0/1，注释写清含义
- 物理删除，不做逻辑删除（省掉每个查询都要带 `deleted = 0` 的负担）
- 字符集 `utf8mb4`，标识符全小写（`lower_case_table_names=1` 的坑见下）

### 4.1 命名规范（强制）

> Windows 上 MySQL 默认 `lower_case_table_names=1`，写 `SELECT * FROM Demo_Item` 也能跑；
> 部署到 Linux（该值为 0）会直接报表不存在。

- 库名、表名、字段名、索引名 **一律小写 + 下划线**
- 时间列用 `DATETIME`（不用 `TIMESTAMP`，避免 2038 与时间区问题）
- 金额用 `DECIMAL`，禁用 `FLOAT`/`DOUBLE`
- 每张表显式 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`

### 4.2 迁移

表结构**只由 Flyway 管**，MyBatis-Plus 的自动建表已关闭。

- 位置 `resources/db/migration`，命名 `V00X__描述.sql`
- 已执行的脚本只增不改，改结构就加新版本
- `baseline-on-migrate=true`、`clean-disabled=true`（防误清库）

---

## 5. 后端约定

### 5.1 统一响应

```json
{ "code": 0, "message": "ok", "data": {} }
```

- `code = 0` 成功，非 0 失败
- HTTP 状态码同时保持语义（400 参数 / 401 未登录 / 500 异常）
- 前端按 `code` 判断业务，按 `message` 直接提示
- 不带 `traceId`、`timestamp`——个人项目看日志时间足够

### 5.2 错误码

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
| 2001 | 数据不存在（示例） |
| 2002 | 名称已存在（示例） |

新模块从 3xxx 往后顺延。

### 5.3 异常处理

| 异常 | HTTP | code | 对外消息 |
|---|---|---|---|
| `BizException` | 200 | 自带 | 自带文案 |
| `MethodArgumentNotValidException` | 400 | 400 | 字段错误拼接 |
| 其他 `Exception` | 500 | 500 | 固定兜底文案（堆栈只进日志） |

Controller 里**不写 try-catch**。

### 5.4 参数校验

- DTO 上用 Jakarta Validation，controller 参数加 `@Valid`
- 校验消息写中文
- 新增/修改共用 DTO 时用分组校验

### 5.5 分页

入参继承 `PageQuery`（默认 1/10，`getPageSize()` 里截断到 200），出参 `PageResult`：
`{ list, total, pageNum, pageSize }`。用 `PaginationInnerInterceptor`，禁止手拼 `LIMIT`。

### 5.6 认证

**单令牌，7 天有效，无刷新、无黑名单。**

- JWT payload 只放 `userId`、`username`、`exp`
- `JwtInterceptor` 拦 `/api/**`，白名单只有 `/api/auth/login`
- 校验通过把 userId 放进 `UserContext`（ThreadLocal），`afterCompletion` 里清理
- 校验失败**直接写 401 JSON**，不抛异常——前端靠 HTTP 401 触发跳登录
- 密码 BCrypt

> `afterCompletion` 而不是 `postHandle`：后者在 Controller 抛异常时不执行，
> Tomcat 线程复用会导致下一个请求读到上一个请求的用户 ID。

### 5.7 JSON 序列化

在 `WebConfig` 里统一配置：

| 规则 | 原因 |
|---|---|
| `Long` → 字符串 | JS Number 只精确到 2^53，雪花 ID 必丢精度 |
| `LocalDateTime` → `yyyy-MM-dd HH:mm:ss` | 统一格式 |
| `null` 字段保留 | 前端拿到稳定字段集 |

### 5.8 日志与文档

- SLF4J + Logback 默认配置，不写 `logback-spring.xml`
- MP 的 SQL 日志通过 `log-impl` 开关控制，默认关
- 密码、令牌禁止打印
- `log.info` 前用 `@Slf4j` 生成 `log` 字段，不要 `System.out`

Swagger 在 `/swagger-ui.html`，`@Tag` + `@Operation(summary = "中文")`，
管理端接口类上标 `@SecurityRequirement(name = "bearerAuth")`。

---

## 6. 前端约定

### 6.1 路由

静态路由，写死。加页面 = 建 .vue + 在对应父路由的 `children` 加一条。

守卫只保护 `/admin/**`：未登录跳 `/admin/login?redirect=原路径`，已登录访问登录页送回 `/admin`。

### 6.2 请求封装

`api/request.ts` 一个 axios 实例：

1. 请求拦截注入 `Authorization: Bearer {token}`
2. 响应拦截：`code === 0` 时返回 `data`，否则弹 `ElMessage.error(message)` 并 reject
3. 401 → 清 token、动态 `import('@/router')` 跳登录（避免循环依赖）
4. 超时 15 秒

### 6.3 布局

- `PortalLayout`：公开页面，顶部导航 + 内容区
- `AdminLayout`：管理端，桌面常驻侧边栏；手机隐藏侧边栏改用 `el-drawer`
- 断点统一 **768px**，JS 侧用 `useIsMobile()`，CSS 侧用 `@media (max-width: 767px)`

### 6.4 状态

只有一个 `user` store：token（以 localStorage 为准）、当前用户、`login` / `loadUser` / `logout`。

### 6.5 构建

- Element Plus 按需引入（`unplugin-auto-import` + `unplugin-vue-components`），
  生成的 `auto-imports.d.ts`、`components.d.ts` **要提交**
- `/api` 由 vite proxy 转发到 8080
- `npm run build` = `vue-tsc --noEmit && vite build`

---

## 7. 接口清单

| 方法 | 路径 | 鉴权 |
|---|---|---|
| POST | `/api/auth/login` | 公开 |
| GET | `/api/auth/me` | 需登录 |
| PUT | `/api/auth/password` | 需登录 |
| GET | `/api/admin/items` | 需登录 |
| GET | `/api/admin/items/{id}` | 需登录 |
| POST | `/api/admin/items` | 需登录 |
| PUT | `/api/admin/items/{id}` | 需登录 |
| DELETE | `/api/admin/items/{id}` | 需登录 |
| PATCH | `/api/admin/items/{id}/status?status=` | 需登录 |

`items` 这一组是示例，删掉示例模块时一并删除。

---

## 8. 中文注释规范（强制）

> **没有注释的代码等同于没写完。** 单人项目半年后回头看，注释是唯一的记忆。

### 8.1 必须写注释的位置

| 位置 | 要求 |
|---|---|
| 类 / 接口 / 枚举 | Javadoc：职责、使用场景、关键约束 |
| 所有 `public` 方法 | Javadoc：`@param`、`@return`、`@throws` |
| 复杂私有方法 | 说明思路 |
| 业务分支 | 行内注释说明**为什么**这么判断 |
| 常量 / 枚举项 | 含义与取值 |
| Mapper 方法与 XML | 查询用途与关键条件 |
| 配置项 | 作用与取值范围 |
| 前端函数 / 组合式函数 | JSDoc：`@param`、`@returns` |
| 模板中的复杂表达式 | 说明意图 |
| 正则、魔法数字 | 必须注释 |

### 8.2 写什么

**注释解释"为什么"，代码本身已经说明了"是什么"。**

- ✅ 设计意图、业务规则来源、边界条件、踩过的坑
- ✅ 不直观的取值、与其他模块的隐式约定
- ❌ `// 设置名称`、`// 循环遍历`
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

### 8.4 前端示例

```ts
/**
 * 处理登录态失效。
 *
 * 这里用动态 import 拿 router，而不是在文件顶部 import。
 * 原因：router → store → api → request 已经构成一条链，
 * 如果 request 在顶部再 import router，就形成了循环依赖，
 * 打包时会出现「router 是 undefined」这种只在生产构建才暴露的问题。
 */
async function handleUnauthorized(): Promise<void> { ... }
```

### 8.5 语言

统一简体中文。专有名词、标识符、路径、命令、报错信息保留原文。

---

## 9. 命名与编码约定

| 类别 | 规则 | 示例 |
|---|---|---|
| 包名 | 全小写 | `com.touki.demo` |
| 类名 | 大驼峰 | `DemoItemService` |
| 方法/变量 | 小驼峰 | `pageItems` |
| 常量 | 全大写 + 下划线 | `MAX_PAGE_SIZE` |
| DTO / VO | 业务名 + 后缀 | `DemoItemSaveDTO`、`DemoItemVO` |
| 数据库字段 | 小写下划线 | `create_time` |
| 前端组件 | 大驼峰 | `ItemFormDialog.vue` |
| 前端页面 | 大驼峰 + `View` | `ItemListView.vue` |

后端：方法不超 50 行；`@Transactional` 必须指定 `rollbackFor`；时间用 `java.time`；集合返回空集合而非 null。

---

## 10. 运行与配置

```bash
# 一次性
mysql -u root -p < scripts/init-db.sql
copy backend\src\main\resources\application-local.yml.example application-local.yml   # 填密码

# 后端
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```

**配置分层**：`application.yml`（公共，进版本库）+ `application-local.yml`（本机密码，gitignore）。

**数据库可配**：`url` 里用 `${DB_NAME:template_dev}`，默认 `template_dev`，设环境变量可换库——
每个业务用自己的库，迁移历史互不干扰。

---

## 11. 分支

| 分支 | 内容 |
|---|---|
| `main` | 脚手架骨架（本文档描述的对象） |
| `feature/toolbox` | 基于骨架实现的工具站，完整业务示例 |

脚手架改通用能力在 `main` 做，业务分支按文件同步（`git checkout main -- <文件>`），
不要整分支 merge——两边文档内容不同，每次都会冲突。

---

## 12. 变更记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-06 | v3.0 | 从"工具站设计"中剥离出脚手架部分：数据模型改为 `sys_user` + `demo_item`，接口缩到 9 个，删除双端业务描述；业务设计移交 `feature/toolbox` |
