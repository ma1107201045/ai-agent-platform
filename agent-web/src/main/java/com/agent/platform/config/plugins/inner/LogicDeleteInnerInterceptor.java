package com.agent.platform.config.plugins.inner;

import com.agent.platform.config.plugins.handler.LogicDeleteHandler;
import com.agent.platform.config.plugins.inner.other.CteScope;
import com.agent.platform.config.plugins.inner.other.InsertValueSupport;
import com.agent.platform.config.plugins.inner.other.QueryJoinSupport;
import com.agent.platform.config.plugins.inner.other.SelectExpressionSupport;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.BaseMultiTableInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import com.agent.platform.config.plugins.inner.other.SubqueryExpressionVisitor;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.values.ValuesStatement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.util.Objects;
import java.util.Locale;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 与租户插件同级，复用公共基类的别名、JOIN、子查询和 UNION 遍历逻辑。
 * XML、注解和内置 SQL 均在执行前处理；INSERT 缺少删除标记时补充正常值。
 * 使用 {@code @InterceptorIgnore(others = {"logicDelete@true"})} 可单独跳过此插件。
 */
public class LogicDeleteInnerInterceptor extends BaseMultiTableInnerInterceptor implements InnerInterceptor {
    private static final String SINGLE_TABLE_SCOPE = "logicDelete:singleTable";
    private static final String MULTI_TABLE_SCOPE = "logicDelete:multiTable";
    private final LogicDeleteHandler handler;
    private final CteScope ctes = new CteScope();
    private final ThreadLocal<Deque<Expression>> innerJoinPredicates = new ThreadLocal<>();

    public LogicDeleteInnerInterceptor(LogicDeleteHandler handler) {
        this.handler = Objects.requireNonNull(handler, "logicDeleteHandler");
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        if (!InterceptorIgnoreHelper.willIgnoreOthersByKey(ms.getId(), "logicDelete")) {
            PluginUtils.mpBoundSql(boundSql).sql(parserSingle(boundSql.getSql(), null));
        }
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        if ((ms.getSqlCommandType() == SqlCommandType.INSERT || ms.getSqlCommandType() == SqlCommandType.UPDATE || ms.getSqlCommandType() == SqlCommandType.DELETE) && !InterceptorIgnoreHelper.willIgnoreOthersByKey(ms.getId(), "logicDelete")) {
            PluginUtils.MPBoundSql boundSql = mpSh.mPBoundSql();
            boundSql.sql(parserMulti(boundSql.sql(), null));
        }
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        ctes.process(insert.getWithItemsList(), body -> processSelectBody(body, null), () -> processInsertBody(insert));
    }

    private void processInsertBody(Insert insert) {
        if (insert.getReturningExpressionList() != null) {
            insert.getReturningExpressionList().forEach(item -> processSelectItem(item, null));
        }
        if (insert.getSelect() != null) {
            processSelect(insert.getSelect(), 0, null, null);
        }
        String tableName = insert.getTable().getName();
        var conflict = insert.getConflictAction();
        if (insert.isUseDuplicate() && !handler.ignoreTable(tableName)) {
            throw new UnsupportedOperationException("逻辑删除表不支持 ON DUPLICATE KEY UPDATE，请使用显式更新");
        }
        if (conflict != null && !conflict.getUpdateSets().isEmpty()) {
            Update update = new Update();
            update.setTable(insert.getTable());
            update.getUpdateSets().addAll(conflict.getUpdateSets());
            update.setWhere(conflict.getWhereExpression());
            processUpdateBody(update, true);
            conflict.setWhereExpression(update.getWhere());
            conflict.getUpdateSets().clear();
            conflict.getUpdateSets().addAll(update.getUpdateSets());
        }
        if (handler.ignoreTable(tableName) || insert.getColumns() == null || insert.getColumns().isEmpty() || insert.getColumns().stream().anyMatch(column -> sameIdentifier(column.getColumnName(), handler.getLogicDeleteColumn(tableName)))) {
            return;
        }
        if (insert.getSelect() == null) {
            throw new UnsupportedOperationException("逻辑删除字段自动填充不支持此 INSERT 形式，请显式指定字段");
        }
        InsertValueSupport.append(insert.getSelect().getSelectBody(), insert.getColumns().size(), () -> handler.getNotDeletedValue(tableName));
        insert.getColumns().add(new Column(handler.getLogicDeleteColumn(tableName)));
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        ctes.process(select.getWithItemsList(), body -> processSelectBody(body, null), () -> processSelectBody(select.getSelectBody(), null));
    }

