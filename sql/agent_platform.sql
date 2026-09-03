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

 Date: 03/09/2026 22:33:09
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
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '智能体应用' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_agent
-- ----------------------------
INSERT INTO `app_agent` VALUES (6, 1, '智能客服助手', '基于知识库回答客户问题，支持多轮追问与转人工', 'chatflow', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, '2026-08-28 01:34:35', '2026-08-28 01:34:35');
INSERT INTO `app_agent` VALUES (7, 1, '1111', '', 'chatflow', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, '2026-08-28 01:40:53', '2026-08-28 01:40:53');
INSERT INTO `app_agent` VALUES (8, 1, '多语言翻译助手', '中英互译与术语润色，支持行业术语定制', 'workflow', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, '2026-08-28 01:41:06', '2026-08-28 01:41:06');
INSERT INTO `app_agent` VALUES (9, 1, '智能客服助手', '基于知识库回答客户问题，支持多轮追问与转人工', 'chatflow', NULL, NULL, NULL, 0, '{\"nodes\":[],\"edges\":[]}', NULL, NULL, NULL, '2026-08-28 01:42:44', '2026-08-28 03:25:50');
INSERT INTO `app_agent` VALUES (10, 1, '11', '', 'chatflow', NULL, NULL, NULL, 1, NULL, NULL, NULL, 1, '2026-08-29 22:42:00', '2026-09-02 17:17:37');

-- ----------------------------
-- Table structure for app_agent_tool
-- ----------------------------
DROP TABLE IF EXISTS `app_agent_tool`;
CREATE TABLE `app_agent_tool`  (
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
-- Records of app_agent_tool
-- ----------------------------
INSERT INTO `app_agent_tool` VALUES (1, 1, 'get_current_time', '获取当前日期时间字符串', 'code', NULL, 'GET', NULL, 'none', NULL, '{\"type\":\"object\",\"properties\":{}}', 'return new java.text.SimpleDateFormat(\"yyyy-MM-dd HH:mm:ss\").format(new java.util.Date())', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');
INSERT INTO `app_agent_tool` VALUES (2, 1, 'text_stats', '统计文本的长度、单词数和行数', 'code', NULL, 'GET', NULL, 'none', NULL, '{\"type\":\"object\",\"properties\":{}}', 'var t = input != null ? String.valueOf(input) : \"\"; return \"字符数=\" + t.length() + \", 单词数=\" + (t.trim().isEmpty() ? 0 : t.trim().split(\"\\\\s+\").length) + \", 行数=\" + t.split(\"\\n\").length', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');

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
INSERT INTO `app_agent_version` VALUES (1, 10, 1, '{\"nodes\":[{\"id\":\"node-1788340652792-7\",\"type\":\"start\",\"label\":\"开始\",\"position\":{\"x\":120,\"y\":260},\"config\":{\"variables\":[],\"welcome\":\"\"}},{\"id\":\"node-1788340652792-8\",\"type\":\"end\",\"label\":\"结束\",\"position\":{\"x\":460,\"y\":260},\"config\":{\"answerTemplate\":\"\"}}],\"edges\":[{\"id\":\"edge-1788340652792-9\",\"source\":\"node-1788340652792-7\",\"target\":\"node-1788340652792-8\"}]}', '', 1, 1, '2026-09-02 17:17:36');

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工作流运行记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_agent_run
-- ----------------------------

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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '聊天会话' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_conversation
-- ----------------------------
INSERT INTO `chat_conversation` VALUES (1, 1, 1, 9, '11', 'agent', 3, 0, '2026-08-28 01:54:37', '2026-08-28 02:59:40');

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '聊天消息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_message
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
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模型信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of model_info
-- ----------------------------
INSERT INTO `model_info` VALUES (5, 2, 'qwen3.8-27b', 'llm', NULL, NULL, '[\"stream\"]', 1, '2026-09-03 17:34:31', '2026-09-03 17:34:31');
INSERT INTO `model_info` VALUES (6, 2, 'qwen3.7-text-embedding-flash', 'embedding', NULL, NULL, NULL, 1, '2026-09-03 17:37:18', '2026-09-03 17:37:18');

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模型供应商' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of model_provider
-- ----------------------------
INSERT INTO `model_provider` VALUES (2, 1, 'DashScope', 'openai-compatible', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-34cd7fe56f554bd7a79b509b6310434b', 1, '2026-09-03 17:33:45', '2026-09-03 17:47:03');

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

SET FOREIGN_KEY_CHECKS = 1;
