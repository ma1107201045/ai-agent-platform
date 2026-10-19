-- 迁移（MySQL）：为软删除（回收站）相关表补齐 deleted / deleted_time 列
-- 背景：实体已使用 @TableLogic 逻辑删除，但旧库部分表缺列，查询报 Unknown column 'deleted'
-- 幂等：通过存储过程判断列/索引是否已存在，可重复执行。

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('agent_team',          'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('agent_team',          'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('app_tool',            'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('app_tool',            'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('app_skill',           'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('app_skill',           'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('app_prompt_template', 'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('app_prompt_template', 'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('chat_conversation',   'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('chat_conversation',   'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('data_table',          'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('data_table',          'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
-- 以下 4 张表在多数旧库中已有该列，此处仅作兜底（缺则补，有则跳过）
CALL add_column_if_missing('app_agent',           'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('app_agent',           'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('app_template',        'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('app_template',        'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('knowledge_dataset',   'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('knowledge_dataset',   'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');
CALL add_column_if_missing('sys_announcement',    'deleted',      '`deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0正常 1回收站''');
CALL add_column_if_missing('sys_announcement',    'deleted_time', '`deleted_time` datetime NULL COMMENT ''删除时间''');

DROP PROCEDURE IF EXISTS add_column_if_missing;

-- 同步补建：发布版本号唯一索引（防并发发布产生重复版本号）
DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_missing('app_agent_version', 'uk_app_version', 'UNIQUE INDEX `uk_app_version`(`app_id` ASC, `version` ASC) USING BTREE');

-- 用户角色列（轻量权限体系）：默认普通用户，内置 admin 账号提升为管理员
CALL add_column_if_missing('sys_user', 'role', '`role` varchar(20) NOT NULL DEFAULT ''user'' COMMENT ''角色: admin管理员 user普通用户''');
UPDATE `sys_user` SET `role` = 'admin' WHERE `username` = 'admin' AND `role` = 'user';

DROP PROCEDURE IF EXISTS add_index_if_missing;
