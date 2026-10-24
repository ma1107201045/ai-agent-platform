package com.agent.platform.config.plugins.inner.other;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;

import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * 租户写入规则及实际 MyBatis 参数读取；与 SQL 遍历、写入生命周期分离。
 */
public final class TenantWriteValidator {
    private record Parameters(Configuration configuration, BoundSql sql) {
    }

    private final ThreadLocal<Parameters> parameters = new ThreadLocal<>();
    private final Supplier<TenantLineHandler> handler;

    public TenantWriteValidator(Supplier<TenantLineHandler> handler) {
        this.handler = handler;
    }

    public void withParameters(Configuration configuration, BoundSql sql, Runnable work) {
        Parameters previous = parameters.get();
        parameters.set(new Parameters(configuration, sql));
        try {
            work.run();
        } finally {
            if (previous == null) parameters.remove();
            else parameters.set(previous);
        }
    }

    public void insert(Insert insert) {
        var tenant = handler.get();
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
            if (isTenantColumn(insert.getColumns().get(i))) {
                hasTenant = true;
                InsertValueSupport.mapColumn(insert.getSelect().getSelectBody(), i, insert.getColumns().size(), value -> {
                    if (value instanceof Column column && isTenantColumn(column)) return tenant.getTenantId();
                    requireCurrentTenant(value);
                    return value;
                });
            }
        }
        if (!hasTenant) {
            InsertValueSupport.append(insert.getSelect().getSelectBody(), insert.getColumns().size(), tenant::getTenantId);
            insert.getColumns().add(new Column(tenant.getTenantIdColumn()));
        }
    }

    public void update(Update update) {
        update.getUpdateSets().forEach(set -> {
            for (int i = 0; i < set.getColumns().size(); i++) {
                if (isTenantColumn(set.getColumns().get(i))) {
                    if (i >= set.getExpressions().size())
                        throw new UnsupportedOperationException("不能通过元组表达式修改租户归属");
                    requireCurrentTenant(set.getExpressions().get(i));
                }
            }
        });
    }

    private boolean isTenantColumn(Column column) {
        return column.getColumnName().replace("\"", "").replace("`", "")
                .equalsIgnoreCase(handler.get().getTenantIdColumn());
    }

    private void requireCurrentTenant(Expression expression) {
        while (expression instanceof Parenthesis parenthesis) expression = parenthesis.getExpression();
        Object value = expression instanceof LongValue number ? number.getValue()
                : expression instanceof StringValue string ? string.getValue()
                : expression instanceof JdbcParameter parameter ? parameter(parameter) : null;
        try {
            if (value != null && new BigDecimal(value.toString())
                    .compareTo(new BigDecimal(handler.get().getTenantId().toString())) == 0) return;
        } catch (NumberFormatException ignored) {
            // 无法验证的表达式按禁止写入处理。
        }
        throw new IllegalArgumentException("租户字段必须等于当前租户；禁止跨租户写入或使用无法校验的租户表达式");
    }

    private Object parameter(JdbcParameter parameter) {
        Parameters context = parameters.get();
        Integer index = parameter.getIndex();
        if (context == null || index == null || index <= 0 || index > context.sql().getParameterMappings().size())
            return null;
        String property = context.sql().getParameterMappings().get(index - 1).getProperty();
        Object object = context.sql().getParameterObject();
        if (context.sql().hasAdditionalParameter(property)) return context.sql().getAdditionalParameter(property);
        if (object == null) return null;
        if (context.configuration().getTypeHandlerRegistry().hasTypeHandler(object.getClass())) return object;
        return context.configuration().newMetaObject(object).getValue(property);
    }
}
