-- ============================================================
-- 智能体平台数据库初始化脚本 (MySQL 8)
-- 执行方式: mysql -uroot -p < sql/init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS agent_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE agent_platform;

-- ------------------------------------------------------------
-- 用户与租户
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_tenant (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '租户名称',
    code        VARCHAR(32)  NOT NULL COMMENT '租户编码',
    plan        VARCHAR(16)  DEFAULT 'free' COMMENT '套餐: free/pro/enterprise',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB COMMENT '租户';

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) DEFAULT NULL COMMENT '密码(BCrypt)',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    avatar      VARCHAR(512) DEFAULT NULL COMMENT '头像',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT '用户';

-- ------------------------------------------------------------
-- 智能体应用
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS agent_app (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id            BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    name                 VARCHAR(128) NOT NULL COMMENT '应用名称',
    description          VARCHAR(512) DEFAULT NULL COMMENT '描述',
    type                 VARCHAR(32)  DEFAULT 'chatflow' COMMENT '类型: chatflow/workflow/agent',
    icon                 VARCHAR(512) DEFAULT NULL COMMENT '图标',
    welcome_message      TEXT         DEFAULT NULL COMMENT '开场白',
    opening_questions    TEXT         DEFAULT NULL COMMENT '推荐问题(JSON数组)',
    status               TINYINT      DEFAULT 0 COMMENT '状态: 0草稿 1已发布',
    workflow_json        LONGTEXT     DEFAULT NULL COMMENT '编排草稿DSL(JSON)，发布时快照到版本表',
    tool_ids             VARCHAR(512) DEFAULT NULL COMMENT '关联工具ID列表(JSON数组)，agent类型应用使用',
    dataset_ids          VARCHAR(512) DEFAULT NULL COMMENT '关联数据集ID列表(JSON数组)，agent类型应用使用',
    published_version_id BIGINT       DEFAULT NULL COMMENT '当前发布版本ID',
    create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT '智能体应用';
-- 已存在的库执行：ALTER TABLE agent_app ADD COLUMN workflow_json LONGTEXT DEFAULT NULL COMMENT '编排草稿DSL(JSON)' AFTER status;
-- 已存在的库执行：ALTER TABLE agent_app ADD COLUMN tool_ids VARCHAR(512) DEFAULT NULL COMMENT '关联工具ID列表(JSON数组)' AFTER workflow_json;
-- 已存在的库执行：ALTER TABLE agent_app ADD COLUMN dataset_ids VARCHAR(512) DEFAULT NULL COMMENT '关联数据集ID列表(JSON数组)' AFTER tool_ids;

CREATE TABLE IF NOT EXISTS agent_app_version (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    app_id        BIGINT       NOT NULL COMMENT '应用ID',
    version       INT          NOT NULL COMMENT '版本号',
    workflow_json LONGTEXT     DEFAULT NULL COMMENT '工作流图定义(JSON DSL快照)',
    prompt_config TEXT         DEFAULT NULL COMMENT 'Prompt配置(JSON)',
    is_published  TINYINT      DEFAULT 0 COMMENT '是否已发布: 0否 1是',
    created_by    BIGINT       DEFAULT NULL COMMENT '创建人',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_app (app_id)
) ENGINE = InnoDB COMMENT '应用版本';
-- 已存在的库执行：ALTER TABLE agent_app ADD COLUMN tool_ids VARCHAR(512) DEFAULT NULL COMMENT '关联工具ID列表(JSON数组)' AFTER workflow_json;

-- ------------------------------------------------------------
-- Agent 工具（工具注册表）
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

-- 示例工具：当前时间
INSERT INTO agent_tool (tenant_id, name, description, type, code, parameters)
VALUES (1, 'get_current_time', '获取当前日期时间字符串', 'code',
        'return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())',
        '{"type":"object","properties":{}}');
-- 示例工具：文本统计
INSERT INTO agent_tool (tenant_id, name, description, type, code, parameters)
VALUES (1, 'text_stats', '统计文本的长度、单词数和行数', 'code',
        'var t = input != null ? String.valueOf(input) : ""; return "字符数=" + t.length() + ", 单词数=" + (t.trim().isEmpty() ? 0 : t.trim().split("\\\\s+").length) + ", 行数=" + t.split("\\n").length',
        '{"type":"object","properties":{}}');

-- ------------------------------------------------------------
-- 模型
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS model_provider (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    name        VARCHAR(64)  NOT NULL COMMENT '供应商名称',
    type        VARCHAR(32)  NOT NULL COMMENT '类型: openai-compatible/anthropic/...',
    base_url    VARCHAR(512) DEFAULT NULL COMMENT 'API基础地址',
    api_key     VARCHAR(512) DEFAULT NULL COMMENT 'API Key(密文存储)',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '模型供应商';

CREATE TABLE IF NOT EXISTS model_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    provider_id     BIGINT       NOT NULL COMMENT '供应商ID',
    name            VARCHAR(128) NOT NULL COMMENT '模型名(调用时使用)',
    model_type      VARCHAR(32)  NOT NULL COMMENT '类型: llm/embedding/rerank/tts/asr/image',
    context_window  INT          DEFAULT NULL COMMENT '上下文窗口',
    max_tokens      INT          DEFAULT NULL COMMENT '最大输出Token',
    capabilities    VARCHAR(512) DEFAULT NULL COMMENT '能力(JSON数组)',
    status          TINYINT      DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_provider (provider_id)
) ENGINE = InnoDB COMMENT '模型信息';

-- ------------------------------------------------------------
-- 聊天会话与消息
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS chat_conversation (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    user_id     BIGINT       NOT NULL COMMENT '创建用户ID',
    app_id      BIGINT       NOT NULL COMMENT '应用ID',
    title       VARCHAR(128) DEFAULT NULL COMMENT '会话标题',
    mode        VARCHAR(16)  DEFAULT 'direct' COMMENT '对话模式: direct直连模型/workflow运行工作流',
    model_id    BIGINT       DEFAULT NULL COMMENT '使用的模型ID(direct模式)',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0删除 1正常',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_app (user_id, app_id)
) ENGINE = InnoDB COMMENT '聊天会话';

CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT       NOT NULL COMMENT '会话ID',
    role            VARCHAR(16)  NOT NULL COMMENT '角色: user/assistant',
    content         LONGTEXT     DEFAULT NULL COMMENT '消息内容',
    trace_json      LONGTEXT     DEFAULT NULL COMMENT '工作流执行轨迹(JSON数组)',
    tokens          INT          DEFAULT 0 COMMENT 'Token用量',
    status          TINYINT      DEFAULT 1 COMMENT '状态: 0失败 1成功',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation (conversation_id)
) ENGINE = InnoDB COMMENT '聊天消息';

