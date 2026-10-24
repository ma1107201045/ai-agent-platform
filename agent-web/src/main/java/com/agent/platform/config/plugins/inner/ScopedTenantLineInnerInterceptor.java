package com.agent.platform.config.plugins.inner;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.values.ValuesStatement;

/**
 * 保留原生租户行为，补齐 CTE 引用及嵌套 WITH 的作用域识别。
 */
public class ScopedTenantLineInnerInterceptor extends TenantLineInnerInterceptor {
    private final CteScope ctes = new CteScope();

    public ScopedTenantLineInnerInterceptor(TenantLineHandler handler) {
        super(handler);
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
    protected void processSelectBody(SelectBody body, String whereSegment) {
        if (body instanceof ValuesStatement values) {
            if (values.getExpressions() != null) {
                values.getExpressions().accept(new ExpressionVisitorAdapter() {
                    @Override
                    public void visit(SubSelect select) {
                        processSubSelect(select);
                    }
                });
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
            expression.accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(SubSelect select) {
                    processSubSelect(select);
                }
            });
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
        // 即使目标已包含 tenant_id，来源查询仍须执行租户过滤。
        if (insert.getSelect() != null) {
            processSelect(insert.getSelect(), index, sql, obj);
        }
        var handler = getTenantLineHandler();
        if (handler.ignoreTable(insert.getTable().getName()) || insert.getColumns() == null
                || insert.getColumns().isEmpty()
                || handler.ignoreInsert(insert.getColumns(), handler.getTenantIdColumn())) {
            return;
        }
        if (insert.getSelect() == null) {
            throw new UnsupportedOperationException("租户字段自动填充不支持此 INSERT 形式");
        }
        InsertValueSupport.append(insert.getSelect().getSelectBody(), insert.getColumns().size(), handler::getTenantId);
        insert.getColumns().add(new Column(handler.getTenantIdColumn()));
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        ctes.process(update.getWithItemsList(), body -> processSelectBody(body, null),
                () -> processUpdateBody(update));
    }

    private void processUpdateBody(Update update) {
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
    protected Column getAliasColumn(Table table) {
        String qualifier = table.getAlias() == null ? table.getFullyQualifiedName() : table.getAlias().getName();
        return new Column(qualifier + "." + getTenantLineHandler().getTenantIdColumn());
    }
}
