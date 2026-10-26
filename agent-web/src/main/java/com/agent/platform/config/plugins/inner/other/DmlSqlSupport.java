package com.agent.platform.config.plugins.inner.other;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.update.Update;

import java.util.function.Consumer;

/** DML 共用的 AST 结构操作，不决定租户或逻辑删除策略。 */
public final class DmlSqlSupport {
    private DmlSqlSupport() {}

    public static Expression and(Expression where, Expression condition) {
        if (condition == null) return where;
        return where == null ? condition : new AndExpression(
                where instanceof OrExpression ? new Parenthesis(where) : where, condition);
    }

    public static void conflict(Insert insert, Consumer<Update> processUpdate) {
        var conflict = insert.getConflictAction();
        if (conflict == null || conflict.getUpdateSets().isEmpty()) return;
        Update update = new Update();
        update.setTable(insert.getTable());
        update.getUpdateSets().addAll(conflict.getUpdateSets());
        update.setWhere(conflict.getWhereExpression());
        processUpdate.accept(update);
        conflict.setWhereExpression(update.getWhere());
        conflict.getUpdateSets().clear();
        conflict.getUpdateSets().addAll(update.getUpdateSets());
    }

    public static void updateSources(Update update, ScopedSelectSupport queries, Consumer<PlainSelect> processSelect) {
        queries.items(update.getReturningExpressionList());
        update.getUpdateSets().forEach(set -> set.getExpressions().forEach(queries::expression));
        queries.expression(update.getWhere());
        // 复用 SELECT 的连接遍历；只回写 WHERE，JOIN 节点与原语句共享。
        if (update.getFromItem() != null) {
            PlainSelect source = new PlainSelect();
            source.setFromItem(update.getFromItem());
            source.setJoins(update.getJoins());
            source.setWhere(update.getWhere());
            processSelect.accept(source);
            update.setWhere(source.getWhere());
        }
        if (update.getStartJoins() != null && !update.getStartJoins().isEmpty()) {
            PlainSelect source = new PlainSelect();
            source.setFromItem(update.getTable());
            source.setJoins(update.getStartJoins());
            source.setWhere(update.getWhere());
            processSelect.accept(source);
            update.setWhere(source.getWhere());
        }
    }
}
