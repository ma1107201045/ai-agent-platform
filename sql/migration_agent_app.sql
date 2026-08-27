-- ============================================================
-- 增量修复脚本：为已存在的 agent_app 表补齐新列 / 新建 agent_tool 表
-- 适用场景：数据库是用旧版 init.sql 初始化的
--   1) agent_app 表缺少 workflow_json / tool_ids / dataset_ids 列
--      （现象：报错 Unknown column 'tool_ids' in 'field list'）
--   2) 不存在 agent_tool 表（工具模块新增）
--      （现象：报错 Table 'agent_platform.agent_tool' doesn't exist）
-- 执行方式：在 MySQL 客户端（Navicat / DataGrip / mysql CLI）中
--           选中本文件全部内容执行，或直接运行本文件
-- 幂等性：重复执行不会报错
-- ============================================================

USE agent_platform;

-- ------------------------------------------------------------
-- 1. agent_tool 表（新增表，不存在则创建；幂等）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_tool (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    name        VARCHAR(64)  NOT NULL COMMENT '工具名称(模型调用时使用)',
    description VARCHAR(512) DEFAULT NULL COMMENT '工具描述(给模型理解用途)',
    type        VARCHAR(16)  DEFAULT 'http' COMMENT '类型: http/code',
    url         VARCHAR(512) DEFAULT NULL COMMENT 'HTTP工具: 请求地址',
    method      VARCHAR(8)   DEFAULT 'GET' COMMENT 'HTTP工具: 请求方式',
    headers     TEXT         DEFAULT NULL COMMENT 'HTTP工具: 请求头(JSON)',
    auth_type   VARCHAR(16)  DEFAULT 'none' COMMENT '鉴权: none/bearer/basic',
    auth_token  VARCHAR(512) DEFAULT NULL COMMENT 'Bearer Token',
    parameters  TEXT         DEFAULT NULL COMMENT '参数JSON Schema(JSON)',
    code        TEXT         DEFAULT NULL COMMENT '代码工具: MVEL脚本',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB COMMENT 'Agent工具注册表';

-- 示例工具（仅在表为空时插入，避免重复）
INSERT INTO agent_tool (tenant_id, name, description, type, code, parameters)
SELECT 1, 'get_current_time', '获取当前日期时间字符串', 'code',
       'return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())',
       '{"type":"object","properties":{}}'
WHERE NOT EXISTS (SELECT 1 FROM agent_tool WHERE name = 'get_current_time');

INSERT INTO agent_tool (tenant_id, name, description, type, code, parameters)
SELECT 1, 'text_stats', '统计文本的长度、单词数和行数', 'code',
       'var t = input != null ? String.valueOf(input) : ""; return "字符数=" + t.length() + ", 单词数=" + (t.trim().isEmpty() ? 0 : t.trim().split("\\\\s+").length) + ", 行数=" + t.split("\\n").length',
       '{"type":"object","properties":{}}'
WHERE NOT EXISTS (SELECT 1 FROM agent_tool WHERE name = 'text_stats');

-- ------------------------------------------------------------
-- 2. agent_app 表补齐缺失列（幂等）
-- ------------------------------------------------------------

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tbl
          AND COLUMN_NAME = col
    ) THEN
        SET @ddl = ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 编排草稿 DSL（JSON）
CALL add_column_if_missing('agent_app', 'workflow_json',
    'ALTER TABLE agent_app ADD COLUMN workflow_json LONGTEXT DEFAULT NULL COMMENT ''编排草稿DSL(JSON)'' AFTER status');

-- 关联工具 ID 列表（JSON 数组）
CALL add_column_if_missing('agent_app', 'tool_ids',
    'ALTER TABLE agent_app ADD COLUMN tool_ids VARCHAR(512) DEFAULT NULL COMMENT ''关联工具ID列表(JSON数组)'' AFTER workflow_json');

-- 关联数据集 ID 列表（JSON 数组）
CALL add_column_if_missing('agent_app', 'dataset_ids',
    'ALTER TABLE agent_app ADD COLUMN dataset_ids VARCHAR(512) DEFAULT NULL COMMENT ''关联数据集ID列表(JSON数组)'' AFTER tool_ids');

DROP PROCEDURE IF EXISTS add_column_if_missing;
