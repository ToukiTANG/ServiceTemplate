-- =============================================================================
-- 服务脚手架模板 · 数据库初始化脚本
-- -----------------------------------------------------------------------------
-- 用途：首次搭建环境时，用 root 执行一次，创建数据库与应用账号。
-- 执行：mysql -u root -p < scripts/init-db.sql
--
-- -----------------------------------------------------------------------------
-- 关于「一个业务一个库」
--
-- 本项目约定：每个项目、每个业务分支使用独立的数据库，不复用同一个库。
-- 原因：
--   1. 不同分支的 Flyway 迁移脚本不同，共用一个库会导致校验和不匹配；
--   2. 业务数据互相隔离，删库重建互不影响；
--   3. 将来某个业务要单独迁移/备份时，边界是清晰的。
--
-- 因此这个脚本只是"建一个库"的范例。新增业务时，复制下面几行、改库名即可，
-- 例如 toolbox_dev、blog_dev、erp_dev…… 然后用环境变量指定要连哪个库：
--   $env:DB_NAME='toolbox_dev'; mvn spring-boot:run
--
-- 应用账号 template_app 只需授权一次就能访问所有新建的库；
-- 如果希望每个业务用独立账号，把 GRANT 那行也复制一份改掉即可。
-- -----------------------------------------------------------------------------
--
-- 注意：本脚本只负责"建库"和"建账号"，不建表。
--       表结构由 Flyway 在应用启动时自动创建，见
--       backend/src/main/resources/db/migration/
--       这样做的原因是：表结构需要随代码版本演进，交给迁移工具才有历史可查；
--       而库和账号属于环境准备，一台机器只做一次。
-- =============================================================================

-- 开发库：字符集用 utf8mb4（支持 emoji）
-- 排序规则 utf8mb4_0900_ai_ci 是 MySQL 8 专用，若需兼容 5.7 请改成 utf8mb4_general_ci
CREATE DATABASE IF NOT EXISTS `template_dev`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 应用账号：不要让应用直接用 root 连接。
-- 密码请改成你自己的，并同步填到 backend/src/main/resources/application-local.yml
-- 该文件已在 .gitignore 中，真实密码不会进版本库。
CREATE USER IF NOT EXISTS 'template_app'@'localhost' IDENTIFIED BY 'ChangeMe_123456';

-- 授权：给到所有 template_% 开头的库，这样新增业务库不用每次重新 grant。
-- 如果不想用通配，就改成逐个库授权。
GRANT ALL PRIVILEGES ON `template_dev`.* TO 'template_app'@'localhost';

-- ---- 新增一个业务库时，复制下面三行并改库名 ----
-- CREATE DATABASE IF NOT EXISTS `toolbox_dev`
--     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- GRANT ALL PRIVILEGES ON `toolbox_dev`.* TO 'template_app'@'localhost';

FLUSH PRIVILEGES;

-- 验证：执行完后应当能看到 template_dev 库
-- SHOW DATABASES LIKE 'template%';
