-- ============================================================================
-- 「数据」菜单增强模块 建表脚本（记忆管理 / 数据存储 / 素材管理）
-- 目标库：agent_platform（与 agent_platform.sql 同库，可直接在现有库执行）
-- 幂等：使用 IF NOT EXISTS，重复执行不会破坏已有数据
-- 执行方式：mysql -uroot -p agent_platform < upgrade-data-modules.sql
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 一、记忆管理（/data/memory）
-- ----------------------------------------------------------------------------

-- 1.1 记忆策略：每个应用一条，控制长期记忆的开关/抽取/注入参数
CREATE TABLE IF NOT EXISTS `mem_strategy` (
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

-- 1.2 会话变量：跨会话保存的键值上下文（global 全局 / session 指定会话）
CREATE TABLE IF NOT EXISTS `mem_variable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID(app_agent.id)',
  `scope` varchar(16) NOT NULL DEFAULT 'global' COMMENT '作用域: global全局(跨会话)/session指定会话',
  `conversation_id` bigint NULL DEFAULT NULL COMMENT '所属会话ID(scope=session时使用，空=该应用全部会话)',
  `name` varchar(64) NOT NULL COMMENT '变量名(英文下划线)',
  `value` text NULL COMMENT '变量值',
  `value_type` varchar(16) NOT NULL DEFAULT 'string' COMMENT '类型: string/number/boolean/json',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '说明',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app_scope`(`app_id` ASC, `scope` ASC) USING BTREE,
  INDEX `idx_app_name`(`app_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_conv`(`conversation_id` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '记忆管理-会话变量' ROW_FORMAT = DYNAMIC;

-- 1.3 长期记忆条目：自动抽取或手动沉淀的事实/偏好/摘要等
CREATE TABLE IF NOT EXISTS `mem_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `app_id` bigint NOT NULL COMMENT '应用ID(app_agent.id)',
  `scope` varchar(16) NOT NULL DEFAULT 'global' COMMENT '作用域: global全局/user用户',
  `source` varchar(16) NOT NULL DEFAULT 'manual' COMMENT '来源: manual手动/auto自动抽取',
  `category` varchar(24) NOT NULL DEFAULT 'preference' COMMENT '类别: preference偏好/fact事实/event事件/summary摘要/custom自定义',
  `content` text NOT NULL COMMENT '记忆内容',
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

-- ----------------------------------------------------------------------------
-- 二、数据存储（/data/storage）
-- ----------------------------------------------------------------------------

-- 2.1 自定义数据表：列定义以 JSON 存储（[{key,label,type}]），行数据用 data_record
CREATE TABLE IF NOT EXISTS `data_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(64) NOT NULL COMMENT '数据表名(展示用，应用内唯一)',
  `label` varchar(128) NULL DEFAULT NULL COMMENT '显示名称/别名',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '描述',
  `columns_json` text NULL COMMENT '列定义(JSON数组: [{key,label,type,options?}])',
  `row_count` int NOT NULL DEFAULT 0 COMMENT '行记录数',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_name`(`tenant_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据存储-自定义数据表' ROW_FORMAT = DYNAMIC;

-- 2.2 数据记录：行数据以 JSON 对象存储（键为列 key）
CREATE TABLE IF NOT EXISTS `data_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `table_id` bigint NOT NULL COMMENT '数据表ID(data_table.id)',
  `data_json` longtext NOT NULL COMMENT '行数据(JSON对象: {列key:值})',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_table`(`table_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据存储-数据记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------------------------------------------------------
-- 三、素材管理（/data/assets）
-- ----------------------------------------------------------------------------

-- 3.1 素材文件：元数据入库，二进制落盘于 platform.upload-dir 配置目录
CREATE TABLE IF NOT EXISTS `asset_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(255) NOT NULL COMMENT '素材名称(展示用)',
  `original_name` varchar(255) NULL DEFAULT NULL COMMENT '原始文件名',
  `ext` varchar(16) NULL DEFAULT NULL COMMENT '扩展名(小写不带点)',
  `content_type` varchar(128) NULL DEFAULT NULL COMMENT 'MIME类型',
  `size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `category` varchar(16) NOT NULL DEFAULT 'other' COMMENT '分类: image/document/audio/video/other',
  `storage_path` varchar(512) NOT NULL COMMENT '存储相对路径(实际文件位于 upload-dir 下)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0删除 1正常',
  `created_by` bigint NULL DEFAULT NULL COMMENT '上传用户ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '素材管理-素材文件' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
