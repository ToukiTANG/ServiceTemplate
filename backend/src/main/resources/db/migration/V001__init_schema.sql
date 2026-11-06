-- =============================================================================
-- V001 · 初始化表结构
-- -----------------------------------------------------------------------------
-- 说明：
--   1. 表结构只由 Flyway 管理，MyBatis-Plus 的自动建表已关闭。改字段请新增
--      V002__xxx.sql，不要修改本文件（已执行的脚本再改会导致校验和不匹配）。
--   2. 所有标识符小写：本机 MySQL 的 lower_case_table_names=1，写大写也能跑，
--      但部署到 Linux（该值为 0）会报表不存在。
--   3. 主键统一 BIGINT 雪花 ID，由应用生成，不用 AUTO_INCREMENT。
--   4. 时间列用 DATETIME 而非 TIMESTAMP：TIMESTAMP 有 2038 年上限，且受时区影响。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 用户表
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- 示例表
--
-- 这张表属于"示例模块"，和 com.touki.demo 包下的代码是一套。
-- 开始写真正业务时，可以把这张表、示例代码、以及示例页面一起删掉：
-- 新增一个 V00X__drop_demo_item.sql 执行 DROP TABLE 即可。
-- -----------------------------------------------------------------------------
CREATE TABLE `demo_item`
(
    `id`          BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '名称',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    `remark`      VARCHAR(200)         DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME             DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 名称唯一，由数据库兜底；业务层的重复校验在 DemoItemService.assertNameUnique
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='示例数据（可删除）';
