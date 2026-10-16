# 数据库脚本说明

数据库：**PostgreSQL 16+**，需启用 **pgvector** 扩展（知识库向量检索依赖）。
本目录脚本均为**幂等设计，可重复执行，不会清空已有数据**。

## 文件说明

| 文件 | 用途 | 幂等机制 |
|---|---|---|
| `schema.sql` | 全量表结构（52 张表） | `CREATE TABLE IF NOT EXISTS`，已有表不受影响 |
| `data.sql` | 种子数据（示例应用、提示词模板、模型供应商等） | `ON CONFLICT DO NOTHING`，依赖主键去重 |
| `migration/` | 增量变更补丁（新增列、索引、表） | 按文件名顺序执行，见下文约定 |

## 初始化新环境

```bash
# 建库并启用 pgvector
psql -U postgres -c "CREATE DATABASE agent_platform;"
psql -U postgres -d agent_platform -c "CREATE EXTENSION IF NOT EXISTS vector;"   # schema.sql 首行已包含

psql -U postgres -d agent_platform -f schema.sql
psql -U postgres -d agent_platform -f data.sql    # 可选：仅开发/演示环境需要种子数据
```

## 向量列约定

`knowledge_chunk.embedding` 为 pgvector `vector` 类型（不定维度，精确检索）。
Java 侧以 `'[f1,f2,...]'` 字面量字符串读写，依赖 JDBC URL 参数 `stringtype=unspecified`（默认配置已带）。
数据规模增长后可在该列建 HNSW 索引（需固定维度列或表达式索引）。

## 变更约定（重要）

**禁止**再提交 `DROP TABLE` / 全量重建式脚本。任何结构变更请在 `migration/` 下新增补丁文件：

- 命名：`V<日期>_<序号>__<描述>.sql`，例如 `V20261016_01__add_app_agent_remark.sql`
- 内容必须幂等：新增列用 `ADD COLUMN IF NOT EXISTS`
- 已在生产执行过的补丁**不要修改**，新增问题用下一个补丁修正

## 历史说明

- 2026-10-16 之前使用 MySQL，`agent_platform.sql`（Navicat 整库 dump，含 `DROP TABLE`）已删除，如需回溯请从 Git 历史获取（最后版本见 commit 5b13cc4）。
- 2026-10-16 数据库从 MySQL 迁移至 PostgreSQL + pgvector；存量 MySQL 数据迁移建议使用 pgloader。
