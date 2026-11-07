# 本地多分支开发

在 `main`（脚手架）和 `feature/toolbox`（业务）之间来回切换时，需要按固定顺序做几件事。省略任何一步都会碰到看起来毫不相关的报错。

---

## 一、两个数据库

一条纪律：**一个分支一个库，不要共用。**

| 库 | 用于 | 启动后的表 |
|---|---|---|
| `template_scaffold` | `main` | `sys_user` |
| `template_dev` | `feature/toolbox` | `sys_user`、`tool`、`transfer_file` |

原因：两个分支的迁移脚本不同（`V001` 建的是一张表还是两张表）。共用同一个库，切过去启动时会报 **Flyway checksum 不匹配**——因为库里记录的是另一个分支的脚本指纹。

### 一次性准备（用 root 执行）

```powershell
& 'D:\ProgramTools\env\MySQL\bin\mysql.exe' -u root -p -e "CREATE DATABASE IF NOT EXISTS template_scaffold DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS template_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON template_scaffold.* TO 'template_app'@'localhost'; GRANT ALL PRIVILEGES ON template_dev.* TO 'template_app'@'localhost'; FLUSH PRIVILEGES;"
```

表不用手动建，Flyway 会在首次启动时自动创建。

---

## 二、切换步骤

### 切到脚手架

```powershell
git checkout main

cd backend
$env:DB_NAME = 'template_scaffold'
mvn clean spring-boot:run          # ← clean 不能省

cd ..\frontend
npm run dev
```

### 切到业务分支

```powershell
git checkout feature/toolbox

cd backend
$env:DB_NAME = 'template_dev'
mvn clean spring-boot:run          # ← 同样不能省

cd ..\frontend
npm run dev
```

`DB_NAME` 只在当前 PowerShell 窗口有效，换个窗口要重新设。想免掉这一步，就写进 `backend/src/main/resources/application-local.yml`（该文件已 gitignore）。

---

## 三、为什么必须 `mvn clean`

`mvn spring-boot:run` 会先做 `process-resources` 和 `compile`，但**这两个步骤只往 `target/` 里写，不会删掉多余的文件**。切分支后 `target/` 里留着上一个分支的产物，于是：

1. **旧的 `.class` 还在** —— Spring 照样扫描注册，比如在 `main` 上启动却加载了业务分支的 `ToolMapper`，一查库就报 `Table 'xxx.tool' doesn't exist`
2. **旧的迁移脚本还在 `target/classes/db/migration/`** —— Flyway 会执行它，比如在 `main` 上跑了业务分支的 `V003__transfer.sql`，而这个脚本里有 `INSERT INTO tool`

这两种报错都**指向不了真正原因**，看起来像是代码写错了，实际是缓存。

> 同理，改了包名、删了文件、动了迁移脚本之后，都要 `clean`。

---

## 四、报错对照表

| 报错信息 | 真正原因 | 解决 |
|---|---|---|
| `Table 'xxx.tool' doesn't exist`（刚切到 main） | `target/classes` 里有业务分支的 class | `mvn clean` |
| `Failed to execute script V003__transfer.sql` | `target/classes` 里有业务分支的迁移脚本 | `mvn clean` |
| `Validate failed: Migration checksum mismatch` | 连到了另一个分支的库 | 换 `DB_NAME`，或重置该库 |
| `Detected failed migration to version N` | 上一次迁移失败，`flyway_schema_history` 里留了失败记录 | 删掉那一行记录，或整个库重建 |
| `Access denied for user` | `DB_NAME` 指向了没授权的库 | 用 root 补 `GRANT` |
| 页面报 `xxx is not defined`、菜单错乱 | 前端 dev server 跨分支时 HMR 状态残留 | 重启 `npm run dev` |

---

## 五、前端 dev server 的注意事项

切换分支后**建议重启 `npm run dev`**，不要指望 HMR 能处理：

- 跨分支改动的是几十个文件，HMR 容易只更新一部分，出现"菜单是新的、页面是旧的"这种状态
- `.env` 改动（比如品牌名）不会热更新，必须重启才生效
- `components.d.ts` / `auto-imports.d.ts` 是插件生成的，跨分支后会变，重启能保证一致

重启很快（约 1 秒），不值得为省它去排查诡异现象。

---

## 六、切换后的验证清单

切完分支、启动之后，花 30 秒确认一下：

```powershell
# 1. 健康检查
curl.exe -s -o NUL -w "health=%{http_code}`n" http://localhost:8080/actuator/health

# 2. 连的是不是预期的库（看日志里 Flyway 的库名）
Select-String -Path "$env:TEMP\backend.log" -Pattern 'Database: jdbc' | Select-Object -Last 1
```

| 分支 | 期望 |
|---|---|
| `main` | 库是 `template_scaffold`；管理端菜单只有「工作台」；`/api/portal/**` 不存在 |
| `feature/toolbox` | 库是 `template_dev`；菜单有「工具管理」「文件传输」；`/api/portal/tools` 返回 200 |

浏览器打开 `http://localhost:5173/admin`，看左侧菜单是否与分支匹配，是最快的判断方式。

---

## 七、刷新一个库

需要把某个库恢复成干净状态时（数据都是测试数据，丢了不心疼）：

```powershell
& 'D:\ProgramTools\env\MySQL\bin\mysql.exe' -u template_app -p -e "SET FOREIGN_KEY_CHECKS=0; DROP TABLE IF EXISTS flyway_schema_history, sys_user, tool, transfer_file; SET FOREIGN_KEY_CHECKS=1;" template_dev
```

然后 `mvn clean spring-boot:run`，Flyway 会从零重建，`DataInitializer` 会重新创建 `admin / admin123`。

注意：业务分支的 `transfer_file` 记录被清掉后，磁盘上 `D:\data\transfer` 里的文件就成了孤儿——不会自动删除，需要手动清理那个目录。
