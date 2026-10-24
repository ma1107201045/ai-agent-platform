package com.agent.platform.config.plugins.inner;

import com.agent.platform.config.plugins.inner.other.CteScope;
import com.agent.platform.config.plugins.inner.other.InsertValueSupport;
import com.agent.platform.config.plugins.inner.other.QueryJoinSupport;
import com.agent.platform.config.plugins.inner.other.SelectExpressionSupport;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import com.agent.platform.config.plugins.inner.other.SubqueryExpressionVisitor;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.values.ValuesStatement;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import java.sql.Connection;
import java.math.BigDecimal;

/**
 * 扩展原生租户插件，补齐 CTE 作用域与 INSERT SELECT、UPDATE 来源查询的租户过滤。
 */
public class ScopedTenantLineInnerInterceptor extends TenantLineInnerInterceptor {
    private final CteScope ctes = new CteScope();
    private record Parameters(Configuration configuration, BoundSql sql) {}
    private final ThreadLocal<Parameters> parameters = new ThreadLocal<>();

    public ScopedTenantLineInnerInterceptor(TenantLineHandler handler) {
        super(handler);
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        var statement = PluginUtils.mpStatementHandler(sh);
        Parameters previous = parameters.get();
        parameters.set(new Parameters(statement.mappedStatement().getConfiguration(), sh.getBoundSql()));
        try {
            super.beforePrepare(sh, connection, transactionTimeout);
        } finally {
            if (previous == null) parameters.remove();
            else parameters.set(previous);
        }
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        return ctes.isCte(table) ? null : super.buildTableExpression(table, where, whereSegment);
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        ctes.process(select.getWithItemsList(), body -> processSelectBody(body, null),
                () -> processSelectBody(select.getSelectBody(), null));
    }

    @Override
    protected void processPlainSelect(PlainSelect select, String whereSegment) {
        QueryJoinSupport.validate(select.getJoins());
        if (select.getJoins() != null) {
            select.getJoins().forEach(join -> join.getOnExpressions().forEach(on -> processWhereSubSelect(on, null)));
        }
        super.processPlainSelect(select, whereSegment);
        processWhereSubSelect(select.getHaving(), null);
        SelectExpressionSupport.visit(select, expression -> processWhereSubSelect(expression, null));
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
        ctes.process(select.getWithItemsList(), body -> processSelectBody(body, null),
                () -> processSelectBody(select.getSelectBody(), null));
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
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        ctes.process(insert.getWithItemsList(), body -> processSelectBody(body, null),
                () -> processInsertBody(insert, index, sql, obj));
    }

    private void processInsertBody(Insert insert, int index, String sql, Object obj) {
        if (insert.getReturningExpressionList() != null) {
            insert.getReturningExpressionList().forEach(item -> processSelectItem(item, null));
        }
        // 即使目标已包含 tenant_id，来源查询仍须执行租户过滤。
        if (insert.getSelect() != null) {
            processSelect(insert.getSelect(), index, sql, obj);
        }
        var handler = getTenantLineHandler();
        if (handler.ignoreTable(insert.getTable().getName())) {
            return;
        }
        if (insert.isUseDuplicate()) {
            throw new UnsupportedOperationException("租户表不支持 ON DUPLICATE KEY UPDATE，请使用带租户过滤的显式更新");
        }
        if (insert.getColumns() == null || insert.getColumns().isEmpty()) {
            throw new UnsupportedOperationException("租户表 INSERT 必须提供显式列清单");
        }
        if (insert.getSelect() == null) {
            throw new UnsupportedOperationException("租户字段自动填充不支持此 INSERT 形式");
        }
        boolean hasTenant = false;
        for (int i = 0; i < insert.getColumns().size(); i++) {
            if (tenantColumn(insert.getColumns().get(i))) {
                hasTenant = true;
                InsertValueSupport.mapColumn(insert.getSelect().getSelectBody(), i, insert.getColumns().size(), value -> {
                    // INSERT SELECT 直接投影租户字段时统一写当前租户常量，不复制来源归属。
                    if (value instanceof Column column && tenantColumn(column)) return handler.getTenantId();
                    requireCurrentTenant(value);
                    return value;
                });
            }
        }
        if (!hasTenant) {
            InsertValueSupport.append(insert.getSelect().getSelectBody(), insert.getColumns().size(), handler::getTenantId);
            insert.getColumns().add(new Column(handler.getTenantIdColumn()));
        }
        var conflict = insert.getConflictAction();
        if (conflict != null && !conflict.getUpdateSets().isEmpty()) {
            Update update = new Update();
            update.setTable(insert.getTable());
            update.getUpdateSets().addAll(conflict.getUpdateSets());
            update.setWhere(conflict.getWhereExpression());
            processUpdateBody(update);
            conflict.setWhereExpression(update.getWhere());
        }
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        ctes.process(update.getWithItemsList(), body -> processSelectBody(body, null),
                () -> processUpdateBody(update));
    }

