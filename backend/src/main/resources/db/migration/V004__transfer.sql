-- =============================================================================
-- V004 · 文件传输（业务表）
-- -----------------------------------------------------------------------------
-- 改造背景：文件不再落在服务器磁盘上，改为前端直传又拍云。
-- 因此「文件是什么」由脚手架提供的 upload_file 表负责，
-- 本表只保存业务属性：提取码、有效期、下载次数。
--
-- 为什么不把文件名、大小也存一份：那会产生两处真相。文件改名、换存储、
-- 迁移历史数据时，两张表的字段就会不一致。需要展示文件名时 join 一下即可。
--
-- 版本号说明：本脚本原先是 V003，与脚手架新增的 V003__upload_file 撞号，
-- 因为两个分支的迁移脚本最终会合并到一起，版本号必须全局唯一。
-- =============================================================================

CREATE TABLE `transfer_file`
(
    `id`             BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    `upload_file_id` BIGINT      NOT NULL COMMENT '关联 upload_file.id，文件信息与存储路径都在那张表',
    `code`           VARCHAR(10) NOT NULL COMMENT '6 位提取码',
    `expire_time`    DATETIME    NOT NULL COMMENT '过期时间，到点后清理任务删除对象与记录',
    `download_count` INT         NOT NULL DEFAULT 0 COMMENT '下载次数',
    `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '1 可用 / 2 已失效',
    `create_time`    DATETIME             DEFAULT NULL COMMENT '创建时间',
    `update_time`    DATETIME             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 提取码唯一，撞码时由 Service 重新生成
    UNIQUE KEY `uk_code` (`code`),
    -- 一个文件只允许挂一条传输记录，避免同一份文件被重复分享后清理混乱
    UNIQUE KEY `uk_upload_file` (`upload_file_id`),
    -- 清理任务按「过期时间 + 状态」扫描
    KEY `idx_expire_status` (`expire_time`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件传输记录';

-- 把文件传输挂到展示页卡片墙上（link 以 / 开头表示站内路由）
INSERT INTO `tool` (`id`, `name`, `description`, `icon`, `link`, `category`, `sort`, `status`, `create_time`,
                    `update_time`)
VALUES (9, '文件传输', '上传文件生成提取码，对方输码下载，到期自动清理', '📦', '/transfer', '实用工具', 5, 1, NOW(), NOW());