    @Override
    protected void processSelectBody(SelectBody body, String whereSegment) {
        if (body instanceof ValuesStatement values) {
            if (values.getExpressions() != null) {
                values.getExpressions().accept(new SubqueryExpressionVisitor(this::processSubSelect));
            }
            return;
        }
        super.processSelectBody(body, whereSegment);
    }

    private void processSubSelect(SubSelect select) {
        ctes.process(select.getWithItemsList(), body -> processSelectBody(body, null), () -> processSelectBody(select.getSelectBody(), null));
    }

    @Override
    protected void processWhereSubSelect(Expression expression, String whereSegment) {
        if (expression != null) {
            expression.accept(new SubqueryExpressionVisitor(this::processSubSelect));
        }
    }

    @Override
    protected void processOtherFromItem(FromItem item, String whereSegment) {
        if (item instanceof SubSelect select) {
            processSubSelect(select);
        } else if (item instanceof LateralSubSelect lateral) {
            processSubSelect(lateral.getSubSelect());
        } else {
            super.processOtherFromItem(item, whereSegment);
        }
    }

    @Override
    protected void processSelectItem(SelectItem item, String whereSegment) {
        if (item instanceof SelectExpressionItem expression) {
            processWhereSubSelect(expression.getExpression(), whereSegment);
        }
    }

    @Override
    protected void processFunction(Function function, String whereSegment) {
        processWhereSubSelect(function, whereSegment);
    }

