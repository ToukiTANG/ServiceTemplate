# 开发约定

这份文档回答「要加一个新东西，应该改哪些文件」。照着步骤做，代码风格和现有代码就是一致的。

---

## 一、新增一个后端接口

以「给工具加一个复制功能」为例，假设接口是 `POST /api/admin/tools/{id}/copy`。

### 1. 确定分层

| 层 | 文件 | 职责 |
|---|---|---|
| Controller | `tool/ToolController.java` | 接参数、调 Service、返回 `R<T>` |
| Service | `tool/ToolService.java` | 业务逻辑，事务边界 |
| DTO/VO | `tool/dto/`、`tool/vo/` | 入参出参，**不要直接暴露实体** |

### 2. 写 Controller 方法

```java
@PostMapping("/{id}/copy")
@Operation(summary = "复制工具")
public R<ToolVO> copy(@PathVariable Long id) {
    return R.ok(toolService.copy(id));
}
```

约定：

- 路径统一走 `/api/admin/**`（管理端）或 `/api/portal/**`（公开），**不要出现第三种前缀**
- 返回类型永远是 `R<T>`，不要直接返回实体
- 方法上不加 `@PreAuthorize` 之类——认证由拦截器统一处理，Controller 里只关心业务
- 参数校验用 `@Valid` + DTO 上的 Jakarta 注解，不要手写 if 判断

### 3. 写 Service 方法

```java
@Transactional
public ToolVO copy(Long id) {
    Tool source = toolMapper.selectById(id);
    if (source == null) {
        throw new BizException(ErrorCode.TOOL_NOT_FOUND);
    }
    Tool copy = new Tool();
    BeanUtils.copyProperties(source, copy, "id", "createTime", "updateTime");
    copy.setName(source.getName() + " 副本");
    toolMapper.insert(copy);
    return ToolVO.from(copy);
}
```

约定：

- 业务失败抛 `BizException`，不要返回 `null` 或 `false` 让 Controller 猜
- 涉及多表写操作才加 `@Transactional`，单表操作不需要
- 实体转 VO 用 VO 上的静态 `from()` 方法，转换逻辑集中在 VO 里

### 4. 错误码

新增错误码加到 `common/ErrorCode.java`，按区间分类：

| 区间 | 用途 |
|---|---|
| 1xxx | 认证与用户（1001 登录失败、1002 禁用、1003 原密码错） |
| 2xxx | 业务模块（2001 工具不存在、2002 同分类重名） |
| 4xx/5xx | 与 HTTP 状态码对齐的通用错误 |

不要复用已有错误码表达新含义——前端可能已经在按 code 做分支了。

### 5. 前端对接

```ts
// api/tool.ts
export function copyTool(id: string): Promise<Tool> {
  return http.post<Tool>(`/admin/tools/${id}/copy`)
}
```

---

## 二、新增一个站内工具页面

模板自带的「JSON 格式化」就是标准示例，照它做：

### 1. 建页面

```
frontend/src/views/portal/XxxToolView.vue
```

### 2. 注册路由

在 `router/index.ts` 的 `/` 子路由里加一条：

```ts
{
  path: 'tools/xxx',
  name: 'xxx-tool',
  component: () => import('@/views/portal/XxxToolView.vue')
}
```

### 3. 在管理端录入

登录管理端 → 新增工具 → 链接填 `/tools/xxx` → 保存。

展示页会把它渲染成卡片，点击时走前端路由而不是新开标签页。

> 判断规则在 `utils/group.ts` 的 `isExternalLink`：`http(s)://` 开头为外链，`/` 开头为站内。

---

## 三、新增一张数据表

### 1. 写迁移脚本

在 `backend/src/main/resources/db/migration/` 下新建 `V003__xxx.sql`，编号必须连续。

```sql
CREATE TABLE demo_item (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(50)  NOT NULL COMMENT '名称',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '示例表';
```

约定：

