-- =============================================================================
-- V003 · 文件上传记录
-- -----------------------------------------------------------------------------
-- 脚手架提供「文件存储能力」：前端直传对象存储（又拍云），后端只负责签名与记账。
--
-- 为什么要记这张表：前端直传意味着服务器完全不碰文件内容，也就不知道传了什么。
-- 如果不记录，对象存储里会慢慢堆积无人引用的孤儿文件，既占空间又查不出是谁传的。
-- 记录之后可以：
--   1. 按创建时间清理「上传了但业务没接手」的临时文件（status = 0）
--   2. 业务删除时反查 storage_key，同步删对象存储上的文件
--
-- 与业务表的关系：业务表（如 transfer_file）通过 upload_file_id 关联本表，
-- 本表不认识任何业务，只描述「某个文件存在于对象存储的某个位置」。
-- =============================================================================

CREATE TABLE `upload_file`
(
    `id`           BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `file_name`    VARCHAR(255) NOT NULL COMMENT '原始文件名（仅展示用，不参与存储路径）',
    `file_size`    BIGINT       NOT NULL COMMENT '文件字节数',
    `content_type` VARCHAR(128)          DEFAULT NULL COMMENT 'MIME 类型',
    `storage_key`  VARCHAR(500) NOT NULL COMMENT '对象存储中的相对路径，如 2026/11/07/ab12cd34.pdf',
    `file_hash`    VARCHAR(64)           DEFAULT NULL COMMENT '客户端指纹，用于断点续传识别（非 SHA-256）',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 上传中 / 1 可用 / 2 已删除',
    `create_time`  DATETIME              DEFAULT NULL COMMENT '创建时间',
    `update_time`  DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 一个存储位置只对应一条记录，防止同路径被重复登记
    UNIQUE KEY `uk_storage_key` (`storage_key`),
    -- 清理任务按「状态 + 创建时间」扫描孤儿记录
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件上传记录';
