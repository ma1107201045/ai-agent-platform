# SQL 逻辑删除插件

`LogicDeleteInnerInterceptor` 与租户拦截器同级，继承 `BaseMultiTableInnerInterceptor`
并实现 `InnerInterceptor`，复用公共基类的 SQL AST 遍历能力，
在租户拦截器之后、分页拦截器之前注册。对内置方法、XML、注解 SQL 统一生效。

- SELECT 自动添加 `deleted = 0`，支持别名、JOIN、子查询、UNION、CTE。
- UPDATE 自动添加 `deleted = 0`，避免普通更新修改已删除记录。
- 标准单表 DELETE 转为 `UPDATE ... SET deleted = 1, deleted_time = CURRENT_TIMESTAMP`，
  保留原 WHERE、参数顺序及租户约束，并追加 `deleted = 0`。
- INSERT 显式列清单缺少 `deleted` 时自动补充 `0`，支持单条、批量 VALUES 和 INSERT SELECT
  （包括 UNION 各分支）。已经指定删除字段时保留原值，不影响参数顺序。
  无列清单的 INSERT 保持原样；补值不改变 SELECT 来源的过滤规则或 UPSERT 更新分支。
- 仅处理 `LogicDeleteHandler.TABLES` 中已经具有删除字段的十张表。
  新表应先完成数据库迁移，再加入清单；字段名和值可通过 handler 扩展。
- 兼容现有 `@TableLogic`；通过 AST 识别当前作用域中已有的未删除等值条件，避免重复追加。
  AND 任一分支具有约束即可，OR 则要求所有分支都具有约束。按表别名分别判断，
  子查询独立处理，不把子查询的条件当作外层约束；参数值未知时仍追加过滤。
  多表查询中无表限定的列无法可靠归属，保守地保留追加条件。

回收站等需要查询已删除记录、恢复或物理删除的 Mapper/方法使用：

```java
@InterceptorIgnore(others = {"logicDelete@true"})
```

该配置仅跳过逻辑删除插件，仍执行租户隔离。现有 `TrashMapper` 已配置。
请勿用 `tenantLine = "true"` 代替；租户插件的跳过配置不会关闭逻辑删除。

DELETE 的 USING/JOIN/RETURNING/LIMIT 等非标准单表形式会明确报错，
防止转换时丢失语义。此类需求应编写显式 UPDATE；确需物理删除时单独豁免。
插件依赖项目当前的 MyBatis-Plus 3.5.5 / JSqlParser 4.6，升级时应运行回归测试：

```shell
mvn -pl agent-web -am test -Dtest=LogicDeleteInnerInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false
```