    private void processUpdateBody(Update update) {
        if (update.getReturningExpressionList() != null) {
            update.getReturningExpressionList().forEach(item -> processSelectItem(item, null));
        }
        if (!getTenantLineHandler().ignoreTable(update.getTable().getName())) {
            update.getUpdateSets().forEach(set -> {
                for (int i = 0; i < set.getColumns().size(); i++) {
                    if (tenantColumn(set.getColumns().get(i))) {
                        if (i >= set.getExpressions().size()) {
                            throw new UnsupportedOperationException("不能通过元组表达式修改租户归属");
                        }
                        requireCurrentTenant(set.getExpressions().get(i));
                    }
                }
            });
        }
        processWhereSubSelect(update.getWhere(), null);
        update.getUpdateSets().forEach(set -> set.getExpressions()
                .forEach(expression -> processWhereSubSelect(expression, null)));
        if (update.getFromItem() != null) {
            PlainSelect source = new PlainSelect();
            source.setFromItem(update.getFromItem());
            source.setJoins(update.getJoins());
            source.setWhere(update.getWhere());
            processPlainSelect(source, null);
            update.setWhere(source.getWhere());
        }
        if (update.getStartJoins() != null && !update.getStartJoins().isEmpty()) {
            PlainSelect source = new PlainSelect();
            source.setFromItem(update.getTable());
            source.setJoins(update.getStartJoins());
            source.setWhere(update.getWhere());
            processPlainSelect(source, null);
            update.setWhere(source.getWhere());
        }
        // 更新目标始终为实体表，即便同名 CTE 可见也不豁免目标租户条件。
        if (!getTenantLineHandler().ignoreTable(update.getTable().getName())) {
            Expression condition = new EqualsTo(getAliasColumn(update.getTable()), getTenantLineHandler().getTenantId());
            Expression where = update.getWhere();
            update.setWhere(where == null ? condition : new AndExpression(
                    where instanceof OrExpression ? new Parenthesis(where) : where, condition));
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        ctes.process(delete.getWithItemsList(), body -> processSelectBody(body, null), () -> {
            if ((delete.getUsingList() != null && !delete.getUsingList().isEmpty())
                    || (delete.getJoins() != null && !delete.getJoins().isEmpty())) {
                throw new UnsupportedOperationException("暂不支持自动隔离 DELETE USING/JOIN，请改用 WHERE 子查询");
            }
            processWhereSubSelect(delete.getWhere(), null);
            if (delete.getReturningExpressionList() != null) {
                delete.getReturningExpressionList().forEach(item -> processSelectItem(item, null));
            }
            super.processDelete(delete, index, sql, obj);
        });
    }

    private boolean tenantColumn(Column column) {
        return column.getColumnName().replace("\"", "").replace("`", "")
                .equalsIgnoreCase(getTenantLineHandler().getTenantIdColumn());
    }

    private void requireCurrentTenant(Expression expression) {
        while (expression instanceof Parenthesis parenthesis) expression = parenthesis.getExpression();
        Object value = expression instanceof LongValue number ? number.getValue()
                : expression instanceof StringValue string ? string.getValue() : null;
        if (expression instanceof JdbcParameter parameter) {
            Parameters context = parameters.get();
            Integer index = parameter.getIndex();
            if (context != null && index != null && index > 0 && index <= context.sql().getParameterMappings().size()) {
                String property = context.sql().getParameterMappings().get(index - 1).getProperty();
                Object object = context.sql().getParameterObject();
                if (context.sql().hasAdditionalParameter(property)) value = context.sql().getAdditionalParameter(property);
                else if (object != null && context.configuration().getTypeHandlerRegistry().hasTypeHandler(object.getClass())) value = object;
                else if (object != null) value = context.configuration().newMetaObject(object).getValue(property);
            }
        }
        try {
            if (value != null && new BigDecimal(value.toString()).compareTo(
                    new BigDecimal(getTenantLineHandler().getTenantId().toString())) == 0) return;
        } catch (NumberFormatException ignored) {
            // 无法静态验证的表达式不能用于修改租户归属。
        }
        throw new IllegalArgumentException("租户字段必须等于当前租户；禁止跨租户写入或使用无法校验的租户表达式");
    }

    @Override
    protected Column getAliasColumn(Table table) {
        String qualifier = table.getAlias() == null ? table.getFullyQualifiedName() : table.getAlias().getName();
        return new Column(qualifier + "." + getTenantLineHandler().getTenantIdColumn());
    }
}
