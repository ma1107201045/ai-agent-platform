package com.agent.platform.config.plugins;

import com.agent.platform.config.plugins.handler.LogicDeleteHandler;
import com.agent.platform.config.plugins.inner.LogicDeleteInnerInterceptor;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LogicDeleteSchemaTest {
    @TableName(value = "schema_unit", schema = "audit")
    static class Audit { @TableId Long id; @TableLogic Integer deleted; LocalDateTime deletedTime; }
    @TableName(value = "schema_unit", schema = "archive")
    static class Archive { @TableId Long id; @TableLogic(value = "N", delval = "Y") String removed; }
    @TableName(value = "schema_unit", schema = "public")
    static class Public { @TableId Long id; @TableLogic(value = "0", delval = "9") Integer hidden; }
    @TableName("unqualified_unit")
    static class Unqualified { @TableId Long id; @TableLogic Integer deleted; }
    @TableName(value = "\"Mixed.Table\"", schema = "\"Audit.Mixed\"")
    static class Quoted { @TableId Long id; @TableLogic Integer deleted; }
    @TableName(value = "\"Quote\"\"Table\"", schema = "audit")
    static class Escaped { @TableId Long id; @TableLogic Integer deleted; }

    @BeforeAll
    static void metadata() {
        for (Class<?> entity : new Class<?>[]{Audit.class, Archive.class, Public.class, Unqualified.class, Quoted.class, Escaped.class}) {
            var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entity.getName());
            assistant.setCurrentNamespace(entity.getName());
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    private final LogicDeleteHandler handler = new LogicDeleteHandler() {};
    private final LogicDeleteInnerInterceptor plugin = new LogicDeleteInnerInterceptor(handler);

    @Test
    void qualifiedTablesUseTheirOwnMetadata() {
        assertEquals("SELECT * FROM audit.schema_unit WHERE deleted = 0", plugin.parserSingle("SELECT * FROM audit.schema_unit", null));
        assertEquals("SELECT * FROM archive.schema_unit WHERE removed = 'N'", plugin.parserSingle("SELECT * FROM archive.schema_unit", null));
        String joined = plugin.parserSingle("SELECT a.id FROM audit.schema_unit a INNER JOIN archive.schema_unit b ON a.id=b.id", null);
        assertTrue(joined.contains("a.deleted = 0"), joined);
        assertTrue(joined.contains("b.removed = 'N'"), joined);
    }

    @Test
    void defaultSchemaDoesNotBorrowSameNamedTableFromAnotherSchema() {
        assertSame(handler.getTableInfo("schema_unit"), handler.getTableInfo("public.schema_unit"));
        assertEquals("SELECT * FROM schema_unit WHERE hidden = 0", plugin.parserSingle("SELECT * FROM schema_unit", null));
        var audit = new LogicDeleteInnerInterceptor(new LogicDeleteHandler() {
            @Override public String getDefaultSchema() { return "audit"; }
        });
        assertEquals("SELECT * FROM schema_unit WHERE deleted = 0", audit.parserSingle("SELECT * FROM schema_unit", null));
        assertNull(handler.getTableInfo("unknown.schema_unit"));
        assertSame(handler.getTableInfo("unqualified_unit"), handler.getTableInfo("public.unqualified_unit"));
    }

    @Test
    void quotingCaseAndDotsArePreserved() {
        assertNotNull(handler.getTableInfo("\"Audit.Mixed\".\"Mixed.Table\""));
        assertNull(handler.getTableInfo("\"audit.mixed\".\"Mixed.Table\""));
        assertNotNull(handler.getTableInfo("audit.\"Quote\"\"Table\""));
        assertTrue(plugin.parserSingle("SELECT * FROM \"Audit.Mixed\".\"Mixed.Table\"", null).endsWith("WHERE deleted = 0"));
        assertSame(handler.getTableInfo("AUDIT.SCHEMA_UNIT"), handler.getTableInfo("audit.schema_unit"));
    }

    @Test
    void insertUpdateDeleteUseQualifiedMetadataToo() {
        assertEquals("INSERT INTO audit.schema_unit (id, deleted) VALUES (?, 0)",
                plugin.parserSingle("INSERT INTO audit.schema_unit (id) VALUES (?)", null));
        assertEquals("UPDATE archive.schema_unit SET removed = 'Y' WHERE id = ? AND removed = 'N'",
                plugin.parserSingle("DELETE FROM archive.schema_unit WHERE id = ?", null));
        assertEquals("UPDATE audit.schema_unit SET deleted = 1, deleted_time = CURRENT_TIMESTAMP WHERE id = ? AND deleted = 0",
                plugin.parserSingle("UPDATE audit.schema_unit SET deleted = 1 WHERE id = ?", null));
    }
}