-- ------------------------------------------------------------
-- 知识库
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS knowledge_dataset (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    name            VARCHAR(128) NOT NULL COMMENT '数据集名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '描述',
    embedding_model BIGINT       DEFAULT NULL COMMENT '向量化模型ID',
    chunk_size      INT          DEFAULT 500 COMMENT '分块大小(字符)',
    chunk_overlap   INT          DEFAULT 50 COMMENT '分块重叠(字符)',
    status          TINYINT      DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT '知识库数据集';

CREATE TABLE IF NOT EXISTS knowledge_document (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    dataset_id      BIGINT       NOT NULL COMMENT '数据集ID',
    name            VARCHAR(255) NOT NULL COMMENT '文档名称',
    content         LONGTEXT     DEFAULT NULL COMMENT '原文内容',
    char_count      INT          DEFAULT 0 COMMENT '字符数',
    chunk_count     INT          DEFAULT 0 COMMENT '分块数',
    status          VARCHAR(16)  DEFAULT 'pending' COMMENT '状态: pending/indexing/ready/failed',
    error_msg       VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dataset (dataset_id)
) ENGINE = InnoDB COMMENT '知识库文档';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    dataset_id      BIGINT       NOT NULL COMMENT '数据集ID',
    document_id     BIGINT       NOT NULL COMMENT '文档ID',
    chunk_index     INT          NOT NULL COMMENT '块序号(文档内自增)',
    content         TEXT         NOT NULL COMMENT '块文本',
    vector          LONGTEXT     DEFAULT NULL COMMENT '向量(JSON float数组)',
    char_count      INT          DEFAULT 0 COMMENT '字符数',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dataset (dataset_id),
    KEY idx_document (document_id)
) ENGINE = InnoDB COMMENT '知识库分块';

-- ------------------------------------------------------------
-- 种子数据
-- ------------------------------------------------------------

INSERT INTO sys_tenant (id, name, code, plan, status) VALUES (1, '默认租户', 'default', 'pro', 1);

INSERT INTO sys_user (id, tenant_id, username, password, nickname, status)
VALUES (1, 1, 'admin', 'admin', '管理员', 1);
-- 说明：此处为明文种子密码。系统支持明文兼容：首次登录成功后会自动升级为 BCrypt 密文。
-- 正式环境请勿使用明文，可通过用户管理接口或直接执行下方 SQL 更新为 BCrypt 密文：
-- UPDATE sys_user SET password = '<bcrypt-hash>' WHERE username = 'admin';

-- 示例：DeepSeek 供应商（请替换为自己的 API Key）
INSERT INTO model_provider (id, tenant_id, name, type, base_url, api_key, status)
VALUES (1, 1, 'DeepSeek', 'openai-compatible', 'https://api.deepseek.com/v1', 'sk-xxx', 1);

INSERT INTO model_info (provider_id, name, model_type, context_window, max_tokens, capabilities)
VALUES (1, 'deepseek-chat', 'llm', 64000, 8192, '["function_call","stream"]');

INSERT INTO model_info (provider_id, name, model_type, context_window, max_tokens, capabilities)
VALUES (1, 'deepseek-reasoner', 'llm', 64000, 8192, '["stream"]');
