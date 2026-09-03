-- ============================================================================
-- 「工具 / 数据集成」新增模块建表脚本（tool_connector 外部连接器）
-- 目标库：agent_platform（与 agent_platform.sql 同库，可直接在现有库执行）
-- 幂等：使用 IF NOT EXISTS，重复执行不会破坏已有数据
-- 执行方式：mysql -uroot -p agent_platform < upgrade-tool-connector.sql
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
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

-- ----------------------------------------------------------------------------
-- 1. 外部连接器表（/tools/integrations 数据集成）
--    支持 HTTP API 连接器与 MySQL 数据库连接器；
--    配合 /api/tool/connectors 的 test 连通性校验 与 as-tool 一键生成 HTTP 工具
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tool_connector` (
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

SET FOREIGN_KEY_CHECKS = 1;
