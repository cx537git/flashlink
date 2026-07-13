CREATE DATABASE IF NOT EXISTS flash_link CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `flash_link`;

CREATE TABLE `short_link`
(
    `id`           BIGINT        NOT NULL COMMENT '雪花ID主键',
    `short_code`   VARCHAR(16)   NOT NULL COMMENT '短链码',
    `original_url` VARCHAR(2048) NOT NULL COMMENT '原始长链接',
    `click_count`  INT           NOT NULL DEFAULT 0 COMMENT '点击次数',
    `status`       TINYINT       NOT NULL DEFAULT 1 COMMENT '1-有效 0-失效',
    `expire_time`  DATETIME               DEFAULT NULL COMMENT '过期时间',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT       NOT NULL DEFAULT 0 COMMENT '0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_short_code` (`short_code`),
    KEY `idx_expire_time` (`expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;