    @Override
    protected void processPlainSelect(PlainSelect select, String whereSegment) {
        QueryJoinSupport.validate(select.getJoins());
        // 标记当前查询作用域；递归子查询会独立计算，不共享可变上下文。
        boolean singleTable = !MULTI_TABLE_SCOPE.equals(whereSegment) && select.getFromItem() instanceof Table
                && (select.getJoins() == null || select.getJoins().isEmpty());
        boolean innerOnly = QueryJoinSupport.innerOnly(select.getJoins());
        Deque<Expression> stack = innerJoinPredicates.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            innerJoinPredicates.set(stack);
        }
        // 仅纯内连接可复用 WHERE 约束；外连接不能将 ON 的过滤当作保留侧约束。
        stack.push(innerOnly && select.getWhere() != null ? select.getWhere() : new NullValue());
        try {
            if (select.getJoins() != null) {
                select.getJoins().forEach(join -> join.getOnExpressions().forEach(on -> processWhereSubSelect(on, null)));
            }
            super.processPlainSelect(select, singleTable ? SINGLE_TABLE_SCOPE : null);
            processWhereSubSelect(select.getHaving(), null);
            SelectExpressionSupport.visit(select, expression -> processWhereSubSelect(expression, null));
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                innerJoinPredicates.remove();
            }
        }
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        ctes.process(update.getWithItemsList(), body -> processSelectBody(body, null), () -> processUpdateBody(update));
    }

    private void processUpdateBody(Update update) {
        processUpdateBody(update, false);
    }

    private void processUpdateBody(Update update, boolean forceQualified) {
        if (update.getReturningExpressionList() != null) {
            update.getReturningExpressionList().forEach(item -> processSelectItem(item, null));
        }
        Table table = update.getTable();
        if (update.getUpdateSets() != null) {
            update.getUpdateSets().forEach(set -> set.getExpressions().forEach(expression -> processWhereSubSelect(expression, null)));
        }
        processWhereSubSelect(update.getWhere(), null);
        // 用临时 SELECT 复用公共基类的 FROM/JOIN 处理；只回写 WHERE，JOIN 节点直接共享。
        if (update.getFromItem() != null) {
            PlainSelect source = new PlainSelect();
            source.setFromItem(update.getFromItem());
            source.setJoins(update.getJoins());
            source.setWhere(update.getWhere());
            processPlainSelect(source, MULTI_TABLE_SCOPE);
            update.setWhere(source.getWhere());
        }
        if (update.getStartJoins() != null && !update.getStartJoins().isEmpty()) {
            PlainSelect joined = new PlainSelect();
            joined.setFromItem(table);
            joined.setJoins(update.getStartJoins());
            joined.setWhere(update.getWhere());
            processPlainSelect(joined, null);
            update.setWhere(joined.getWhere());
        }
        if (handler.ignoreTable(table.getName())) {
            return;
        }
        // ON CONFLICT 同时存在目标表与 EXCLUDED，过滤列必须限定到目标表。
        boolean singleTable = !forceQualified && update.getFromItem() == null && (update.getJoins() == null || update.getJoins().isEmpty()) && (update.getStartJoins() == null || update.getStartJoins().isEmpty());
        Expression condition = tableCondition(table, update.getWhere(), singleTable, false);
        if (condition != null) {
            update.setWhere(appendCondition(update.getWhere(), condition));
        }
        fillDeletedTime(update);
    }

    private Expression appendCondition(Expression where, Expression condition) {
        return where == null ? condition : new AndExpression(where instanceof OrExpression ? new Parenthesis(where) : where, condition);
    }

    private void fillDeletedTime(Update update) {
        String tableName = update.getTable().getName();
        String timeColumn = handler.getDeletedTimeColumn(tableName);
        if (timeColumn == null) {
            return;
        }
        boolean deleting = false;
        for (var set : update.getUpdateSets()) {
            for (int i = 0; i < set.getColumns().size(); i++) {
                String column = set.getColumns().get(i).getColumnName();
                if (sameIdentifier(column, timeColumn)) {
                    return;
                }
                if (sameIdentifier(column, handler.getLogicDeleteColumn(tableName)) && i < set.getExpressions().size()
                        && sameValue(set.getExpressions().get(i), handler.getDeletedValue(tableName))) {
                    deleting = true;
                }
            }
        }
        if (deleting) {
            update.addUpdateSet(new Column(timeColumn), new TimeKeyExpression("CURRENT_TIMESTAMP"));
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        // 启用逻辑删除的表已转换为 UPDATE；物理删除仍须过滤 WHERE 中的来源子查询。
        ctes.process(delete.getWithItemsList(), body -> processSelectBody(body, null), () -> {
            processWhereSubSelect(delete.getWhere(), null);
            if (delete.getReturningExpressionList() != null) {
                delete.getReturningExpressionList().forEach(item -> processSelectItem(item, null));
            }
        });
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        return tableCondition(table, where, SINGLE_TABLE_SCOPE.equals(whereSegment), true);
    }

    private Expression tableCondition(Table table, Expression where, boolean singleTable, boolean checkCte) {
        if ((checkCte && ctes.isCte(table)) || handler.ignoreTable(table.getName())) {
            return null;
        }
        if (guaranteesNotDeleted(where, table, singleTable)) {
            return null;
        }
        Deque<Expression> stack = innerJoinPredicates.get();
        if (checkCte && stack != null && !stack.isEmpty() && guaranteesNotDeleted(stack.peek(), table, false)) {
            return null;
        }
        String column = handler.getLogicDeleteColumn(table.getName());
        if (table.getAlias() != null) {
            column = table.getAlias().getName() + "." + column;
        } else if (!singleTable) {
            column = table.getFullyQualifiedName() + "." + column;
        }
        Expression value = handler.getNotDeletedValue(table.getName());
        if (value instanceof NullValue) {
            IsNullExpression isNull = new IsNullExpression();
            isNull.setLeftExpression(new Column(column));
            return isNull;
        }
        return new EqualsTo(new Column(column), value);
    }

    /**
     * 仅识别当前作用域中能保证成立的条件，不深入子查询、NOT 或其他运算符。
     */
    private boolean guaranteesNotDeleted(Expression expression, Table table, boolean allowUnqualified) {
        if (expression instanceof Parenthesis parenthesis) {
            return guaranteesNotDeleted(parenthesis.getExpression(), table, allowUnqualified);
        }
        if (expression instanceof AndExpression and) {
            return guaranteesNotDeleted(and.getLeftExpression(), table, allowUnqualified) || guaranteesNotDeleted(and.getRightExpression(), table, allowUnqualified);
        }
        if (expression instanceof OrExpression or) {
            // OR 的每个分支都必须保证未删除，才能省略追加条件。
            return guaranteesNotDeleted(or.getLeftExpression(), table, allowUnqualified) && guaranteesNotDeleted(or.getRightExpression(), table, allowUnqualified);
        }
        if (expression instanceof EqualsTo equals) {
            return matchesNotDeleted(equals.getLeftExpression(), equals.getRightExpression(), table, allowUnqualified) || matchesNotDeleted(equals.getRightExpression(), equals.getLeftExpression(), table, allowUnqualified);
        }
        if (expression instanceof IsNullExpression isNull && !isNull.isNot() && handler.getNotDeletedValue(table.getName()) instanceof NullValue) {
            return matchesColumn(isNull.getLeftExpression(), table, allowUnqualified);
        }
        return false;
    }

    private boolean matchesNotDeleted(Expression candidate, Expression value, Table table, boolean allowUnqualified) {
        return sameValue(value, handler.getNotDeletedValue(table.getName())) && matchesColumn(candidate, table, allowUnqualified);
    }

    private boolean sameValue(Expression value, Expression expected) {
        return (expected instanceof LongValue || expected instanceof StringValue || expected instanceof NullValue) && value.getClass() == expected.getClass() && value.toString().equals(expected.toString());
    }

    private boolean matchesColumn(Expression candidate, Table table, boolean allowUnqualified) {
        if (!(candidate instanceof Column column) || !sameIdentifier(column.getColumnName(), handler.getLogicDeleteColumn(table.getName()))) {
            return false;
        }
        String qualifier = column.getTable() == null ? null : column.getTable().getFullyQualifiedName();
        if (qualifier == null || qualifier.isEmpty()) {
            return allowUnqualified;
        }
        if (table.getAlias() != null) {
            return sameIdentifier(qualifier, table.getAlias().getName());
        }
        return sameIdentifier(qualifier, table.getFullyQualifiedName()) || sameIdentifier(qualifier, table.getName());
    }

    private boolean sameIdentifier(String left, String right) {
        // PostgreSQL 双引号标识符区分大小写；保守匹配，无法确认时仍追加过滤条件。
        if (left.contains("\"") || right.contains("\"")) {
            String normalizedLeft = left.contains("\"") ? left.replace("\"", "") : left.toLowerCase(Locale.ROOT);
            String normalizedRight = right.contains("\"") ? right.replace("\"", "") : right.toLowerCase(Locale.ROOT);
            return normalizedLeft.equals(normalizedRight);
        }
        return left.equalsIgnoreCase(right);
    }

    @Override
    protected String processParser(Statement statement, int index, String sql, Object obj) {
        if (statement instanceof Delete delete && !handler.ignoreTable(delete.getTable().getName())) {
            // 只转换标准单表 DELETE。复杂方言显式报错，避免丢失 USING/JOIN/RETURNING 等语义。
            String expected = "DELETE FROM " + delete.getTable() + (delete.getWhere() == null ? "" : " WHERE " + delete.getWhere());
            if (!expected.equalsIgnoreCase(delete.toString())) {
                throw new UnsupportedOperationException("逻辑删除仅支持标准单表 DELETE，请改用显式 UPDATE：" + sql);
            }
            String tableName = delete.getTable().getName();
            String update = "UPDATE " + delete.getTable() + " SET " + handler.getLogicDeleteColumn(tableName) + " = " + handler.getDeletedValue(tableName);
            if (handler.getDeletedTimeColumn(tableName) != null) {
                update += ", " + handler.getDeletedTimeColumn(tableName) + " = CURRENT_TIMESTAMP";
            }
            update += delete.getWhere() == null ? "" : " WHERE " + delete.getWhere();
            try {
                statement = CCJSqlParserUtil.parse(update);
            } catch (net.sf.jsqlparser.JSQLParserException e) {
                throw new IllegalArgumentException("逻辑删除 SQL 转换失败", e);
            }
        }
        return super.processParser(statement, index, sql, obj);
    }
}
