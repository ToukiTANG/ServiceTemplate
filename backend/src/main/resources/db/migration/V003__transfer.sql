-- =============================================================================
-- V003 · 文件传输
-- -----------------------------------------------------------------------------
-- 新增两张内容：
--   1. transfer_file 表：记录一次文件传输（提取码、大小、过期时间等）
--   2. tool 表插一条记录，让「文件传输」出现在展示页的卡片墙上
--
-- 设计说明：
--   - status 三态：0 上传中 / 1 可用 / 2 已删除。
--     分片传到一半的记录必须能被挡住，否则提取页会显示一个下载不了的文件。
--   - code 是给对方用的提取码，唯一索引保证不会撞码。
--   - storage_path 只存相对路径（相对 app.transfer.storage-path），
--     不存绝对路径：换机器、换挂载点时只需要改配置，不用刷数据。
--   - 用物理删除 + 定时清理文件，不做逻辑删除：
--     文件动辄几百 MB，留着记录却删了文件，反而会造成"记录还在但下不了"的困惑。
-- =============================================================================

CREATE TABLE `transfer_file`
(
    `id`             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `file_name`      VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_size`      BIGINT       NOT NULL COMMENT '文件字节数',
    `file_hash`      VARCHAR(64)           DEFAULT NULL COMMENT 'SHA-256，用于完整性校验与秒传',
    `content_type`   VARCHAR(128)          DEFAULT NULL COMMENT 'MIME 类型',
    `storage_path`   VARCHAR(500) NOT NULL COMMENT '相对存储路径，如 20261107/xxx.bin',
    `code`           VARCHAR(10)  NOT NULL COMMENT '6 位提取码',
    `expire_time`    DATETIME     NOT NULL COMMENT '过期时间',
    `download_count` INT          NOT NULL DEFAULT 0 COMMENT '下载次数',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 上传中 / 1 可用 / 2 已删除',
    `create_time`    DATETIME              DEFAULT NULL COMMENT '创建时间',
    `update_time`    DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 提取码唯一，撞码时由 Service 重新生成
    UNIQUE KEY `uk_code` (`code`),
    -- 清理任务按过期时间扫描
    KEY `idx_expire_status` (`expire_time`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件传输记录';

-- 把文件传输挂到展示页卡片墙上（link 以 / 开头表示站内路由）
INSERT INTO `tool` (`id`, `name`, `description`, `icon`, `link`, `category`, `sort`, `status`, `create_time`,
                    `update_time`)
VALUES (9, '文件传输', '上传文件生成提取码，对方输码下载，到期自动清理', '📦', '/transfer', '实用工具', 5, 1, NOW(), NOW());
