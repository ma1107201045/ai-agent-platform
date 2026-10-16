-- ----------------------------
-- 2026-10-16 软删除扩展：团队/工具/技能/提示词模板/会话/数据表 接入回收站（PostgreSQL）
-- 适用于已按旧版 schema 初始化的存量库；新装库直接执行 schema.sql 即可，无需本补丁
-- ----------------------------

ALTER TABLE agent_team
    ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_time timestamp DEFAULT NULL;

ALTER TABLE app_tool
    ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_time timestamp DEFAULT NULL;

ALTER TABLE app_skill
    ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_time timestamp DEFAULT NULL;

ALTER TABLE app_prompt_template
    ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_time timestamp DEFAULT NULL;

ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_time timestamp DEFAULT NULL;

ALTER TABLE data_table
    ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_time timestamp DEFAULT NULL;

COMMENT ON COLUMN agent_team.deleted IS '逻辑删除: 0正常 1回收站';
COMMENT ON COLUMN agent_team.deleted_time IS '删除时间';
COMMENT ON COLUMN app_tool.deleted IS '逻辑删除: 0正常 1回收站';
COMMENT ON COLUMN app_tool.deleted_time IS '删除时间';
COMMENT ON COLUMN app_skill.deleted IS '逻辑删除: 0正常 1回收站';
COMMENT ON COLUMN app_skill.deleted_time IS '删除时间';
COMMENT ON COLUMN app_prompt_template.deleted IS '逻辑删除: 0正常 1回收站';
COMMENT ON COLUMN app_prompt_template.deleted_time IS '删除时间';
COMMENT ON COLUMN chat_conversation.deleted IS '逻辑删除: 0正常 1回收站';
COMMENT ON COLUMN chat_conversation.deleted_time IS '删除时间';
COMMENT ON COLUMN data_table.deleted IS '逻辑删除: 0正常 1回收站';
COMMENT ON COLUMN data_table.deleted_time IS '删除时间';
