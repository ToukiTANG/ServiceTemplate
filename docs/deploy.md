# 部署

目标机器：Ubuntu 24.04 / 2G 内存 / 单机。用 Docker Compose 跑四个容器。

---

## 一、架构

```
                     ┌─────────────────────────────┐
  touki.top ────────►│ nginx (80/443)              │
                     │  ├─ /        → dist 静态文件 │
                     │  └─ /api/**  → app:8080     │
                     └─────────────────────────────┘
                                  │
                     ┌────────────▼────────────┐
                     │ app (Spring Boot, 8080) │
                     └────────────┬────────────┘
                                  │
                     ┌────────────▼────────────┐
                     │ mysql (3306, 不对外)     │
                     └─────────────────────────┘

  file.touki.top ───► 又拍云（文件直传/下载，完全不经过服务器）
```

服务器只处理「JSON 接口 + 200KB 静态文件」，文件流量全在又拍云。

---

## 二、前置准备（一次性）

### 1. DNS

在 touki.top 的域名服务商处添加：

| 类型 | 主机记录 | 值 |
|---|---|---|
| A | `@` | 服务器公网 IP |
| A | `www`（可选） | 服务器公网 IP |
| CNAME | `file` | 又拍云控制台「域名管理」给你的 CNAME |

等解析生效（`ping touki.top` 能看到你的 IP）。

### 2. 又拍云

1. 控制台 → 云存储 → `file-touki` → **域名管理** → 添加 `file.touki.top`
2. 把又拍云给的 CNAME 值填到上表
3. 同一页面 → **HTTPS** → 申请免费证书（TrustAsia DV，一年期，自动续期）
4. 状态变成「已开启」后，用浏览器打开 `https://file.touki.top/任意一个已存在的文件` 验证

> 域名已备案才能绑定国内 CDN。你已备案，这步没问题。

### 3. 服务器

```bash
# 装 Docker（官方源，Ubuntu 24.04）
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 重新登录后生效

# 2G 内存建议加 2G swap，防止构建/MySQL 冷启动时被 OOM killer 杀进程
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 项目目录
sudo mkdir -p /opt/touki && sudo chown $USER:$USER /opt/touki

# 防火墙
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
```

---

## 三、本地构建

服务器只有 2G，跑不了 `mvn package`，所以**在本地构建好再上传**：

```powershell
# 在项目根目录执行
pwsh scripts/build-deploy.ps1
```

脚本做三件事：

1. `mvn -DskipTests clean package` → 把 jar 拷到 `deploy/backend/app.jar`
2. `npm run build` → 把 `dist/` 拷到 `deploy/www/`
3. 打印产物大小

---

## 四、上传

```bash
# 本地执行，用你自己的服务器地址和用户名
scp -r deploy user@服务器IP:/opt/touki
```

或者打包上传（更省带宽，只传需要的）：

```powershell
# 本地
Compress-Archive -Path deploy\* -DestinationPath touki-deploy.zip -Force
scp touki-deploy.zip user@服务器IP:/opt/touki
# 服务器
cd /opt/touki && unzip -o touki-deploy.zip
```

---

## 五、首次启动（含证书申请）

证书申请分两阶段：**先只有 80 端口的配置**，拿到证书后再开 443。原因写在 nginx 配置的注释里——没有证书时 nginx 会因为找不到文件直接启动失败。

### 第 1 步：填环境变量

```bash
cd /opt/touki/deploy
cp .env.example .env
chmod 600 .env
nano .env        # 填 4 个值
```

`JWT_SECRET` 用 `openssl rand -base64 48` 生成。

### 第 2 步：先起 mysql + app

```bash
docker compose up -d mysql
docker compose logs -f mysql     # 等出现 "ready for connections"

docker compose up -d app
docker compose logs -f app       # 等出现 "Started TemplateApplication"
```

第一次启动时 Flyway 会自动建表（V001–V004），`DataInitializer` 会创建 `admin / admin123`。

### 第 3 步：起 nginx（80 端口那版）

```bash
docker compose up -d nginx
curl -I http://touki.top          # 期望 200
```

### 第 4 步：申请证书

```bash
docker compose run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d touki.top \
  --email 你的邮箱 \
  --agree-tos --no-eff-email
```

成功后会打印 `Congratulations! Your certificate and chain have been saved at ...`。

### 第 5 步：换成 443 配置并重启

```bash
cd /opt/touki/deploy
cp nginx/conf.d/touki.top.conf nginx/conf.d/touki.top.bootstrap.bak
cp nginx/templates/touki.top.conf nginx/conf.d/touki.top.conf
docker compose restart nginx
```

验证：

```bash
curl -I https://touki.top          # 期望 200
curl -I http://touki.top           # 期望 301
```

浏览器打开 `https://touki.top`，用 `admin / admin123` 登录管理端。

### 第 6 步：起 certbot 常驻（自动续期）

```bash
docker compose up -d certbot
```

它每 12 小时检查一次，快到期时自动续。**证书有效期 90 天，别只申请不管续期。**

---

## 六、日常更新

```powershell
# 本地：改完代码后重新构建
pwsh scripts/build-deploy.ps1

# 只传变化的部分
scp deploy\backend\app.jar user@服务器:/opt/touki/deploy/backend/
scp -r deploy\www\* user@服务器:/opt/touki/deploy/www/
```

```bash
# 服务器：重启对应容器
cd /opt/touki/deploy
docker compose up -d --build app     # 后端有 jar 变化
docker compose restart nginx         # 前端有 dist 变化
```

数据库结构变更走 Flyway，`app` 重启时自动执行新迁移，不用手动做任何事。

---

## 七、排错

| 现象 | 原因 | 处理 |
|---|---|---|
| `app` 启动后立刻退出 | 连不上 mysql | `docker compose logs mysql` 看是否 ready；`depends_on` 的 healthcheck 是否通过 |
| 502 Bad Gateway | app 没起来或崩溃 | `docker compose logs app` |
| 刷新 /transfer/xxx 返回 404 | nginx 少了 SPA 兜底 | 检查 `try_files $uri $uri/ /index.html` |
| nginx 起不来，日志报 `cannot load certificate` | 用了 443 配置但证书还没申请 | 换回 bootstrap 配置，先申请证书 |
| 页面能开但上传文件报 CORS | 又拍云 CORS 被改过 | 控制台确认 `Access-Control-Allow-Origin` 存在 |
| 文件 URL 打不开（HTTPS 报错） | 又拍云域名证书没配好 | 控制台看 HTTPS 状态 |
| `docker compose up` 报 `no space left on device` | 磁盘满 | `docker system prune -a` 清旧镜像 |
| 服务莫名重启 | 2G 内存 OOM | `dmesg \| grep -i oom` 确认，调小 JVM 或加 swap |

---

## 八、常用命令

```bash
cd /opt/touki/deploy

docker compose ps                    # 状态
docker compose logs -f app           # 跟日志
docker compose restart app           # 重启后端
docker compose exec mysql mysql -u template_app -p template_dev    # 进库
docker stats                         # 实时内存占用
```
