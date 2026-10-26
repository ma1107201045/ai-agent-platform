package com.agent.platform.config.plugins.inner.other;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.values.ValuesStatement;

import java.util.List;
import java.util.function.Consumer;

/**
 * 共享遍历机制；实体表过滤与条件生成仍由各拦截器负责。每个拦截器单独持有实例。
 */
public final class ScopedSelectSupport {
    private final CteScope ctes = new CteScope();
    private final Consumer<SelectBody> processBody;

    public ScopedSelectSupport(Consumer<SelectBody> processBody) {
        this.processBody = processBody;
    }

    public void withCtes(List<WithItem> items, Runnable body) {
        ctes.process(items, processBody, body);
    }

    public boolean isCte(Table table) {
        return ctes.isCte(table);
    }

    public void select(Select select) {
        withCtes(select.getWithItemsList(), () -> processBody.accept(select.getSelectBody()));
    }

    private void subSelect(SubSelect select) {
        withCtes(select.getWithItemsList(), () -> processBody.accept(select.getSelectBody()));
    }

    public void expression(Expression expression) {
        if (expression != null) expression.accept(new SubqueryExpressionVisitor(this::subSelect));
    }

    public void item(SelectItem item) {
        if (item instanceof SelectExpressionItem expression) expression(expression.getExpression());
    }

    public void items(List<SelectItem> items) {
        if (items != null) items.forEach(this::item);
    }

    public boolean values(SelectBody body) {
        if (!(body instanceof ValuesStatement values)) return false;
        if (values.getExpressions() != null) {
            values.getExpressions().accept(new SubqueryExpressionVisitor(this::subSelect));
        }
        return true;
    }

    public boolean from(FromItem item) {
        if (item instanceof SubSelect select) subSelect(select);
        else if (item instanceof LateralSubSelect lateral) subSelect(lateral.getSubSelect());
        else return false;
        return true;
    }

    public void plainSelect(PlainSelect select, Runnable processTables) {
        QueryJoinSupport.validate(select.getJoins());
        if (select.getJoins() != null) {
            select.getJoins().forEach(join -> join.getOnExpressions().forEach(this::expression));
        }
        processTables.run();
        expression(select.getHaving());
        SelectExpressionSupport.visit(select, this::expression);
    }
}
