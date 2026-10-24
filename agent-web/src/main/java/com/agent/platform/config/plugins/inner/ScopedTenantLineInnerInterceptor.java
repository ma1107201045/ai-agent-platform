package com.agent.platform.config.plugins.inner;

import com.agent.platform.config.plugins.inner.other.DmlSqlSupport;
import com.agent.platform.config.plugins.inner.other.ScopedSelectSupport;
import com.agent.platform.config.plugins.inner.other.TenantWriteValidator;
import com.agent.platform.config.plugins.inner.other.WriteSqlSupport;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;

import java.sql.Connection;
import java.util.Objects;

/**
 * 租户策略入口：保留原生租户查询规则，扩展来源过滤、写入校验和安全 UPSERT。
 */
public class ScopedTenantLineInnerInterceptor extends TenantLineInnerInterceptor {
    private final ScopedSelectSupport queries = new ScopedSelectSupport(body -> processSelectBody(body, null));
    private final WriteSqlSupport writes = new WriteSqlSupport("__agentTenantWriteProcessed");
    private final TenantWriteValidator validator = new TenantWriteValidator(this::getTenantLineHandler);

    public ScopedTenantLineInnerInterceptor(TenantLineHandler handler) {
        super(Objects.requireNonNull(handler, "tenantLineHandler"));
    }

    @Override
    public void beforePrepare(StatementHandler handler, Connection connection, Integer transactionTimeout) {
        processWrite(handler);
    }

    @Override
    public void beforeGetBoundSql(StatementHandler handler) {
        processWrite(handler);
    }

    private void processWrite(StatementHandler handler) {
        writes.process(handler, InterceptorIgnoreHelper::willIgnoreTenantLine, () -> getTenantLineHandler().getTenantId().toString(), statement -> validator.withParameters(statement.configuration(), statement.boundSql(), () -> {
            var sql = statement.mPBoundSql();
            sql.sql(parserMulti(sql.sql(), null));
        }));
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        return queries.isCte(table) ? null : super.buildTableExpression(table, where, whereSegment);
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        queries.select(select);
    }

    @Override
    protected void processSelectBody(SelectBody body, String whereSegment) {
        if (!queries.values(body)) super.processSelectBody(body, whereSegment);
    }

    @Override
    protected void processPlainSelect(PlainSelect select, String whereSegment) {
        queries.plainSelect(select, () -> super.processPlainSelect(select, whereSegment));
    }

    @Override
    protected void processWhereSubSelect(Expression expression, String whereSegment) {
        queries.expression(expression);
    }

    @Override
    protected void processOtherFromItem(FromItem item, String whereSegment) {
        if (!queries.from(item)) super.processOtherFromItem(item, whereSegment);
    }

    @Override
    protected void processSelectItem(SelectItem item, String whereSegment) {
        queries.item(item);
    }

    @Override
    protected void processFunction(Function function, String whereSegment) {
        queries.expression(function);
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        queries.withCtes(insert.getWithItemsList(), () -> {
            queries.items(insert.getReturningExpressionList());
            // 目标豁免或已含租户字段，都不能成为来源查询的隔离豁免。
            if (insert.getSelect() != null) queries.select(insert.getSelect());
            if (getTenantLineHandler().ignoreTable(insert.getTable().getName())) return;
            validator.insert(insert);
            DmlSqlSupport.conflict(insert, this::processUpdateBody);
        });
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        queries.withCtes(update.getWithItemsList(), () -> processUpdateBody(update));
    }

    private void processUpdateBody(Update update) {
        boolean scoped = !getTenantLineHandler().ignoreTable(update.getTable().getName());
        if (scoped) validator.update(update);
        DmlSqlSupport.updateSources(update, queries, select -> processPlainSelect(select, null));
        if (scoped) {
            // 更新目标始终是实体表，即使同名 CTE 可见也必须隔离。
            Expression condition = new EqualsTo(getAliasColumn(update.getTable()), getTenantLineHandler().getTenantId());
            update.setWhere(DmlSqlSupport.and(update.getWhere(), condition));
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        queries.withCtes(delete.getWithItemsList(), () -> {
            if ((delete.getUsingList() != null && !delete.getUsingList().isEmpty()) || (delete.getJoins() != null && !delete.getJoins().isEmpty())) {
                throw new UnsupportedOperationException("暂不支持自动隔离 DELETE USING/JOIN，请改用 WHERE 子查询");
            }
            queries.expression(delete.getWhere());
            queries.items(delete.getReturningExpressionList());
            super.processDelete(delete, index, sql, obj);
        });
    }

    @Override
    protected Column getAliasColumn(Table table) {
        String qualifier = table.getAlias() == null ? table.getFullyQualifiedName() : table.getAlias().getName();
        return new Column(qualifier + "." + getTenantLineHandler().getTenantIdColumn());
    }
}