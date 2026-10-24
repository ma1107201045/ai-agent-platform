package com.agent.platform.config.plugins.inner.other;

import java.util.function.Consumer;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * JSqlParser 4.6 的窗口表达式默认 visitor 存在空指针及遗漏分区、FILTER 的问题。
 */
public final class SubqueryExpressionVisitor extends ExpressionVisitorAdapter {
    private final Consumer<SubSelect> visitor;

    public SubqueryExpressionVisitor(Consumer<SubSelect> visitor) {
        this.visitor = visitor;
    }

    @Override
    public void visit(SubSelect select) {
        visitor.accept(select);
    }

    private void expression(Expression value) {
        if (value != null) value.accept(this);
    }

    @Override
    public void visit(AnalyticExpression value) {
        expression(value.getExpression());
        expression(value.getDefaultValue());
        expression(value.getOffset());
        expression(value.getKeep());
        expression(value.getFilterExpression());
        if (value.getPartitionExpressionList() != null) value.getPartitionExpressionList().accept(this);
        if (value.getOrderByElements() != null)
            value.getOrderByElements().forEach(order -> expression(order.getExpression()));
        if (value.getFuncOrderBy() != null) value.getFuncOrderBy().forEach(order -> expression(order.getExpression()));
        var window = value.getWindowElement();
        if (window != null) {
            if (window.getOffset() != null) expression(window.getOffset().getExpression());
            if (window.getRange() != null) {
                if (window.getRange().getStart() != null) expression(window.getRange().getStart().getExpression());
                if (window.getRange().getEnd() != null) expression(window.getRange().getEnd().getExpression());
            }
        }
    }
}
