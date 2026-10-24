# 租户隔离与逻辑删除插件

MyBatis-Plus 3.5.5 / JSqlParser 4.6，面向项目的 PostgreSQL 数据访问层。
MyBatis-Plus 内置方法、Mapper XML 和注解 SQL 共用同一套执行前处理。

- [使用与配置](#使用与配置)
- [忽略某个方法或 Mapper](#忽略某个方法或-mapper)
- [SQL 行为](#sql-行为)
- [支持范围与限制](#支持范围与限制)
- [运行测试](#运行测试)
- [实现设计与维护约束](DESIGN.md)

## 使用与配置

项目已在 [MybatisPlusConfig](../MybatisPlusConfig.java) 注册：

```text
ScopedTenantLineInnerInterceptor → LogicDeleteInnerInterceptor → PaginationInnerInterceptor
```

租户 ID 来自 `UserContext`。逻辑删除字段和值来自已注册实体的 MyBatis-Plus `TableInfo`，与 `@TableLogic`、全局逻辑删除配置保持一致，不需要维护表清单。

```java
@TableName(value = "app_agent", schema = "public")
public class AppAgent {
    @TableId
    private Long id;
    private Long tenantId;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
    private LocalDateTime deletedTime;
}
```

删除时间只在实体存在 `deletedTime` 属性时补充，列名服从该属性的映射。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `platform.mybatis.default-schema` | `public` | 用于解析未限定 schema 的表名 |
| `MYBATIS_DEFAULT_SCHEMA` | `public` | 上述配置对应的环境变量 |

配置必须与连接的默认 schema/search_path 一致；动态修改 search_path 不会自动更新插件配置。多 schema 场景建议同时限定实体和 SQL 的表名。引号内的大小写、点和转义双引号会保留，不同 schema 的同名表分别匹配。

**当前兼容策略：** 没有租户上下文时使用租户 `1`。后台任务需要主动建立、清理正确的 `UserContext`，插件不会自动传播异步上下文。

## 忽略某个方法或 Mapper

只关闭自定义逻辑删除，保留租户隔离：

```java
@InterceptorIgnore(others = {"logicDelete@true"})
@Select("SELECT * FROM app_agent WHERE deleted = 1")
List<AppAgent> selectDeleted();
```

`logicDelete@true` 的格式是 `插件标识@是否忽略`。同一注解也可以放在整个 Mapper 接口上；现有 `TrashMapper` 已这样配置，供查询回收站、恢复和物理删除使用。

```java
@Mapper
@InterceptorIgnore(others = {"logicDelete@true"})
public interface TrashMapper {
    @Update("UPDATE app_agent SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restore(@Param("id") Long id);

    @Delete("DELETE FROM app_agent WHERE id = #{id}")
    int purge(@Param("id") Long id);
}
```

注意：

- 注解放在 Mapper 接口或方法上，Service 上不生效。
- 该开关不关闭 MyBatis-Plus 内置方法生成的 `@TableLogic` SQL；恢复和物理删除应使用自定义 SQL。
- 忽略租户使用独立的 `@InterceptorIgnore(tenantLine = "true")`，不要与回收站逻辑删除豁免混为一体。
- 逻辑删除单独豁免后，跨租户写入校验与租户条件仍然执行。

## SQL 行为

以下以当前租户 `2`、正常值 `0`、删除值 `1` 为例。

| SQL | 行为 |
| --- | --- |
| SELECT | 追加当前租户、未删除条件；分页统计使用相同过滤规则 |
| UPDATE | 过滤目标表、WHERE/SET 子查询及 FROM/JOIN 来源；禁止修改为其他租户 |
| 单表 DELETE | 逻辑删除表转换为 UPDATE，标记删除并补时间；其他表物理删除，仍过滤来源子查询 |
| INSERT VALUES | 缺少租户/删除字段时补当前租户与正常值，支持批量行 |
| 显式租户字段 | 校验字面量及真实 MyBatis 参数；与当前租户不符或无法验证时拒绝 |
| INSERT SELECT | 始终过滤来源，包括目标豁免或已包含租户/删除字段时；直接投影的租户字段统一写当前租户 |
| ON CONFLICT DO UPDATE | 更新分支过滤目标租户和未删除状态，保留原 WHERE；逻辑删除赋值也会补时间 |
| ON CONFLICT DO NOTHING | 保留冲突时不更新的语义，插入值仍受校验 |

示例：

```sql
-- 原 SQL
DELETE FROM app_agent WHERE id = ?;

-- 改写后（条件顺序可能不同）
UPDATE app_agent
SET deleted = 1, deleted_time = CURRENT_TIMESTAMP
WHERE id = ? AND app_agent.tenant_id = 2 AND deleted = 0;
```

已有效约束当前表的 `deleted = 0` 不会重复追加。AND 任一分支具备约束即可，OR 要求所有分支都具备约束；子查询按各自作用域判断。纯内连接可以复用 WHERE 条件；外连接按保留侧语义保守处理，不追求全局去重。

SIMPLE、REUSE、BATCH 均在实际 SQL 执行前处理。语句复用前逐条校验参数；租户切换产生相应的 SQL 复用键，不沿用上一租户的常量条件。

## 支持范围与限制

支持别名、INNER/LEFT/RIGHT JOIN、UNION、普通/递归/嵌套 CTE、同名 CTE 遮蔽，以及 WHERE、SET、ON、HAVING、ORDER BY、GROUP BY、DISTINCT ON、LIMIT/OFFSET、窗口和 RETURNING 中的来源子查询。

| 场景 | 当前约束 |
| --- | --- |
| FULL/NATURAL/CROSS JOIN | 明确拒绝；CROSS JOIN 可改写为 INNER JOIN ... ON 1 = 1 |
| DELETE USING/JOIN | 明确拒绝自动隔离，可改用 WHERE 子查询 |
| 逻辑 DELETE 的 RETURNING/LIMIT 等复杂形式 | 明确拒绝，改用显式 UPDATE；物理 DELETE 的 RETURNING 子查询会被过滤 |
| MySQL ON DUPLICATE KEY UPDATE | 对受保护表明确拒绝，当前只支持 PostgreSQL 的安全 UPSERT |
| 租户表 INSERT 无列清单 | 明确拒绝；显式租户列的 INSERT SELECT 也要求明确投影，不能使用 SELECT * |
| `SET tenant_id = EXCLUDED.tenant_id` 等表达式 | 不作为可验证的租户赋值；省略无需修改的租户字段 |
| 参数化或计算得到的 UPDATE 删除标记 | 不保证自动补删除时间，业务应显式设置 `deleted_time` |
| 未注册实体元数据的表 | 默认不启用逻辑删除，可通过 `LogicDeleteHandler` 扩展 |

插件仅覆盖上述 MyBatis SQL 路径，不覆盖原生 JDBC、MERGE/TRUNCATE、存储过程等路径。SQL 解析或受保护语法不支持时，不会通过捕获异常静默放行。

## 运行测试

在仓库根目录运行，不需要启动 Spring 应用：

```powershell
mvn -pl agent-web -am test '-Dtest=LogicDeleteInnerInterceptorTest,LogicDeleteBoundaryTest,LogicDeleteSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

PostgreSQL 集成测试使用专用测试库，连接用户为 `postgres`：

```powershell
$env:LOGIC_DELETE_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5432/agent_plugin_test'
$env:LOGIC_DELETE_TEST_DB_PASSWORD = '<测试库密码>'
mvn -pl agent-web -am test '-Dtest=LogicDeleteInnerInterceptorTest,LogicDeleteBoundaryTest,LogicDeleteSchemaTest,LogicDeletePostgresTest,LogicDeleteSchemaPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

未设置测试连接地址时跳过实库测试。测试使用临时表或事务内 schema/表，结束时回滚并关闭连接，不读取项目业务库配置。测试覆盖参数绑定、SIMPLE/REUSE/BATCH、租户切换、回收站豁免、跨 schema 同名表及引号名称。