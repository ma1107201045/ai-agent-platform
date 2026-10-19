-- 迁移：为软删除（回收站）相关的 6 张表补齐 deleted / deleted_time 列
-- 背景：实体已使用 @TableLogic 逻辑删除，但旧库表结构缺列，查询报 column "deleted" does not exist
-- 幂等：可重复执行（IF NOT EXISTS）。在业务数据库上执行本脚本即可，无需重建库。

ALTER TABLE "agent_team"           ADD COLUMN IF NOT EXISTS "deleted" smallint NOT NULL DEFAULT 0,
                                   ADD COLUMN IF NOT EXISTS "deleted_time" timestamp;
ALTER TABLE "app_tool"             ADD COLUMN IF NOT EXISTS "deleted" smallint NOT NULL DEFAULT 0,
                                   ADD COLUMN IF NOT EXISTS "deleted_time" timestamp;
ALTER TABLE "app_skill"            ADD COLUMN IF NOT EXISTS "deleted" smallint NOT NULL DEFAULT 0,
                                   ADD COLUMN IF NOT EXISTS "deleted_time" timestamp;
ALTER TABLE "app_prompt_template"  ADD COLUMN IF NOT EXISTS "deleted" smallint NOT NULL DEFAULT 0,
                                   ADD COLUMN IF NOT EXISTS "deleted_time" timestamp;
ALTER TABLE "chat_conversation"    ADD COLUMN IF NOT EXISTS "deleted" smallint NOT NULL DEFAULT 0,
                                   ADD COLUMN IF NOT EXISTS "deleted_time" timestamp;
ALTER TABLE "data_table"           ADD COLUMN IF NOT EXISTS "deleted" smallint NOT NULL DEFAULT 0,
                                   ADD COLUMN IF NOT EXISTS "deleted_time" timestamp;

-- 同步补建：发布版本号唯一索引（防并发发布产生重复版本号）
CREATE UNIQUE INDEX IF NOT EXISTS "uk_app_version" ON "app_agent_version" ("app_id", "version");
