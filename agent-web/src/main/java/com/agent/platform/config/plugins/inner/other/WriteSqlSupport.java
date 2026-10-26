package com.agent.platform.config.plugins.inner.other;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.SqlCommandType;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 两个写入钩子共用的幂等入口；状态跟随实际 BoundSql，不跟随被复用的 Statement。
 */
public final class WriteSqlSupport {
    private record Processed(WriteSqlSupport owner, Object context) {
    }

    private final String markerKey;

    public WriteSqlSupport(String markerKey) {
        this.markerKey = markerKey;
    }

    public void process(StatementHandler handler, Predicate<String> ignored,
                        Supplier<?> context, Consumer<PluginUtils.MPStatementHandler> rewrite) {
        var statement = PluginUtils.mpStatementHandler(handler);
        var mapped = statement.mappedStatement();
        SqlCommandType command = mapped.getSqlCommandType();
        if ((command != SqlCommandType.INSERT && command != SqlCommandType.UPDATE && command != SqlCommandType.DELETE)
                || ignored.test(mapped.getId())) {
            return;
        }
        var sql = statement.boundSql();
        Object current = context.get();
        if (sql.getAdditionalParameter(markerKey) instanceof Processed processed && processed.owner() == this) {
            if (!Objects.equals(processed.context(), current)) {
                throw new IllegalStateException("同一 BoundSql 执行期间不能切换租户上下文");
            }
            return;
        }
        rewrite.accept(statement);
        // 失败时不留完成标记，异常由调用方正常处理。
        sql.setAdditionalParameter(markerKey, new Processed(this, current));
    }
}
