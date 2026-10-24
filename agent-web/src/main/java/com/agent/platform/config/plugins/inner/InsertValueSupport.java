package com.agent.platform.config.plugins.inner;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.RowConstructor;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.MultiExpressionList;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.values.ValuesStatement;

import java.util.function.Supplier;

/**
 * 给 INSERT 的每行或每个 SELECT 分支追加一个值，不改变原有 JDBC 参数顺序。
 */
final class InsertValueSupport {
    private InsertValueSupport() {
    }

    static void append(SelectBody body, int columnCount, Supplier<Expression> value) {
        switch (body) {
            case PlainSelect select -> select.getSelectItems().add(new SelectExpressionItem(value.get()));
            case SetOperationList operations ->
                    operations.getSelects().forEach(branch -> append(branch, columnCount, value));
            case ValuesStatement statement -> {
                if (statement.getExpressions() instanceof MultiExpressionList rows) {
                    rows.getExpressionLists().forEach(row -> row.getExpressions().add(value.get()));
                } else if (statement.getExpressions() instanceof ExpressionList values) {
                    var expressions = values.getExpressions();
                    if (expressions.stream().allMatch(expression -> expression instanceof RowConstructor)) {
                        expressions.forEach(expression -> ((RowConstructor) expression).getExprList().getExpressions().add(value.get()));
                    } else if (columnCount == 1 && expressions.stream().allMatch(expression -> expression instanceof Parenthesis)) {
                        expressions.replaceAll(expression -> new RowConstructor().withExprList(new ExpressionList(((Parenthesis) expression).getExpression(), value.get())));
                    } else {
                        expressions.add(value.get());
                    }
                } else {
                    throw new UnsupportedOperationException("不支持此 VALUES 表达式，请显式指定插入字段");
                }
            }
            case null, default ->
                    throw new UnsupportedOperationException("不支持此 INSERT 数据源形式，请显式指定插入字段");
        }
    }
}
