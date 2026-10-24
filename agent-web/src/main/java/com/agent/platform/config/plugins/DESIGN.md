# SQL 插件实现设计

使用方法、配置、忽略注解和 SQL 支持范围见 [README](README.md)。本文面向维护者，说明职责与不能破坏的执行约束。

## 分层与职责

逻辑删除直接继承 `BaseMultiTableInnerInterceptor`；租户扩展原生 `TenantLineInnerInterceptor`。二者通过组合共享机制，逻辑删除不继承租户插件，也不借用租户 Handler 表达删除规则。

| 组件 | 职责 |
| --- | --- |
| `ScopedTenantLineInnerInterceptor` | 租户策略入口：查询条件、写入校验调用、更新目标和冲突行隔离 |
| `LogicDeleteInnerInterceptor` | 删除策略入口：条件生成/去重、INSERT 正常值、删除时间与 DELETE 转换 |
| `WriteSqlSupport` | 判断写入命令与豁免、检查 BoundSql 完成标记、避免两个生命周期钩子重复处理 |
| `TenantWriteValidator` | 校验 INSERT/UPDATE 租户值，读取实际参数，规范化 INSERT SELECT 的租户投影 |
| `ScopedSelectSupport` | SELECT/VALUES/子查询遍历、CTE 作用域与表达式位置协调 |
| `CteScope` | 管理普通、递归、嵌套 WITH 名称的可见域 |
| `DmlSqlSupport` | UPSERT 分支到 UPDATE AST 的桥接、UPDATE 来源遍历、保留 OR 优先级的条件追加 |
| `InsertValueSupport` | 按行/分支追加值，定位显式租户列，不改变原有 JDBC 参数顺序 |
| `SelectExpressionSupport` / `SubqueryExpressionVisitor` | 补齐额外表达式位置，规避 JSqlParser 4.6 窗口 visitor 的空指针和遗漏 |
| `QueryJoinSupport` | 连接类型支持范围与纯内连接判断 |
| `LogicDeleteHandler` / `TableIdentity` | 读取实体元数据、按 schema 与 PostgreSQL 标识符规则匹配表 |

公共辅助类保留在 `inner.other`，不持有业务服务或数据库连接。策略类负责决定过滤什么，辅助类负责如何遍历、改写。

## 执行顺序

```text
租户 → 逻辑删除 → 分页
```

查询通过 `beforeQuery` 改写实际 BoundSql，分页在过滤条件添加之后统计总数。

写入执行顺序：

```text
创建本次 StatementHandler / BoundSql
  → beforeGetBoundSql：校验本次参数并改写 SQL
  → BATCH/REUSE 根据改写后的 SQL 判断是否复用
  → beforePrepare：SIMPLE 的兜底入口，已处理时跳过
  → 绑定原有 JDBC 参数并执行或加入批次
```

不能只在 `beforePrepare` 校验租户参数：BATCH/REUSE 后续调用可能不再次 prepare。也不应通过另调 `MappedStatement.getBoundSql()` 生成第二份动态 SQL 来校验，否则校验对象可能不是实际执行对象。

`WriteSqlSupport` 将内部完成标记放入实际 BoundSql 的 additionalParameters；只有改写成功才写入。标记记录所属 support 实例与上下文，不使用可能被误设的普通布尔值。租户实例还检查同一 BoundSql 内的租户上下文没有变化。标记不加入 ParameterMapping，因此不参与 JDBC 绑定。

租户常量参与改写后的 SQL，租户切换会影响语句复用键。逻辑删除在同一入口改写，确保被复用的预编译语句已完成 DELETE 到 UPDATE 的转换。

## 参数与线程状态

`TenantWriteValidator` 按 JSqlParser 的 JDBC 参数索引定位 ParameterMapping，再依次读取：

1. BoundSql 额外参数，包括 foreach 生成的参数。
2. 配有 TypeHandler 的直接参数对象。
3. 实体或 Map 的属性。

仅验证可确定的值；无法验证的租户赋值明确拒绝。原有占位符不被删除或重新排序，新补的租户/删除标记使用常量。

参数上下文、CTE 作用域、逻辑删除的内连接约束栈均在 `finally` 中恢复或移除。嵌套调用恢复上一层上下文，不直接清除外层状态；线程池复用不应继承上一请求的状态。

## 查询与更新的作用域

`ScopedSelectSupport` 回调到所属拦截器的 `processSelectBody`，使公共基类仍负责连接、UNION、派生表遍历，各策略通过 `buildTableExpression` 提供条件。

CTE 定义和引用分开处理：非递归定义可见此前定义；递归 WITH 可见递归名称；限定 schema 的实体表不当成 CTE。进入子查询时重新建立其 WITH 作用域，退出后恢复外层。

UPDATE FROM/JOIN 通过临时 PlainSelect 复用来源表遍历。临时节点共享原 JOIN 节点，处理后只回写 WHERE。即使目标表豁免，来源子查询仍然过滤；更新目标永远按实体表处理，不能因为同名 CTE 而豁免。

## 逻辑删除规则

- 已有等值条件仅在当前表、当前作用域可保证成立时复用。AND 任一分支成立即可，OR 必须每个分支都成立；不从 NOT 或子查询中推断外层约束。
- 纯内连接可复用 WHERE 中的条件；外连接不把 ON 中的保留侧条件当作全局过滤条件。
- NULL 正常值生成 `IS NULL`，字符串值按字符串表达式处理。
- 单表 DELETE 校验语法边界后直接构建 UPDATE AST，保留原 Table、WHERE 及参数节点，不再拼接后重新解析。
- UPSERT 通过 `DmlSqlSupport.conflict` 复用更新策略，回写 WHERE 与更新字段；其删除过滤必须限定目标表，避免与 EXCLUDED 歧义。
- 已显式提供删除时间时保留原值；可识别的删除赋值且存在 `deletedTime` 属性时补 `CURRENT_TIMESTAMP`。

## 元数据与 schema

以完整表名和配置的默认 schema 构造 `TableIdentity`；未引用名称转小写，引号内容保留大小写、点与转义引号。未限定名称只映射到声明的默认 schema，不按短表名从其他 schema 猜测。

元数据在 Mapper 注册后读取，不在拦截器构造时建立永久快照。当前未引入全局 AST 或元数据缓存，避免实体注册顺序、多个 MyBatis 配置和可变 AST 引起陈旧或共享状态问题；性能优化需有压测依据和失效策略。

## 回归验证

| 测试类 | 重点 |
| --- | --- |
| `LogicDeleteInnerInterceptorTest` | 基础 SQL、XML/注解、内置 TableLogic、回收站与拦截器顺序 |
| `LogicDeleteBoundaryTest` | 子查询位置、JOIN 去重、递归/嵌套作用域、异常与并发清理 |
| `LogicDeleteSchemaTest` | 默认 schema、同名表、引号和 schema DML 的 AST 结果 |
| `LogicDeletePostgresTest` | 实际行数、参数绑定、UPSERT、删除时间及 SIMPLE/BATCH/REUSE |
| `LogicDeleteSchemaPostgresTest` | PostgreSQL 跨 schema 同名表、引号名称与真实增删改查 |

修改公共遍历或写入入口时运行整组测试。新增语法应同时验证 SQL 形状、真实返回/修改行数、参数绑定与豁免行为，不能仅检查 SQL 是否包含一个字段名。

当前版本仍保留无上下文默认租户 1、未注册实体默认跳过逻辑删除等兼容策略；参数化/计算型删除赋值、动态 search_path 及支持范围之外的访问路径，遵循 README 所述边界。
