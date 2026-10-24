# SQL 逻辑删除插件

`LogicDeleteInnerInterceptor` 与租户拦截器同级，继承 `BaseMultiTableInnerInterceptor`
并实现 `InnerInterceptor`，复用公共基类的 SQL AST 遍历能力，
在租户拦截器之后、分页拦截器之前注册。对内置方法、XML、注解 SQL 统一生效。

- 默认 SELECT 自动添加 `deleted = 0`，支持别名、INNER/LEFT/RIGHT JOIN、子查询、UNION、CTE。
- UPDATE 同时过滤目标表、WHERE/SET 子查询和 FROM/JOIN 来源表；即使目标没有逻辑删除字段，
  也会处理来源表。多表条件使用表名或别名限定，避免字段歧义。
- 标准单表 DELETE 转为 `UPDATE ... SET deleted = 1, deleted_time = CURRENT_TIMESTAMP`，
  保留原 WHERE、参数顺序及租户约束，并追加 `deleted = 0`。
- INSERT 显式列清单缺少 `deleted` 时自动补充 `0`，支持单条、批量 VALUES 和 INSERT SELECT
  （包括 UNION 各分支）。已经指定删除字段时保留原值，不影响参数顺序。
  INSERT SELECT 始终过滤来源查询，包括已经显式指定 `deleted` 或目标没有逻辑删除字段的情况。
  逻辑删除插件单独使用时无列清单不补列；完整拦截链对租户表要求显式列清单。
- PostgreSQL `ON CONFLICT DO UPDATE` 的更新分支同时追加目标租户和未删除条件，
  不能更新其他租户或已删除的冲突记录；原有 WHERE 与子查询仍保留并过滤。
  分支内逻辑删除同样补充删除时间。`DO NOTHING` 保持原有语义。
- 删除字段、正常值和删除值来自已注册实体的 `TableInfo`，与 `@TableLogic` 及 MyBatis-Plus
  全局配置一致，不再维护固定表清单。支持字符串标记和 `NULL` 正常值（生成 `IS NULL`）。
  未注册 MyBatis-Plus 实体元数据的表默认不启用逻辑删除；可通过 handler 扩展。
- 有 `deletedTime` 实体属性时使用其映射列补充删除时间；自定义 DELETE 和内置逻辑删除
  UPDATE 都会补充 `CURRENT_TIMESTAMP`。调用方已显式赋值时保留原值；普通更新不补时间。
  参数化 `SET deleted = ?` 无法仅凭 AST 判断运行值，需要业务显式写入删除时间。
- 兼容现有 `@TableLogic`；通过 AST 识别当前作用域中已有的未删除等值条件，避免重复追加。
  AND 任一分支具有约束即可，OR 则要求所有分支都具有约束。按表别名分别判断，
  子查询独立处理，不把子查询的条件当作外层约束；参数值未知时仍追加过滤。
  多表查询中无表限定的列无法可靠归属，保守地保留追加条件。
  纯内连接可复用 WHERE 中已有条件，避免再向 ON 重复追加；混合外连接保守地局部去重。
- CTE 定义与引用分开处理，支持同名实体遮蔽、递归和嵌套作用域；引用 CTE 时不要求输出
  `deleted` 或 `tenant_id`。作用域线程状态在正常结束、异常退出时都会清理。

`ScopedTenantLineInnerInterceptor` 扩展原生租户插件，使用同样的 CTE 作用域识别，
并补齐 INSERT SELECT、UPDATE FROM 和子查询的来源租户过滤。目标显式包含 `tenant_id`
时也过滤来源；未包含时补当前租户常量，支持 VALUES、UNION 和 CTE。
显式写入或修改 `tenant_id` 时必须等于当前租户。字面量直接校验，MyBatis 参数在执行前
通过 BoundSql/ParameterMapping 读取，兼容实体、Map 和 foreach 额外参数，不改变 JDBC 绑定顺序。
INSERT SELECT 直接投影的租户列统一替换为当前租户常量；其他无法验证的租户表达式会拒绝。
更新时应省略无需修改的租户字段（包括冗余的 `tenant_id = EXCLUDED.tenant_id`）。
无上下文时仍沿用项目租户 1 的默认策略。

回收站等需要查询已删除记录、恢复或物理删除的 Mapper/方法使用：

```java
@InterceptorIgnore(others = {"logicDelete@true"})
```

该配置仅跳过逻辑删除插件，仍执行租户隔离。现有 `TrashMapper` 已配置。
请勿用 `tenantLine = "true"` 代替；租户插件的跳过配置不会关闭逻辑删除。

DELETE 的 USING/JOIN/RETURNING/LIMIT 等非标准单表形式会明确报错，
防止转换时丢失语义。此类需求应编写显式 UPDATE；确需物理删除时单独豁免。
FULL/NATURAL/CROSS JOIN 不在公共解析基类的可靠处理范围内，两个插件都会明确报错，
避免静默漏过滤或改变外连接语义。CROSS JOIN 可等价改写成 INNER JOIN ... ON 1 = 1。
物理 DELETE 的 WHERE 子查询也执行租户与逻辑删除过滤；DELETE USING/JOIN 暂不支持自动隔离。
来源子查询遍历覆盖 WHERE、SET、ON、HAVING、ORDER BY、GROUP BY、DISTINCT ON、
LIMIT/OFFSET、窗口定义及 INSERT/UPDATE/物理 DELETE 的 RETURNING。
MySQL `ON DUPLICATE KEY UPDATE` 无法安全追加目标行过滤条件，对受保护表明确拒绝。
插件依赖项目当前的 MyBatis-Plus 3.5.5 / JSqlParser 4.6，升级时应运行回归测试：

```shell
mvn -pl agent-web -am test -Dtest=LogicDeleteInnerInterceptorTest,LogicDeleteBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

PostgreSQL 集成测试通过完整 MyBatis 拦截链执行真实 SQL，验证实际返回行、修改行数和删除时间。
使用专用测试库，设置 `LOGIC_DELETE_TEST_JDBC_URL` 和 `LOGIC_DELETE_TEST_DB_PASSWORD`
（用户 `postgres`），再将 `LogicDeletePostgresTest` 加入 `-Dtest`。未设置连接地址时跳过该类。
每项测试仅使用事务内临时表，结束后回滚并关闭连接；无需修改项目业务库配置。
