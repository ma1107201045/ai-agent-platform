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
    published_version_id BIGINT       DEFAULT NULL COMMENT '当前发布版本ID',
    create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT '智能体应用';

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
