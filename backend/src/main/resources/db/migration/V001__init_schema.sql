-- =============================================================================
-- V001 · 初始化表结构
-- -----------------------------------------------------------------------------
-- 说明：
--   1. 表结构只由 Flyway 管理，MyBatis-Plus 的自动建表已关闭。改字段请新增
--      V002__xxx.sql，不要修改本文件（已执行的脚本再改会导致校验和不匹配）。
--   2. 所有标识符小写：本机 MySQL 的 lower_case_table_names=1，写大写也能跑，
--      但部署到 Linux（该值为 0）会报表不存在。
--   3. 主键统一 BIGINT 雪花 ID，由应用生成，不用 AUTO_INCREMENT
--      （避免分库分表或数据合并时的 ID 冲突）。
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
-- 工具表（本项目的核心业务）
-- -----------------------------------------------------------------------------
CREATE TABLE `tool`
(
    `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `name`        VARCHAR(50)  NOT NULL COMMENT '工具名称',
    `description` VARCHAR(200)          DEFAULT NULL COMMENT '一句话简介，展示在卡片上',
    `icon`        VARCHAR(50)           DEFAULT NULL COMMENT '图标，存 emoji（如 🧰）或 Element Plus 图标名',
    `link`        VARCHAR(500) NOT NULL COMMENT '跳转地址：http(s) 开头为外链，/ 开头为站内路径',
    `category`    VARCHAR(50)  NOT NULL DEFAULT '默认' COMMENT '分类名。用字符串而非外键，省掉分类表的 CRUD',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序，升序，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架（展示页可见）/ 0 下架',
    `create_time` DATETIME              DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一分类下名称唯一。跨分类允许同名（例如"后台"分类和"运维"分类下都有"监控"）
    UNIQUE KEY `uk_name_category` (`name`, `category`),
    -- 展示页按分类分组、组内按 sort 排序，这个联合索引正好覆盖
    KEY `idx_category_sort` (`category`, `sort`),
    -- 展示页只查上架数据
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='工具入口';
