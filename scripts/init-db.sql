-- =============================================================================
-- 服务脚手架模板 · 数据库初始化脚本
-- -----------------------------------------------------------------------------
-- 用途：首次搭建环境时，用 root 账号执行一次，创建数据库与应用账号。
-- 执行：mysql -u root -p < scripts/init-db.sql
--       或在客户端工具里打开本文件执行。
--
-- 注意：本脚本只负责"建库"和"建账号"，不建表。
--       表结构由 Flyway 在应用启动时自动创建，见
--       backend/src/main/resources/db/migration/
--       这样做的原因是：表结构需要随代码版本演进，交给迁移工具才有历史可查；
--       而库和账号属于环境准备，一台机器只做一次。
-- =============================================================================

-- 开发库：字符集用 utf8mb4（支持 emoji，工具图标会用到）
-- 排序规则 utf8mb4_0900_ai_ci 是 MySQL 8 专用，若需兼容 5.7 请改成 utf8mb4_general_ci
CREATE DATABASE IF NOT EXISTS `template_dev`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 应用账号：不要让应用直接用 root 连接。
-- 密码请改成你自己的，并同步填到 backend/src/main/resources/application-local.yml
-- 该文件已在 .gitignore 中，真实密码不会进版本库。
CREATE USER IF NOT EXISTS 'template_app'@'localhost' IDENTIFIED BY 'ChangeMe_123456';

-- 只授予本库权限，不给全局权限，避免误操作影响其他库
GRANT ALL PRIVILEGES ON `template_dev`.* TO 'template_app'@'localhost';

FLUSH PRIVILEGES;

-- 验证：执行完后应当能看到 template_dev 库
-- SHOW DATABASES LIKE 'template_dev';
