-- =============================================================================
-- V001 · 初始化表结构
-- -----------------------------------------------------------------------------
-- 脚手架只建一张表：sys_user。
--
-- 业务表由各业务分支自己的迁移脚本负责（例如 feature/toolbox 的 V001 里还有 tool 表）。
-- 这样脚手架保持"零业务"，新项目从空库启动时不会多出一张用不到的表。
--
-- 说明：
--   1. 表结构只由 Flyway 管理，MyBatis-Plus 的自动建表已关闭。改字段请新增
--      V00X__xxx.sql，不要修改本文件（已执行的脚本再改会导致校验和不匹配）。
--   2. 所有标识符小写：本机 MySQL 的 lower_case_table_names=1，写大写也能跑，
--      但部署到 Linux（该值为 0）会报表不存在。
--   3. 主键统一 BIGINT 雪花 ID，由应用生成，不用 AUTO_INCREMENT。
--   4. 时间列用 DATETIME 而非 TIMESTAMP：TIMESTAMP 有 2038 年上限，且受时区影响。
-- =============================================================================

CREATE TABLE `sys_user`
(
    `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文，固定 60 字符，留余量到 100',
    `nickname`    VARCHAR(50)           DEFAULT NULL COMMENT '显示名',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 禁用',
    `create_time` DATETIME              DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 用户名唯一。因为采用物理删除，不需要把逻辑删除标记并入唯一键
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';
