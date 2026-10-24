package com.agent.platform.config.plugins.inner.other;

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
import java.util.function.UnaryOperator;

/**
 * 给 INSERT 的每行或每个 SELECT 分支追加一个值，不改变原有 JDBC 参数顺序。
 */
public final class InsertValueSupport {
    private InsertValueSupport() {
    }

    /**
     * 校验或规范化目标列对应的值；SELECT * 无法可靠定位目标列，明确拒绝。
     */
    public static void mapColumn(SelectBody body, int index, int columnCount, UnaryOperator<Expression> mapper) {
        switch (body) {
            case PlainSelect select -> {
                if (select.getSelectItems().size() != columnCount || !(select.getSelectItems().get(index) instanceof SelectExpressionItem item)) {
                    throw new UnsupportedOperationException("显式租户列要求 INSERT SELECT 使用明确的投影列，不能使用 SELECT *");
                }
                item.setExpression(mapper.apply(item.getExpression()));
            }
            case SetOperationList operations ->
                    operations.getSelects().forEach(branch -> mapColumn(branch, index, columnCount, mapper));
            case ValuesStatement values -> {
                if (values.getExpressions() instanceof MultiExpressionList rows) {
                    rows.getExpressionLists().forEach(row -> row.getExpressions().set(index, mapper.apply(row.getExpressions().get(index))));
                } else if (values.getExpressions() instanceof ExpressionList row) {
                    var expressions = row.getExpressions();
                    if (expressions.stream().allMatch(expression -> expression instanceof RowConstructor)) {
                        expressions.forEach(expression -> {
                            var fields = ((RowConstructor) expression).getExprList().getExpressions();
                            fields.set(index, mapper.apply(fields.get(index)));
                        });
                    } else if (columnCount == 1 && expressions.stream().allMatch(expression -> expression instanceof Parenthesis)) {
                        expressions.forEach(expression -> {
                            Parenthesis field = (Parenthesis) expression;
                            field.setExpression(mapper.apply(field.getExpression()));
                        });
                    } else {
                        expressions.set(index, mapper.apply(expressions.get(index)));
                    }
                } else {
                    throw new UnsupportedOperationException("无法校验此 VALUES 的租户列");
                }
            }
            case null, default -> throw new UnsupportedOperationException("无法校验此 INSERT 的租户列");
        }
    }

    public static void append(SelectBody body, int columnCount, Supplier<Expression> value) {
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