- 表名小写下划线，字段小写下划线
- 每张表都有 `id`、`create_time`、`update_time`
- 状态字段用 `TINYINT` 存 0/1，注释里写清楚含义
- 字符集统一 `utf8mb4`（要存 emoji）
- **不要修改已执行的迁移脚本**，改结构就加新版本号

### 2. 建实体

继承 `common/BaseEntity`（它已经处理了创建/更新时间的自动填充）：

```java
@Getter
@Setter
@TableName("demo_item")
public class DemoItem extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Integer status;
}
```

注意用 `@Getter`/`@Setter` 而不是 `@Data`——`@Data` 会生成 `equals`/`hashCode`，对实体来说基本没意义，还会在日志里打出巨长的 `toString`。

### 3. 建 Mapper

```java
@Mapper
public interface DemoItemMapper extends BaseMapper<DemoItem> {
}
```

单表 CRUD 不用写 SQL，MyBatis-Plus 已经提供了。

---

## 四、命名对照表

前后端字段名保持一致，减少心智负担：

| 后端 | 前端 | 说明 |
|---|---|---|
| `id` (Long) | `id` (string) | 雪花 ID 序列化为字符串，防止 JS 丢精度 |
| `createTime` | `createTime` | Jackson 已配置为 `yyyy-MM-dd HH:mm:ss` |
| `pageNum` / `pageSize` | 同 | 分页参数 |
| `list` / `total` | 同 | 分页结果 |

**前端 `types/index.ts` 里的接口定义必须与后端 VO 对齐**。改了后端字段忘了改前端，是最常见的一类 bug——TypeScript 在这里帮不上忙，因为请求响应是运行时数据。

---

## 五、注释规范

完整版在 `docs/DESIGN.md` 第 8 章，这里只列最常踩的三条：

1. **写为什么，不写是什么**
   - ✅ `// 先验密码再验状态，避免通过「账号是否禁用」的提示枚举出有效用户名`
   - ❌ `// 查询用户`

2. **必须注释的位置**：类头、公共方法、魔法值、非常规写法（比如为什么用动态 import）、踩坑规避（比如为什么必须加 `mybatis-plus-jsqlparser`）

3. **不要写变更日志**：`// 2024-01-01 张三修改` 这种交给 git，注释只描述当前代码的意图

---

## 六、提交前检查

```bash
# 后端能编译
cd backend && mvn -q compile

# 前端类型能过
cd frontend && npx vue-tsc --noEmit
```

两条都过，基本就不会有低级错误。

---

## 七、分支合并

```
main                 脚手架：认证、统一响应、分页、示例模块
  └ feature/toolbox  业务：工具站（工具卡片 + 文件传输）
```

### 同步脚手架的改动

```bash
git checkout feature/toolbox
git merge main
```

**只会冲突两个文件**：

| 文件 | 原因 | 处理 |
|---|---|---|
| `README.md` | main 讲脚手架，业务分支讲工具站 | 保留业务分支版本 |
| `docs/DESIGN.md` | 同上，两份设计文档定位不同 | 保留业务分支版本 |

```bash
git checkout --ours README.md docs/DESIGN.md
git add .
git commit
```

这两份文档在两边的写法都成立，git 无法自动裁决，属于**预期内**的冲突。其余文件（`R.java`、`JwtInterceptor.java`、`request.ts`、`application.yml` 等）因为业务分支从未改过，会直接快进合入，不会冲突。

### 两条纪律

1. **不要在 `main` 上写业务代码**。一旦 main 改了 `ErrorCode`、`types/index.ts`、`router/index.ts` 这类两边都有但内容不同的文件，merge 就会产生真正的代码冲突，那时候 `checkout --ours` 就不适用了——会把脚手架的改动整个丢掉。
2. **不要把 `feature/toolbox` merge 回 `main`**，那等于把业务搬进脚手架，分支隔离就失去意义了。

### 什么时候需要合并

只要 `main` 上有了新的通用能力（比如给拦截器加令牌刷新、给 `request.ts` 加失败重试），就 merge 一次。如果 main 只改了文档或注释，合不合都行。
