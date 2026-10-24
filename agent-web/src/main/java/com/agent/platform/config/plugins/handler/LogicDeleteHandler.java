package com.agent.platform.config.plugins.handler;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;


/**
 * 使用已注册实体的 MyBatis-Plus 元数据，与 @TableLogic 及全局逻辑删除配置保持一致。
 */
public interface LogicDeleteHandler {
    default String getLogicDeleteColumn() {
        return "deleted";
    }

    default Expression getNotDeletedValue() {
        return new LongValue(0);
    }

    default Expression getDeletedValue() {
        return new LongValue(1);
    }

    default String getDeletedTimeColumn() {
        return "deleted_time";
    }

    default boolean ignoreTable(String tableName) {
        TableInfo table = getTableInfo(tableName);
        return table == null || !table.isWithLogicDelete();
    }

    default TableInfo getTableInfo(String tableName) {
        TableIdentity name = TableIdentity.of(tableName, getDefaultSchema());
        return TableInfoHelper.getTableInfos().stream()
                .filter(table -> TableIdentity.of(table.getTableName(), getDefaultSchema()).equals(name))
                .findFirst().orElse(null);
    }

    /** 必须与数据库连接的默认 schema/search_path 首个业务 schema 一致。 */
    default String getDefaultSchema() {
        return "public";
    }

    default String getLogicDeleteColumn(String tableName) {
        TableInfo table = getTableInfo(tableName);
        return table != null && table.isWithLogicDelete() ? table.getLogicDeleteFieldInfo().getColumn() : getLogicDeleteColumn();
    }

    default Expression getNotDeletedValue(String tableName) {
        TableInfo table = getTableInfo(tableName);
        return table != null && table.isWithLogicDelete() ? parseValue(table.getLogicDeleteFieldInfo().getLogicNotDeleteValue(), table.getLogicDeleteFieldInfo().isCharSequence()) : getNotDeletedValue();
    }

    default Expression getDeletedValue(String tableName) {
        TableInfo table = getTableInfo(tableName);
        return table != null && table.isWithLogicDelete() ? parseValue(table.getLogicDeleteFieldInfo().getLogicDeleteValue(), table.getLogicDeleteFieldInfo().isCharSequence()) : getDeletedValue();
    }

    default String getDeletedTimeColumn(String tableName) {
        TableInfo table = getTableInfo(tableName);
        if (table == null) {
            return getDeletedTimeColumn();
        }
        return table.getFieldList().stream().filter(field -> "deletedTime".equals(field.getProperty())).map(TableFieldInfo::getColumn).findFirst().orElse(null);
    }

    private Expression parseValue(String value, boolean text) {
        if ("null".equalsIgnoreCase(value)) {
            return new NullValue();
        }
        if (text) {
            return new StringValue(value);
        }
        try {
            return CCJSqlParserUtil.parseExpression(value);
        } catch (JSQLParserException e) {
            throw new IllegalArgumentException("无法解析逻辑删除配置值：" + value, e);
        }
    }
}
