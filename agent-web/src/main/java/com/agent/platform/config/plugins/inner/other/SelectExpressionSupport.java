package com.agent.platform.config.plugins.inner.other;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;

import java.util.function.Consumer;

/**
 * 公共基类未遍历的 SELECT 表达式位置；子查询作用域由调用方处理。
 */
public final class SelectExpressionSupport {
    private SelectExpressionSupport() {
    }

    public static void visit(PlainSelect select, Consumer<Expression> visitor) {
        if (select.getOrderByElements() != null) {
            select.getOrderByElements().forEach(order -> visitor.accept(order.getExpression()));
        }
        if (select.getGroupBy() != null) {
            if (select.getGroupBy().getGroupByExpressionList() != null) {
                select.getGroupBy().getGroupByExpressionList().getExpressions().forEach(visitor);
            }
            if (select.getGroupBy().getGroupingSets() != null) {
                for (Object grouping : select.getGroupBy().getGroupingSets()) {
                    if (grouping instanceof Expression expression) visitor.accept(expression);
                    else if (grouping instanceof ExpressionList list) list.getExpressions().forEach(visitor);
                }
            }
        }
        if (select.getDistinct() != null && select.getDistinct().getOnSelectItems() != null) {
            select.getDistinct().getOnSelectItems().forEach(item -> {
                if (item instanceof SelectExpressionItem expression) visitor.accept(expression.getExpression());
            });
        }
        if (select.getLimit() != null) {
            visitor.accept(select.getLimit().getOffset());
            visitor.accept(select.getLimit().getRowCount());
        }
        if (select.getOffset() != null) visitor.accept(select.getOffset().getOffset());
        if (select.getTop() != null) visitor.accept(select.getTop().getExpression());
    }
}
