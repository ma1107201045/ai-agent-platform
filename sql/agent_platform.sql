/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : localhost:3306
 Source Schema         : agent_platform

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 04/09/2026 17:35:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for app_agent
-- ----------------------------
DROP TABLE IF EXISTS `app_agent`;
CREATE TABLE `app_agent`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'chatflow' COMMENT '类型: chatflow/workflow/agent',
  `icon` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `welcome_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '开场白',
  `opening_questions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '推荐问题(JSON数组)',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态: 0草稿 1已发布',
  `workflow_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '编排草稿DSL(JSON)，发布时快照到版本表',
  `tool_ids` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联工具ID列表(JSON数组)',
  `dataset_ids` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联数据集ID列表(JSON数组)',
  `published_version_id` bigint NULL DEFAULT NULL COMMENT '当前发布版本ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1回收站',
  `deleted_time` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_deleted`(`deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '智能体应用' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_agent
-- ----------------------------
INSERT INTO `app_agent` VALUES (6, 1, '智能客服助手', '基于知识库回答客户问题，支持多轮追问与转人工', 'chatflow', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, '2026-08-28 01:34:35', '2026-08-28 01:34:35', 0, NULL);
INSERT INTO `app_agent` VALUES (8, 1, '多语言翻译助手', '中英互译与术语润色，支持行业术语定制', 'workflow', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, '2026-08-28 01:41:06', '2026-08-28 01:41:06', 0, NULL);
INSERT INTO `app_agent` VALUES (9, 1, '智能客服助手', '基于知识库回答客户问题，支持多轮追问与转人工', 'chatflow', NULL, NULL, NULL, 0, '{\"nodes\":[],\"edges\":[]}', NULL, NULL, NULL, '2026-08-28 01:42:44', '2026-08-28 03:25:50', 0, NULL);

-- ----------------------------
-- Table structure for app_agent_version
-- ----------------------------
DROP TABLE IF EXISTS `app_agent_version`;
CREATE TABLE `app_agent_version`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `version` int NOT NULL COMMENT '版本号',
  `workflow_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '工作流图定义(JSON DSL快照)',
  `prompt_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'Prompt配置(JSON)',
  `is_published` tinyint NULL DEFAULT 0 COMMENT '是否已发布: 0否 1是',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app`(`app_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '应用版本' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_agent_version
-- ----------------------------

-- ----------------------------
-- Table structure for app_api_key
-- ----------------------------
DROP TABLE IF EXISTS `app_api_key`;
CREATE TABLE `app_api_key`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '关联应用ID(app_agent.id)',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密钥名称(用途标识)',
  `key_prefix` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密钥前缀(列表展示用)',
  `key_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密钥SHA-256哈希(十六进制)，明文仅创建/轮换时返回一次',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `expires_at` datetime NULL DEFAULT NULL COMMENT '过期时间(空=永不过期)',
  `rate_limit` int NULL DEFAULT NULL COMMENT '每分钟请求上限(空=不限)',
  `usage_count` bigint NOT NULL DEFAULT 0 COMMENT '累计调用次数',
  `last_used_at` datetime NULL DEFAULT NULL COMMENT '最近使用时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app`(`app_id` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '应用API密钥' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_api_key
-- ----------------------------
INSERT INTO `app_api_key` VALUES (1, 1, 10, '111', 'sk-bf702d41f', '8102bf800ae2f992e2b19604d366f9344bedbdacdbc6cb176e2c968aea24a2c0', 1, NULL, NULL, 0, NULL, NULL, '2026-09-02 17:18:57', '2026-09-02 17:18:57');

-- ----------------------------
-- Table structure for app_prompt_template
-- ----------------------------
DROP TABLE IF EXISTS `app_prompt_template`;
CREATE TABLE `app_prompt_template`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '模板描述',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'general' COMMENT '分类: general通用 system系统 business业务 custom自定义',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板正文(支持{{var}}占位)',
  `variables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '变量定义(JSON数组:[{\"name\":\"var\",\"desc\":\"说明\"}])',
  `version` int NOT NULL DEFAULT 1 COMMENT '当前版本号',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '提示词模板' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_prompt_template
-- ----------------------------
INSERT INTO `app_prompt_template` VALUES (1, 1, '通用结构化助手', '为任意任务设定专业助手的系统提示词模板', 'general', '你是一个专业的智能助手，名叫{{name}}。你的任务目标是：{{task}}。请严格围绕目标展开，若信息不足请向用户澄清而不是臆测。回答要求：1. 结论先行，逻辑清晰；2. 使用用户使用的语言；3. 涉及数据或结论时给出依据。请开始。', '[{\"name\":\"name\",\"desc\":\"助手名称\"},{\"name\":\"task\",\"desc\":\"任务目标\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');
INSERT INTO `app_prompt_template` VALUES (2, 1, '资深专家角色扮演', '让模型扮演指定领域的资深专家（系统内置）', 'system', '你是一位拥有{{years}}年经验的资深{{expertise}}专家。请以该角色身份回答：1. 先给出明确结论，再分条阐述依据；2. 使用准确的行业术语，必要时作通俗解释；3. 超出专业范围时坦诚说明，并给出可靠的获取建议。回答语言请与用户保持一致。', '[{\"name\":\"years\",\"desc\":\"从业年限\"},{\"name\":\"expertise\",\"desc\":\"专业领域\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');
INSERT INTO `app_prompt_template` VALUES (3, 1, '智能客服应答', '基于知识库内容的在线客服应答模板', 'business', '你是{{brand}}的在线客服{{serviceName}}。请基于给定的知识内容回答：{{knowledge}}。回答原则：1. 优先引用知识库，不编造事实；2. 无法解决时引导用户留下联系方式并承诺转人工跟进；3. 语气礼貌简洁。若用户情绪激动，请先安抚情绪再处理问题。', '[{\"name\":\"brand\",\"desc\":\"品牌名称\"},{\"name\":\"serviceName\",\"desc\":\"客服代号\"},{\"name\":\"knowledge\",\"desc\":\"知识库内容\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');
INSERT INTO `app_prompt_template` VALUES (4, 1, '翻译润色助手', '跨语言翻译与润色的模板示例', 'custom', '你是资深翻译与润色专家。请将下方内容从{{srcLang}}翻译为{{tgtLang}}：{{input}}。要求：1. 准确传达原意与语气；2. 译文自然流畅，符合目标语言习惯；3. 保留专有名词、数字与格式；4. 如存在多义，选择最佳译法并附一句简短说明。', '[{\"name\":\"srcLang\",\"desc\":\"源语言\"},{\"name\":\"tgtLang\",\"desc\":\"目标语言\"},{\"name\":\"input\",\"desc\":\"待翻译内容\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');

-- ----------------------------
-- Table structure for app_prompt_version
-- ----------------------------
DROP TABLE IF EXISTS `app_prompt_version`;
CREATE TABLE `app_prompt_version`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `version` int NOT NULL COMMENT '版本号(从1自增)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '该版本模板正文',
  `variables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '该版本变量定义(JSON)',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '版本说明',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_template_version`(`template_id` ASC, `version` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '提示词模板版本快照' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_prompt_version
-- ----------------------------
INSERT INTO `app_prompt_version` VALUES (1, 1, 1, '你是一个专业的智能助手，名叫{{name}}。你的任务目标是：{{task}}。请严格围绕目标展开，若信息不足请向用户澄清而不是臆测。回答要求：1. 结论先行，逻辑清晰；2. 使用用户使用的语言；3. 涉及数据或结论时给出依据。请开始。', '[{\"name\":\"name\",\"desc\":\"助手名称\"},{\"name\":\"task\",\"desc\":\"任务目标\"}]', '初始版本', 1, '2026-09-01 12:00:00');
INSERT INTO `app_prompt_version` VALUES (2, 2, 1, '你是一位拥有{{years}}年经验的资深{{expertise}}专家。请以该角色身份回答：1. 先给出明确结论，再分条阐述依据；2. 使用准确的行业术语，必要时作通俗解释；3. 超出专业范围时坦诚说明，并给出可靠的获取建议。回答语言请与用户保持一致。', '[{\"name\":\"years\",\"desc\":\"从业年限\"},{\"name\":\"expertise\",\"desc\":\"专业领域\"}]', '初始版本', 1, '2026-09-01 12:00:00');
INSERT INTO `app_prompt_version` VALUES (3, 3, 1, '你是{{brand}}的在线客服{{serviceName}}。请基于给定的知识内容回答：{{knowledge}}。回答原则：1. 优先引用知识库，不编造事实；2. 无法解决时引导用户留下联系方式并承诺转人工跟进；3. 语气礼貌简洁。若用户情绪激动，请先安抚情绪再处理问题。', '[{\"name\":\"brand\",\"desc\":\"品牌名称\"},{\"name\":\"serviceName\",\"desc\":\"客服代号\"},{\"name\":\"knowledge\",\"desc\":\"知识库内容\"}]', '初始版本', 1, '2026-09-01 12:00:00');
INSERT INTO `app_prompt_version` VALUES (4, 4, 1, '你是资深翻译与润色专家。请将下方内容从{{srcLang}}翻译为{{tgtLang}}：{{input}}。要求：1. 准确传达原意与语气；2. 译文自然流畅，符合目标语言习惯；3. 保留专有名词、数字与格式；4. 如存在多义，选择最佳译法并附一句简短说明。', '[{\"name\":\"srcLang\",\"desc\":\"源语言\"},{\"name\":\"tgtLang\",\"desc\":\"目标语言\"},{\"name\":\"input\",\"desc\":\"待翻译内容\"}]', '初始版本', 1, '2026-09-01 12:00:00');

-- ----------------------------
-- Table structure for asset_file
-- ----------------------------
DROP TABLE IF EXISTS `asset_file`;
CREATE TABLE `asset_file`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '素材名称(展示用)',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原始文件名',
  `ext` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扩展名(小写不带点)',
  `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MIME类型',
  `size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `category` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'other' COMMENT '分类: image/document/audio/video/other',
  `storage_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储相对路径(实际文件位于 upload-dir 下)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0删除 1正常',
  `created_by` bigint NULL DEFAULT NULL COMMENT '上传用户ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '素材管理-素材文件' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of asset_file
-- ----------------------------

-- ----------------------------
-- Table structure for chat_agent_run
-- ----------------------------
DROP TABLE IF EXISTS `chat_agent_run`;
CREATE TABLE `chat_agent_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id` varchar(48) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '引擎运行标识(全局唯一)',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `conversation_id` bigint NULL DEFAULT NULL COMMENT '会话ID(公开调用为空)',
  `mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'workflow' COMMENT '运行模式: workflow/agent',
  `input` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '用户输入',
  `answer` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '最终回答',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'running' COMMENT '运行状态: running/success/failed/canceled/timeout',
  `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '技术性错误',
  `trace_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '节点轨迹(JSON数组)',
  `cost_ms` bigint NULL DEFAULT 0 COMMENT '总耗时(毫秒)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `finish_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_run_id`(`run_id` ASC) USING BTREE,
  INDEX `idx_app_time`(`app_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_conv_time`(`conversation_id` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工作流运行记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_agent_run
-- ----------------------------
INSERT INTO `chat_agent_run` VALUES (1, 'run_ac85656d37be41a2', 9, NULL, 'workflow', '如何创建一个智能体应用？', '', 'success', NULL, '[]', 5, '2026-09-03 23:00:28', '2026-09-03 23:00:28');

-- ----------------------------
-- Table structure for chat_conversation
-- ----------------------------
DROP TABLE IF EXISTS `chat_conversation`;
CREATE TABLE `chat_conversation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '创建用户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会话标题',
  `mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'direct' COMMENT '对话模式: direct直连模型/workflow运行工作流',
  `model_id` bigint NULL DEFAULT NULL COMMENT '使用的模型ID(direct模式)',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0删除 1正常',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_app`(`user_id` ASC, `app_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '聊天会话' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_conversation
-- ----------------------------
INSERT INTO `chat_conversation` VALUES (4, 1, 1, 9, '如何创建一个智能体应用？', 'workflow', NULL, 0, '2026-09-03 23:00:28', '2026-09-03 23:00:35');

-- ----------------------------
-- Table structure for chat_message
-- ----------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色: user/assistant',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '消息内容',
  `trace_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '工作流执行轨迹(JSON数组)',
  `tokens` int NULL DEFAULT 0 COMMENT 'Token用量',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0失败 1成功',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation`(`conversation_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '聊天消息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_message
-- ----------------------------

-- ----------------------------
-- Table structure for chat_usage
-- ----------------------------
DROP TABLE IF EXISTS `chat_usage`;
CREATE TABLE `chat_usage`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `conversation_id` bigint NULL DEFAULT NULL COMMENT '会话ID(公开调用为空)',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID(公开调用为空)',
  `model_id` bigint NULL DEFAULT NULL COMMENT '模型ID',
  `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'console' COMMENT '来源: console控制台会话/public公开API',
  `mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会话模式: direct/agent/workflow',
  `prompt_tokens` int NOT NULL DEFAULT 0 COMMENT '输入Token',
  `completion_tokens` int NOT NULL DEFAULT 0 COMMENT '输出Token',
  `total_tokens` int NOT NULL DEFAULT 0 COMMENT '总Token(输入+输出)',
  `cost` decimal(14, 6) NOT NULL DEFAULT 0.000000 COMMENT '估算成本(元)，按模型单价计算',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app_time`(`app_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_model_time`(`model_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_conv`(`conversation_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模型用量事件' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_usage
-- ----------------------------

-- ----------------------------
-- Table structure for data_record
-- ----------------------------
DROP TABLE IF EXISTS `data_record`;
CREATE TABLE `data_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `table_id` bigint NOT NULL COMMENT '数据表ID(data_table.id)',
  `data_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '行数据(JSON对象: {列key:值})',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_table`(`table_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据存储-数据记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of data_record
-- ----------------------------

-- ----------------------------
-- Table structure for data_table
-- ----------------------------
DROP TABLE IF EXISTS `data_table`;
CREATE TABLE `data_table`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据表名(展示用，应用内唯一)',
  `label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示名称/别名',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `columns_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '列定义(JSON数组: [{key,label,type,options?}])',
  `row_count` int NOT NULL DEFAULT 0 COMMENT '行记录数',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_name`(`tenant_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据存储-自定义数据表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of data_table
-- ----------------------------

-- ----------------------------
-- Table structure for knowledge_chunk
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_chunk`;
CREATE TABLE `knowledge_chunk`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dataset_id` bigint NOT NULL COMMENT '数据集ID',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `chunk_index` int NOT NULL COMMENT '块序号(文档内自增)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '块文本',
  `vector` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '向量(JSON float数组)',
  `char_count` int NULL DEFAULT 0 COMMENT '字符数',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dataset`(`dataset_id` ASC) USING BTREE,
  INDEX `idx_document`(`document_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '知识库分块' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of knowledge_chunk
-- ----------------------------

-- ----------------------------
-- Table structure for knowledge_dataset
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_dataset`;
CREATE TABLE `knowledge_dataset`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `embedding_model` bigint NULL DEFAULT NULL COMMENT '向量化模型ID',
  `chunk_size` int NULL DEFAULT 500 COMMENT '分块大小(字符)',
  `chunk_overlap` int NULL DEFAULT 50 COMMENT '分块重叠(字符)',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1回收站',
  `deleted_time` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_deleted`(`deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '知识库数据集' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of knowledge_dataset
-- ----------------------------

-- ----------------------------
-- Table structure for knowledge_document
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_document`;
CREATE TABLE `knowledge_document`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dataset_id` bigint NOT NULL COMMENT '数据集ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文档名称',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '原文内容',
  `char_count` int NULL DEFAULT 0 COMMENT '字符数',
  `chunk_count` int NULL DEFAULT 0 COMMENT '分块数',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'pending' COMMENT '状态: pending/indexing/ready/failed',
  `error_msg` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dataset`(`dataset_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '知识库文档' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of knowledge_document
-- ----------------------------

-- ----------------------------
-- Table structure for mem_item
-- ----------------------------
DROP TABLE IF EXISTS `mem_item`;
CREATE TABLE `mem_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID(app_agent.id)',
  `scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'global' COMMENT '作用域: global全局/user用户',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '来源: manual手动/auto自动抽取',
  `category` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'preference' COMMENT '类别: preference偏好/fact事实/event事件/summary摘要/custom自定义',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '记忆内容',
  `importance` tinyint NOT NULL DEFAULT 3 COMMENT '重要度 1-5(数字越大越重要)',
  `hit_count` int NOT NULL DEFAULT 0 COMMENT '命中次数(作为上下文注入时累计)',
  `last_hit_at` datetime NULL DEFAULT NULL COMMENT '最近命中时间',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app_cat`(`app_id` ASC, `category` ASC) USING BTREE,
  INDEX `idx_app_importance`(`app_id` ASC, `importance` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '记忆管理-长期记忆条目' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of mem_item
-- ----------------------------

-- ----------------------------
-- Table structure for mem_strategy
-- ----------------------------
DROP TABLE IF EXISTS `mem_strategy`;
CREATE TABLE `mem_strategy`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID(app_agent.id)',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用长期记忆: 0否 1是',
  `auto_extract` tinyint NOT NULL DEFAULT 0 COMMENT '对话后自动抽取记忆: 0否 1是',
  `extract_model_id` bigint NULL DEFAULT NULL COMMENT '自动抽取使用的对话模型ID(model_info.id)',
  `top_n` int NOT NULL DEFAULT 3 COMMENT '每次对话注入的记忆条目数',
  `keep_days` int NULL DEFAULT NULL COMMENT '记忆保留天数(空=永久保留)',
  `max_items` int NOT NULL DEFAULT 500 COMMENT '单应用记忆条目上限',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_app`(`app_id` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '记忆管理-应用记忆策略' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of mem_strategy
-- ----------------------------

-- ----------------------------
-- Table structure for mem_variable
-- ----------------------------
DROP TABLE IF EXISTS `mem_variable`;
CREATE TABLE `mem_variable`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID(app_agent.id)',
  `scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'global' COMMENT '作用域: global全局(跨会话)/session指定会话',
  `conversation_id` bigint NULL DEFAULT NULL COMMENT '所属会话ID(scope=session时使用，空=该应用全部会话)',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '变量名(英文下划线)',
  `value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '变量值',
  `value_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'string' COMMENT '类型: string/number/boolean/json',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '说明',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app_scope`(`app_id` ASC, `scope` ASC) USING BTREE,
  INDEX `idx_app_name`(`app_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_conv`(`conversation_id` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '记忆管理-会话变量' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of mem_variable
-- ----------------------------

-- ----------------------------
-- Table structure for model_info
-- ----------------------------
DROP TABLE IF EXISTS `model_info`;
CREATE TABLE `model_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `provider_id` bigint NOT NULL COMMENT '供应商ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型名(调用时使用)',
  `model_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: llm/embedding/rerank/tts/asr/image',
  `context_window` int NULL DEFAULT NULL COMMENT '上下文窗口',
  `max_tokens` int NULL DEFAULT NULL COMMENT '最大输出Token',
  `capabilities` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '能力(JSON数组)',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider`(`provider_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模型信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of model_info
-- ----------------------------
INSERT INTO `model_info` VALUES (5, 2, 'qwen3.8-27b', 'llm', NULL, NULL, '[\"stream\"]', 1, '2026-09-03 17:34:31', '2026-09-03 17:34:31');
INSERT INTO `model_info` VALUES (6, 2, 'qwen3.7-text-embedding-flash', 'embedding', NULL, NULL, '[\"stream\",\"json_mode\",\"reasoning\"]', 1, '2026-09-03 17:37:18', '2026-09-03 22:51:52');
INSERT INTO `model_info` VALUES (8, 3, 'doubao-seed-2-1-pro-260628', 'llm', NULL, NULL, '[\"vision\",\"stream\"]', 1, '2026-09-04 17:16:31', '2026-09-04 17:32:11');

-- ----------------------------
-- Table structure for model_provider
-- ----------------------------
DROP TABLE IF EXISTS `model_provider`;
CREATE TABLE `model_provider`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '供应商名称',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: openai-compatible/anthropic/...',
  `base_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'API基础地址',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'API Key(密文存储)',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模型供应商' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of model_provider
-- ----------------------------
INSERT INTO `model_provider` VALUES (2, 1, 'DashScope', 'openai-compatible', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-34cd7fe56f554bd7a79b509b6310434b', 1, '2026-09-03 17:33:45', '2026-09-03 17:47:03');
INSERT INTO `model_provider` VALUES (3, 1, '火山方舟', 'openai-compatible', 'https://ark.cn-beijing.volces.com/api/v3', 'ark-0ff6dcc7-a492-4c59-9f2a-b75795e788c0-25e3e', 1, '2026-09-04 17:16:16', '2026-09-04 17:26:56');

-- ----------------------------
-- Table structure for sys_tenant
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户名称',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户编码',
  `plan` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'free' COMMENT '套餐: free/pro/enterprise',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租户' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant
-- ----------------------------
INSERT INTO `sys_tenant` VALUES (1, '默认租户', 'default', 'pro', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录名',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码(BCrypt)',
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 1, 'admin', '$2a$10$9q7nqAmKWk7KcW/peXz.Ru4OVkaJUjrrI4UElU1RRk.Z.V6rSrDgq', '管理员', NULL, NULL, 1, '2026-08-28 00:22:58', '2026-08-28 01:23:10');

-- ----------------------------
-- Table structure for tool_connector
-- ----------------------------
DROP TABLE IF EXISTS `tool_connector`;
CREATE TABLE `tool_connector`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '连接器名称(英文标识符)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述(用途说明)',
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'http' COMMENT '类型: http/mysql',
  `url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'http: API地址; mysql: JDBC URL',
  `method` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'GET' COMMENT 'http: 请求方式',
  `headers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'http: 额外请求头(JSON)',
  `auth_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'none' COMMENT '鉴权: none/bearer/basic',
  `auth_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Bearer Token',
  `auth_username` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名(basic/mysql)',
  `auth_password` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码(basic/mysql)',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '外部连接器(数据集成)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of tool_connector
-- ----------------------------

-- ----------------------------
-- Table structure for tool_info
-- ----------------------------
DROP TABLE IF EXISTS `tool_info`;
CREATE TABLE `tool_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具名称(模型调用时使用)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工具描述(给模型理解用途)',
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'http' COMMENT '类型: http/code',
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'HTTP工具: 请求地址',
  `method` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'GET' COMMENT 'HTTP工具: 请求方式',
  `headers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'HTTP工具: 请求头(JSON)',
  `auth_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'none' COMMENT '鉴权: none/bearer/basic',
  `auth_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Bearer Token',
  `parameters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '参数JSON Schema(JSON)',
  `code` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '代码工具: MVEL脚本',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Agent工具注册表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of tool_info
-- ----------------------------
INSERT INTO `tool_info` VALUES (1, 1, 'get_current_time', '获取当前日期时间字符串', 'code', NULL, 'GET', NULL, 'none', NULL, '{\"type\":\"object\",\"properties\":{}}', 'return new java.text.SimpleDateFormat(\"yyyy-MM-dd HH:mm:ss\").format(new java.util.Date())', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');
INSERT INTO `tool_info` VALUES (2, 1, 'text_stats', '统计文本的长度、单词数和行数', 'code', NULL, 'GET', NULL, 'none', NULL, '{\"type\":\"object\",\"properties\":{}}', 'var t = input != null ? String.valueOf(input) : \"\"; return \"字符数=\" + t.length() + \", 单词数=\" + (t.trim().isEmpty() ? 0 : t.trim().split(\"\\\\s+\").length) + \", 行数=\" + t.split(\"\\n\").length', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');

-- ============================================================
-- P0/P1 新增模块表（发布渠道 / 应用市场 / 多智能体 / 内容安全 /
-- 模型网关 / 账号安全 / 对话标注 / 评测 / 费用账单）
-- ============================================================

-- ----------------------------
-- Table structure for publish_channel 发布渠道管理
-- ----------------------------
DROP TABLE IF EXISTS `publish_channel`;
CREATE TABLE `publish_channel`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '绑定应用ID(app_agent.id)',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道名称',
  `channel_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: wechat_mp公众号/feishu飞书/dingtalk钉钉/web网页/webhook',
  `config_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '渠道配置(JSON，含凭证/校验token等)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用: 0停用 1启用',
  `msg_count` bigint NOT NULL DEFAULT 0 COMMENT '累计消息数',
  `last_msg_at` datetime NULL DEFAULT NULL COMMENT '最近消息时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app`(`app_id` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '发布-渠道管理' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for publish_channel_msg 渠道消息
-- ----------------------------
DROP TABLE IF EXISTS `publish_channel_msg`;
CREATE TABLE `publish_channel_msg`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `direction` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'inbound' COMMENT '方向: inbound入站/outbound出站',
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '事件类型(如 message/event)',
  `from_user` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源用户标识(openid等)',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '消息内容',
  `reply` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '回复内容',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'success' COMMENT '处理状态: success/failed/skipped',
  `error_msg` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_channel`(`channel_id` ASC) USING BTREE,
  INDEX `idx_app_time`(`app_id` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '发布-渠道消息记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for app_market_item 应用市场(官方内置或用户上架)
-- ----------------------------
DROP TABLE IF EXISTS `app_market_item`;
CREATE TABLE `app_market_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '上架租户(空/0=平台官方)',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用名称',
  `description` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'general' COMMENT '分类: general通用/customer_service客服/translate翻译/writing写作/office办公/analysis分析/other其他',
  `icon` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'chatflow' COMMENT '应用类型: chatflow/workflow/agent',
  `workflow_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '应用DSL快照(安装时拷贝)',
  `config_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '基础配置(JSON: welcome_message等)',
  `source_app_id` bigint NULL DEFAULT NULL COMMENT '来源应用ID(用户上架时)',
  `author` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '平台官方' COMMENT '作者',
  `install_count` int NOT NULL DEFAULT 0 COMMENT '安装次数',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0下架 1上架',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '应用市场条目' ROW_FORMAT = DYNAMIC;

INSERT INTO `app_market_item` (`id`, `tenant_id`, `name`, `description`, `category`, `icon`, `type`, `workflow_json`, `config_json`, `source_app_id`, `author`, `install_count`, `status`) VALUES
(1, NULL, '智能客服助手', '基于知识库自动应答客户咨询，支持多轮追问与转人工引导，适合官网/电商客服场景。', 'customer_service', NULL, 'chatflow', '{\"nodes\":[],\"edges\":[]}', '{\"welcome_message\":\"您好，我是智能客服助手，请问有什么可以帮您？\",\"opening_questions\":[\"退货政策是什么？\",\"如何联系人工客服？\"]}', NULL, '平台官方', 0, 1),
(2, NULL, '中英互译助手', '高质量中英互译与术语润色，支持行业术语定制，适合翻译与国际化业务。', 'translate', NULL, 'workflow', '{\"nodes\":[],\"edges\":[]}', '{\"welcome_message\":\"我可以帮您完成中英文互译与润色，请输入需要翻译的内容。\",\"opening_questions\":[\"帮我翻译这段话\",\"润色这段英文\"]}', NULL, '平台官方', 0, 1),
(3, NULL, '日报周报助手', '根据工作事项自动生成结构化日报/周报，支持按模板定制，提升汇报效率。', 'office', NULL, 'chatflow', '{\"nodes\":[],\"edges\":[]}', '{\"welcome_message\":\"请描述今天完成的工作事项，我为您生成结构化日报。\",\"opening_questions\":[\"生成今日日报\",\"生成本周周报\"]}', NULL, '平台官方', 0, 1);

-- ----------------------------
-- Table structure for agent_team 多智能体团队
-- ----------------------------
DROP TABLE IF EXISTS `agent_team`;
CREATE TABLE `agent_team`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '团队名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `routing` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'first_match' COMMENT '路由策略: first_match意图匹配/round_robin轮询/all并行汇合',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0停用 1启用',
  `run_count` int NOT NULL DEFAULT 0 COMMENT '累计运行次数',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '多智能体编排-团队' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for agent_team_member 团队成员(角色)
-- ----------------------------
DROP TABLE IF EXISTS `agent_team_member`;
CREATE TABLE `agent_team_member`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `team_id` bigint NOT NULL COMMENT '团队ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '成员/角色名',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '职责描述',
  `app_id` bigint NOT NULL COMMENT '绑定应用ID(app_agent.id)',
  `keywords` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '意图关键词(逗号分隔，first_match路由用)',
  `priority` int NOT NULL DEFAULT 1 COMMENT '路由优先级(数字小优先)',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用: 0否 1是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_team`(`team_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '多智能体编排-团队成员' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for agent_team_run 团队运行记录
-- ----------------------------
DROP TABLE IF EXISTS `agent_team_run`;
CREATE TABLE `agent_team_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `team_id` bigint NOT NULL COMMENT '团队ID',
  `input` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '用户输入',
  `answer` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '最终回答',
  `routed_member` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '命中的成员(名称)',
  `trace_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '执行轨迹(JSON数组)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'running' COMMENT '状态: running/success/failed',
  `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '错误信息',
  `cost_ms` bigint NULL DEFAULT 0 COMMENT '耗时(毫秒)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `finish_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_team_time`(`team_id` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '多智能体编排-团队运行记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for guard_rule 内容安全规则
-- ----------------------------
DROP TABLE IF EXISTS `guard_rule`;
CREATE TABLE `guard_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规则说明',
  `direction` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'input' COMMENT '作用方向: input输入/output输出',
  `match_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'keyword' COMMENT '匹配方式: keyword关键词/regex正则/prompt_injection注入检测',
  `rule_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '匹配内容(关键词逗号分隔 或 正则表达式)',
  `action` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'mask' COMMENT '处置动作: block拦截/mask打码/replace替换',
  `replace_text` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '****' COMMENT '替换/打码文本',
  `risk_level` tinyint NOT NULL DEFAULT 3 COMMENT '风险等级 1-5',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用: 0否 1是',
  `priority` int NOT NULL DEFAULT 1 COMMENT '执行优先级(数字小优先)',
  `hit_count` bigint NOT NULL DEFAULT 0 COMMENT '累计命中次数',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_dir`(`tenant_id` ASC, `direction` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '内容安全-规则库' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for guard_app_bind 应用内容安全绑定
-- ----------------------------
DROP TABLE IF EXISTS `guard_app_bind`;
CREATE TABLE `guard_app_bind`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `rule_ids` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '绑定规则ID列表(JSON数组)',
  `mode` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'enforce' COMMENT '模式: enforce强制/log仅记录',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用: 0否 1是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app`(`app_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '内容安全-应用绑定' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for model_gateway_route 模型网关路由
-- ----------------------------
DROP TABLE IF EXISTS `model_gateway_route`;
CREATE TABLE `model_gateway_route`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '路由名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `route_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'priority' COMMENT '路由类型: priority优先级/failover故障回退/round_robin轮询',
  `targets_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标列表(JSON数组: [{modelId,weight,priority}])',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认路由: 0否 1是',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用: 0否 1是',
  `call_count` bigint NOT NULL DEFAULT 0 COMMENT '累计调用次数',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模型网关路由' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_user_security 账号安全扩展
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_security`;
CREATE TABLE `sys_user_security`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `mfa_enabled` tinyint NOT NULL DEFAULT 0 COMMENT 'MFA二次验证: 0关闭 1开启',
  `mfa_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MFA密钥(Base32)',
  `mfa_bound_at` datetime NULL DEFAULT NULL COMMENT 'MFA绑定时间',
  `last_login_at` datetime NULL DEFAULT NULL COMMENT '最近登录时间',
  `last_login_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最近登录IP',
  `login_count` int NOT NULL DEFAULT 0 COMMENT '累计登录次数',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '账号安全扩展' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for bill_budget 预算提醒设置
-- ----------------------------
DROP TABLE IF EXISTS `bill_budget`;
CREATE TABLE `bill_budget`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `month` varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '月份(yyyy-MM)',
  `budget` decimal(14, 2) NOT NULL DEFAULT 0.00 COMMENT '月度预算(元)',
  `notify_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '超预算提醒: 0否 1是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_month`(`tenant_id` ASC, `month` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '费用账单-预算设置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for chat_message_feedback 对话标注(反馈)
-- ----------------------------
DROP TABLE IF EXISTS `chat_message_feedback`;
CREATE TABLE `chat_message_feedback`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `message_id` bigint NOT NULL COMMENT '被标注消息ID(chat_message.id)',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '会话所属用户ID',
  `rating` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'good' COMMENT '评分: good好/bad差',
  `label_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标签: correct准确/incorrect错误/hallucination幻觉/off_topic跑题/vague含糊',
  `corrected_answer` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '补充参考答案',
  `note` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标注说明',
  `created_by` bigint NULL DEFAULT NULL COMMENT '标注人ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_message`(`message_id` ASC) USING BTREE,
  INDEX `idx_conversation`(`conversation_id` ASC) USING BTREE,
  INDEX `idx_app`(`app_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '对话标注-消息反馈' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for eval_dataset 评测数据集
-- ----------------------------
DROP TABLE IF EXISTS `eval_dataset`;
CREATE TABLE `eval_dataset`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '来源: manual手动/import导入/feedback对话标注回流',
  `sample_count` int NOT NULL DEFAULT 0 COMMENT '样本数量',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评测数据集' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for eval_sample 评测样本
-- ----------------------------
DROP TABLE IF EXISTS `eval_sample`;
CREATE TABLE `eval_sample`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dataset_id` bigint NOT NULL COMMENT '数据集ID',
  `question` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '提问',
  `reference` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '参考答案',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '样本分类/标签',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0停用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dataset`(`dataset_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评测样本' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for eval_experiment 对比实验
-- ----------------------------
DROP TABLE IF EXISTS `eval_experiment`;
CREATE TABLE `eval_experiment`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实验名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `dataset_id` bigint NOT NULL COMMENT '共用评测数据集ID',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0停用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dataset`(`dataset_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评测对比实验' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for eval_run 评测任务
-- ----------------------------
DROP TABLE IF EXISTS `eval_run`;
CREATE TABLE `eval_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `experiment_id` bigint NULL DEFAULT NULL COMMENT '所属对比实验ID(可为空)',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务名称',
  `dataset_id` bigint NOT NULL COMMENT '数据集ID',
  `app_id` bigint NULL DEFAULT NULL COMMENT '被测应用ID(与应用/模型二选一)',
  `app_version_id` bigint NULL DEFAULT NULL COMMENT '被测应用版本ID',
  `model_id` bigint NULL DEFAULT NULL COMMENT '被测模型ID(直连模型时)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT '状态: pending/running/success/failed/stopped',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '样本总数',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '通过数',
  `failed_count` int NOT NULL DEFAULT 0 COMMENT '未通过数',
  `pass_rate` decimal(6, 4) NULL DEFAULT NULL COMMENT '通过率(0-1)',
  `avg_score` decimal(6, 4) NULL DEFAULT NULL COMMENT '平均得分(0-1)',
  `report_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '报告(JSON: 分类得分/耗时统计等)',
  `started_at` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '运行错误',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dataset`(`dataset_id` ASC) USING BTREE,
  INDEX `idx_experiment`(`experiment_id` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评测任务' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for eval_run_case 评测任务用例明细
-- ----------------------------
DROP TABLE IF EXISTS `eval_run_case`;
CREATE TABLE `eval_run_case`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id` bigint NOT NULL COMMENT '评测任务ID',
  `sample_id` bigint NULL DEFAULT NULL COMMENT '样本ID',
  `question` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '提问',
  `reference` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '参考答案',
  `answer` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '模型/应用回答',
  `passed` tinyint NOT NULL DEFAULT 0 COMMENT '是否通过: 0否 1是',
  `score` decimal(6, 4) NULL DEFAULT NULL COMMENT '相似度得分(0-1)',
  `latency_ms` int NULL DEFAULT 0 COMMENT '耗时(毫秒)',
  `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '错误信息',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run`(`run_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评测用例明细' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_announcement 公告
-- ----------------------------
DROP TABLE IF EXISTS `sys_announcement`;
CREATE TABLE `sys_announcement`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '公告内容(支持换行的纯文本)',
  `scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'all' COMMENT '受众范围: all 全部用户',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态: 0草稿 1发布中 2已下线',
  `pinned` tinyint NULL DEFAULT 0 COMMENT '是否置顶: 0否 1是',
  `publish_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `offline_time` datetime NULL DEFAULT NULL COMMENT '下线时间',
  `publisher` bigint NULL DEFAULT NULL COMMENT '发布人用户ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1回收站',
  `deleted_time` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_deleted`(`deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '平台公告' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_announcement
-- ----------------------------
INSERT INTO `sys_announcement` VALUES (1, 1, 'AgentForge 平台 v1.2 功能升级公告', '各位用户：\n\n平台已于近期完成 v1.2 版本升级，本次升级新增以下能力：\n1. 新增费用账单与月度预算提醒；\n2. 新增发布渠道与应用市场模块；\n3. 优化多智能体编排与运行观测体验。\n\n如遇使用问题，可前往「帮助与文档」查看使用指南。', 'all', 1, 1, '2026-09-01 10:00:00', NULL, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT INTO `sys_announcement` VALUES (2, 1, '关于数据安全使用的提醒（草稿）', '请勿在知识库与提示词中上传含敏感信息的资料，注意遵守数据安全规范。', 'all', 0, 0, NULL, NULL, 1, '2026-09-02 14:20:00', '2026-09-02 14:20:00', 0, NULL);

-- ----------------------------
-- Table structure for app_template 应用模板
-- ----------------------------
DROP TABLE IF EXISTS `app_template`;
CREATE TABLE `app_template`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（0=平台内置）',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分类: customer-service/translate/content/data-analysis/marketing/coding/custom',
  `app_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'chatflow' COMMENT '应用类型: chatflow/workflow/agent',
  `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标(emoji)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '模板简介',
  `use_case` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '适用场景',
  `welcome_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '创建应用后的默认开场白',
  `builtin` tinyint NULL DEFAULT 0 COMMENT '是否平台内置: 0否 1是',
  `usage_count` int NULL DEFAULT 0 COMMENT '被使用次数',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态: 0停用 1启用',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1回收站',
  `deleted_time` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_deleted`(`deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '应用模板' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_template（平台内置）
-- ----------------------------
INSERT INTO `app_template` VALUES (1, 0, '智能客服助手', 'customer-service', 'chatflow', '💬', '基于知识库回答客户问题，支持多轮追问与转人工', '电商 / 企业服务', '你好，我是智能客服助手，请问有什么可以帮您？', 1, 3, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT INTO `app_template` VALUES (2, 0, '多语言翻译助手', 'translate', 'chatflow', '🌐', '中英互译与术语润色，支持行业术语定制', '出海业务', '你好，我可以帮你完成多语言翻译与润色。', 1, 1, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT INTO `app_template` VALUES (3, 0, '内容创作助手', 'content', 'agent', '✍️', '撰写文章 / 文案 / 脚本，自动调用写作工具', '新媒体运营', '我来帮你创作内容，告诉我主题与风格即可。', 1, 0, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT INTO `app_template` VALUES (4, 0, '数据分析工作流', 'data-analysis', 'workflow', '📊', '数据接入-清洗-分析-报告一键生成', '经营分析', '', 1, 0, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT INTO `app_template` VALUES (5, 0, '营销文案助手', 'marketing', 'chatflow', '📣', '生成营销文案与活动创意，可切换文案风格', '市场运营', '我可以帮你生成各平台营销文案。', 1, 2, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT INTO `app_template` VALUES (6, 0, '编程助手', 'coding', 'agent', '👨‍💻', '代码生成 / 解释 / 重构，调用代码工具链', '研发提效', '我是编程助手，可以帮你编写与解释代码。', 1, 0, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);

-- ----------------------------
-- Table structure for app_schedule 应用定时任务
-- ----------------------------
DROP TABLE IF EXISTS `app_schedule`;
CREATE TABLE `app_schedule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `app_id` bigint NOT NULL COMMENT '关联应用ID',
  `app_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用名称快照',
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'interval' COMMENT '触发类型: interval间隔/daily每天/weekly每周',
  `interval_minutes` int NULL DEFAULT NULL COMMENT '触发间隔(分钟)，interval 生效',
  `run_time` varchar(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发时刻 HH:mm，daily/weekly 生效',
  `run_weekday` int NULL DEFAULT NULL COMMENT '周几 1-7(周一到周日)，weekly 生效',
  `input_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发时发送给应用的输入',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用: 0停用 1启用',
  `last_run_time` datetime NULL DEFAULT NULL COMMENT '最近执行时间',
  `next_run_time` datetime NULL DEFAULT NULL COMMENT '下次执行时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_next`(`enabled` ASC, `next_run_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '应用定时任务' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for app_schedule_log 定时任务执行记录
-- ----------------------------
DROP TABLE IF EXISTS `app_schedule_log`;
CREATE TABLE `app_schedule_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `schedule_id` bigint NOT NULL COMMENT '任务ID',
  `schedule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务名称快照',
  `app_id` bigint NULL DEFAULT NULL COMMENT '应用ID',
  `app_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用名称快照',
  `trigger_by` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'scheduled' COMMENT '触发方式: scheduled自动/manual手动',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'success' COMMENT '执行结果: success成功/failed失败',
  `message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '结果摘要/错误信息',
  `cost_ms` int NULL DEFAULT NULL COMMENT '耗时(毫秒)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_schedule`(`schedule_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务执行记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_notification 站内通知
-- ----------------------------
DROP TABLE IF EXISTS `sys_notification`;
CREATE TABLE `sys_notification`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system' COMMENT '类型: system系统/announcement公告/run任务/alert告警',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '内容',
  `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务类型(如 announcement)',
  `biz_id` bigint NULL DEFAULT NULL COMMENT '业务ID',
  `read` tinyint NULL DEFAULT 0 COMMENT '是否已读: 0未读 1已读',
  `read_time` datetime NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_read`(`tenant_id` ASC, `user_id` ASC, `read` ASC) USING BTREE,
  INDEX `idx_biz`(`biz_type` ASC, `biz_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '站内通知' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_notification
-- ----------------------------
INSERT INTO `sys_notification` VALUES (1, 1, 1, 'announcement', 'AgentForge 平台 v1.2 功能升级公告', '平台已完成 v1.2 版本升级，新增费用账单、发布渠道与应用市场等能力，欢迎体验。', 'announcement', 1, 1, '2026-09-02 09:00:00', '2026-09-01 10:00:05', '2026-09-01 10:00:05');
INSERT INTO `sys_notification` VALUES (2, 1, 1, 'system', '欢迎使用 AgentForge 智能体平台', '你已成功注册平台账号。可前往「智能体」创建你的第一个应用，或到「使用指南」快速上手。', NULL, NULL, 0, NULL, '2026-09-01 00:30:00', '2026-09-01 00:30:00');

-- ----------------------------
-- Table structure for sys_oper_log 操作日志
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人登录名',
  `module` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属模块',
  `operation` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作内容',
  `method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'HTTP 方法',
  `uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求路径',
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源IP',
  `success` tinyint NULL DEFAULT 1 COMMENT '是否成功: 0失败 1成功',
  `error_msg` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '错误信息',
  `cost_ms` int NULL DEFAULT NULL COMMENT '耗时(毫秒)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_time`(`tenant_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------
INSERT INTO `sys_oper_log` VALUES (1, 1, 1, 'admin', '智能体', '发布应用', 'POST', '/api/app/agents/3/publish', '127.0.0.1', 1, NULL, 560, '2026-09-04 10:12:03');
INSERT INTO `sys_oper_log` VALUES (2, 1, 1, 'admin', '知识库', '上传文档', 'POST', '/api/knowledge/documents/upload', '127.0.0.1', 1, NULL, 1240, '2026-09-04 10:05:41');
INSERT INTO `sys_oper_log` VALUES (3, 1, 1, 'admin', '系统管理', '新增成员', 'POST', '/api/sys/users', '127.0.0.1', 1, NULL, 45, '2026-09-03 18:42:09');
INSERT INTO `sys_oper_log` VALUES (4, 1, 1, 'admin', '工具', '配置数据源', 'PUT', '/api/tool/integrations/7', '127.0.0.1', 1, NULL, 88, '2026-09-03 16:20:55');
INSERT INTO `sys_oper_log` VALUES (5, 1, 1, 'admin', '模型', '更新供应商', 'PUT', '/api/models/1', '127.0.0.1', 1, NULL, 63, '2026-09-03 11:08:30');
INSERT INTO `sys_oper_log` VALUES (6, 1, 1, 'admin', '系统管理', '删除成员', 'DELETE', '/api/sys/users/9', '127.0.0.1', 0, '用户不存在: 9', 12, '2026-09-02 15:33:47');
INSERT INTO `sys_oper_log` VALUES (7, 1, 1, 'admin', '提示词库', '新增提示词', 'POST', '/api/app/prompts', '127.0.0.1', 1, NULL, 28, '2026-09-02 09:14:26');
INSERT INTO `sys_oper_log` VALUES (8, 1, 1, 'admin', '评测', '运行评测任务', 'POST', '/api/eval/tasks/2/run', '127.0.0.1', 1, NULL, 982, '2026-09-01 20:47:12');

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Table structure for ops_alert_rule 告警规则
-- ----------------------------
DROP TABLE IF EXISTS `ops_alert_rule`;
CREATE TABLE `ops_alert_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规则名称',
  `metric` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指标: error_rate错误率/failures失败数/latency延迟/cost成本',
  `operator` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '>=' COMMENT '比较符',
  `threshold` decimal(12, 4) NULL DEFAULT NULL COMMENT '阈值',
  `window_minutes` int NULL DEFAULT 60 COMMENT '统计窗口(分钟)',
  `level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'warning' COMMENT '级别: warning/critical',
  `channels` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'notification' COMMENT '通知渠道: notification,email,webhook',
  `webhook_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Webhook地址',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用: 0否 1是',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `last_fire_time` datetime NULL DEFAULT NULL COMMENT '最近触发时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '告警规则' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ops_alert_rule
-- ----------------------------
INSERT INTO `ops_alert_rule` VALUES (1, 1, '错误率过高预警', 'error_rate', '>=', 5.0000, 60, 'warning', 'notification,webhook', 'https://example.com/hooks/agent-platform-alert', 1, '应用错误率超过 5% 时触发告警', '2026-09-04 09:20:00', '2026-09-02 10:00:00', '2026-09-02 10:00:00');
INSERT INTO `ops_alert_rule` VALUES (2, 1, '运行失败数突增', 'failures', '>=', 10.0000, 60, 'critical', 'notification,email', NULL, 1, '60 分钟内运行失败超过 10 次', NULL, '2026-09-02 10:05:00', '2026-09-02 10:05:00');
INSERT INTO `ops_alert_rule` VALUES (3, 1, '月成本超预算', 'cost', '>=', 300.0000, 1440, 'warning', 'notification', NULL, 1, '月度累计成本超过 300 元提醒', NULL, '2026-09-02 10:10:00', '2026-09-02 10:10:00');

-- ----------------------------
-- Table structure for ops_alert_event 告警事件
-- ----------------------------
DROP TABLE IF EXISTS `ops_alert_event`;
CREATE TABLE `ops_alert_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `rule_id` bigint NULL DEFAULT NULL COMMENT '规则ID',
  `rule_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规则名(冗余)',
  `metric` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指标',
  `level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'warning' COMMENT '级别',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发描述',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'open' COMMENT '状态: open/handled/ignored',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'auto' COMMENT '来源: manual手动测试/auto自动',
  `trigger_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
  `handled_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id`, `status`) USING BTREE,
  INDEX `idx_rule`(`rule_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '告警事件' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ops_alert_event
-- ----------------------------
INSERT INTO `ops_alert_event` VALUES (1, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'handled', 'manual', '2026-09-03 15:24:00', '2026-09-03 15:30:12');
INSERT INTO `ops_alert_event` VALUES (2, 1, 2, '运行失败数突增', 'failures', 'critical', '【手动测试】规则「运行失败数突增」触发：运行失败数 >= 10（60 分钟窗口）', 'open', 'manual', '2026-09-04 09:20:00', NULL);
