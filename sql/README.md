# 数据库脚本说明

本目录脚本均为**幂等设计，可重复执行，不会清空已有数据**。

## 文件说明

| 文件 | 用途 | 幂等机制 |
|---|---|---|
| `schema.sql` | 全量表结构（51 张表） | `CREATE TABLE IF NOT EXISTS`，已有表不受影响 |
| `data.sql` | 种子数据（示例应用、提示词模板、模型供应商等） | `INSERT IGNORE`，依赖主键/唯一键去重 |
| `migration/` | 增量变更补丁（新增列、索引、表） | 按文件名顺序执行，见下文约定 |

## 初始化新环境

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS agent_platform DEFAULT CHARSET utf8mb4;"
mysql -u root -p agent_platform < schema.sql
mysql -u root -p agent_platform < data.sql    # 可选：仅开发/演示环境需要种子数据
```

## 变更约定（重要）

**禁止**再提交 `DROP TABLE` / 全量重建式脚本。任何结构变更请在 `migration/` 下新增补丁文件：

- 命名：`V<日期>_<序号>__<描述>.sql`，例如 `V20261016_01__add_app_agent_remark.sql`
- 内容必须幂等：新增列先判断是否存在（可用 `information_schema` 判断或存储过程兜底）
- 已在生产执行过的补丁**不要修改**，新增问题用下一个补丁修正

## 历史说明

2026-10-16 之前的 `agent_platform.sql`（Navicat 整库 dump，含 `DROP TABLE`）已删除，
重复执行会清空全部业务数据，如需回溯请从 Git 历史获取。
