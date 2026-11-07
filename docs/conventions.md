# 开发约定

这份文档回答「要加一个新东西，应该改哪些文件」。照着步骤做，代码风格和现有代码自然一致。

所有例子都以 `order` 模块为参照——它就是用来被抄的。

---

## 一、新增一个后端接口

以「给示例数据加一个复制功能」为例，接口是 `POST /api/admin/orders/{id}/copy`。

### 1. 分层职责

| 层 | 放哪 | 职责 |
|---|---|---|
| Controller | `order/OrderController.java` | 接参数、调 Service、返回 `R<T>` |
| Service | `order/OrderService.java` | 业务逻辑与事务边界 |
| DTO / VO | `order/dto/`、`order/vo/` | 入参出参，**不要直接暴露实体** |

### 2. Controller

```java
@PostMapping("/{id}/copy")
@Operation(summary = "复制")
public R<OrderVO> copy(@PathVariable Long id) {
    return R.ok(itemService.copy(id));
}
```

约定：

- 路径统一放在 `/api/admin/**` 下，自动受拦截器保护；确实需要公开的接口放 `/api/public/**` 并加进白名单
- 返回类型永远是 `R<T>`
- 方法体三行以内，出现 `if` 说明逻辑该挪到 Service
- 参数校验用 `@Valid` + DTO 注解，不手写判断

### 3. Service

```java
@Transactional(rollbackFor = Exception.class)
public OrderVO copy(Long id) {
    Order source = getExisting(id);

    Order copy = new Order();
    copy.setName(source.getName() + " 副本");
    copy.setStatus(source.getStatus());
    copy.setRemark(source.getRemark());

    itemMapper.insert(copy);
    return toVO(copy);
}
```

约定：

- 业务失败抛 `BizException`，不返回 `null` / `false`
- 写操作加 `@Transactional(rollbackFor = Exception.class)`
- 实体转 VO 用私有 `toVO`，Controller 不碰实体
- 先 `getExisting(id)` 确认存在，再动数据

### 4. 错误码

加到 `common/ErrorCode.java`，按区间：

| 区间 | 用途 |
|---|---|
| 1xxx | 认证与用户 |
| 2xxx | 业务模块（示例只占用了 2001/2002） |
| 4xx / 5xx | 与 HTTP 状态码对齐的通用错误 |

不要复用已有错误码表达新含义——前端可能已经按 code 做分支了。

### 5. 前端对接

```ts
// api/item.ts
export function copyItem(id: string): Promise<Order> {
  return http.post<Order>(`/admin/orders/${id}/copy`)
}
```

---

## 二、新增一个前端页面

### 1. 建页面

按归属放：

```
src/views/admin/  管理端页面（登录后可见）
src/views/portal/ 展示端页面（公开）
```

### 2. 注册路由

在 `router/index.ts` 对应父路由的 `children` 里加一条：

```ts
{
  path: 'items/copy',   // 相对路径，不要以 / 开头
  name: 'admin-item-copy',
  component: () => import('@/views/admin/ItemCopyView.vue')
}
```

### 3. 加菜单（仅管理端）

编辑 `layouts/AdminLayout.vue` 的 `menus` 数组：

```ts
const menus = [
  { path: '/admin', title: '示例数据', icon: House },
  { path: '/admin/orders', title: '我的业务', icon: Grid }
]
```

菜单用 `el-menu` 的 `router` 模式，`index` 就是路由路径，点了会自动跳转。

---

## 三、新增一张数据表

### 1. 写迁移脚本

在 `backend/src/main/resources/db/migration/` 下新建 `V003__xxx.sql`，编号必须连续。

```sql
CREATE TABLE order_xxx
(
    id          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    name        VARCHAR(50)  NOT NULL COMMENT '名称',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    create_time DATETIME              DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '示例';
```

约定：

- 表名、字段名全小写下划线
- 每张表都有 `id`、`create_time`、`update_time`
- 状态字段用 `TINYINT` 存 0/1，注释写清含义
- 字符集 `utf8mb4`
- **不要修改已执行的迁移脚本**，改结构就加新版本号

### 2. 建实体

```java
@Getter
@Setter
@TableName("order_xxx")
public class OrderXxx extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Integer status;
}
```

`BaseEntity` 已经处理了创建/更新时间自动填充。用 `@Getter/@Setter` 而不是 `@Data`——`@Data` 会生成基于全部字段的 `equals/hashCode`，实体这样做没意义还有坑。

### 3. 建 Mapper

```java
@Mapper
public interface OrderXxxMapper extends BaseMapper<OrderXxx> {
}
```

单表 CRUD 不用写 SQL。

---

## 四、命名对照

前后端字段名保持一致：

| 后端 | 前端 | 说明 |
|---|---|---|
| `id` (Long) | `id` (string) | 雪花 ID 序列化为字符串，防 JS 丢精度 |
| `createTime` | `createTime` | 已配置为 `yyyy-MM-dd HH:mm:ss` |
| `pageNum` / `pageSize` | 同 | 分页参数 |
| `list` / `total` | 同 | 分页结果 |

**`types/index.ts` 里的接口定义必须与后端 VO 对齐**。改了后端字段忘了改前端，是最常见的一类 bug——TypeScript 在这里帮不上忙，因为请求响应是运行时数据。

---

## 五、注释规范

完整版在 `docs/DESIGN.md` 第 8 章，最常踩的三条：

1. **写为什么，不写是什么**
   - ✅ `// 先验密码再验状态，避免通过「账号是否禁用」的提示枚举出有效用户名`
   - ❌ `// 查询用户`

2. **必须注释**：类头、公共方法、魔法值、非常规写法、踩坑规避

3. **不写变更日志**：`// 2024-01-01 张三修改` 交给 git，注释只描述当前代码的意图

---

## 六、Git 提交规范

格式：

```
<type>: <中文描述>
```

- `type` 小写，后面跟冒号和空格
- 描述用中文，说清「做了什么」，不加句号
- 多个动作可以用「并」「及」「支持」连接
- 一次提交只做一件事，别把无关改动混进来

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 修复缺陷 |
| `refactor` | 重构（不改变行为） |
| `style` | 样式调整、格式化（不影响逻辑） |
| `docs` | 文档 |
| `chore` | 杂项（依赖升级、忽略文件） |
| `perf` | 性能优化 |
| `test` | 测试 |
| `merge` | 合并分支、同步上游 |

示例：

```
feat: 新增示例数据分页查询与状态切换
fix: 修复列表删除最后一条后停留在空页
refactor: 抽取实体转 VO 的私有方法
chore: 忽略本地 docs 目录
merge: 同步脚手架的响应式布局调整
```

---

## 七、提交前检查

```bash
# 后端能编译
cd backend && mvn -q clean compile

# 前端类型能过
cd frontend && npx vue-tsc --noEmit
```

两条都过，基本不会有低级错误。
