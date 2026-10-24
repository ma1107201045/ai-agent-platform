package com.agent.platform.config.plugins;

import com.agent.platform.config.MybatisPlusConfig;
import com.agent.platform.config.plugins.handler.LogicDeleteHandler;
import com.agent.platform.config.plugins.inner.LogicDeleteInnerInterceptor;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class LogicDeleteBoundaryTest {
    private final LogicDeleteInnerInterceptor plugin = new LogicDeleteInnerInterceptor(new LogicDeleteHandler() {});

    @TableName("custom_logic")
    static class CustomEntity {
        @TableId Long id;
        @TableLogic(value = "N", delval = "Y") String removed;
        @TableField("removed_at") LocalDateTime deletedTime;
    }

    @TableName("nullable_logic")
    static class NullableEntity {
        @TableId Long id;
        @TableLogic(value = "null", delval = "now()") LocalDateTime removed;
    }

    @BeforeAll
    static void metadata() throws Exception {
        LogicDeleteInnerInterceptorTest.initializeEntityMetadata();
        for (Class<?> entity : new Class<?>[]{CustomEntity.class, NullableEntity.class}) {
            var builder = new MapperBuilderAssistant(new MybatisConfiguration(), entity.getName());
            builder.setCurrentNamespace(entity.getName());
            TableInfoHelper.initTableInfo(builder, entity);
        }
    }

    @Test
    void filtersWhereAndSetSubqueriesEvenWhenTargetIsNotSoftDeletable() {
        String sql = plugin.parserSingle("UPDATE asset_file SET name = "
                + "COALESCE((SELECT name FROM app_tool WHERE id = ?), 'none') "
                + "WHERE id IN (SELECT id FROM app_agent)", null);
        assertTrue(sql.contains("app_tool WHERE id = ? AND deleted = 0"), sql);
        assertTrue(sql.contains("app_agent WHERE deleted = 0"), sql);
        assertFalse(sql.contains("asset_file.deleted"), sql);
    }

    @Test
    void filtersUpdateFromWithQualifiedSourceAndTargetColumns() {
        String sql = plugin.parserSingle("UPDATE app_agent SET name = app_tool.name "
                + "FROM app_tool WHERE app_agent.id = app_tool.id", null);
        assertTrue(sql.contains("app_tool.deleted = 0"), sql);
        assertTrue(sql.contains("app_agent.deleted = 0"), sql);
    }

    @Test
    void filtersInsertSourcesWhenDeletedColumnAlreadyExistsOrTargetIsIgnored() {
        assertTrue(plugin.parserSingle("INSERT INTO app_agent (id, deleted) SELECT id, 0 FROM app_tool", null)
                .endsWith("FROM app_tool WHERE deleted = 0"));
        assertTrue(plugin.parserSingle("INSERT INTO asset_file (id) SELECT id FROM app_tool", null)
                .endsWith("FROM app_tool WHERE deleted = 0"));
    }

    @Test
    void sameNameCteDoesNotReceiveEntityColumns() {
        assertEquals("WITH app_agent AS (SELECT id FROM app_tool WHERE deleted = 0) SELECT id FROM app_agent",
                plugin.parserSingle("WITH app_agent AS (SELECT id FROM app_tool) SELECT id FROM app_agent", null));
        assertEquals("WITH app_agent AS (SELECT id FROM app_agent WHERE deleted = 0) SELECT id FROM app_agent",
                plugin.parserSingle("WITH app_agent AS (SELECT id FROM app_agent) SELECT id FROM app_agent", null));
    }

    @Test
    void schemaQualifiedTableIsNotConfusedWithCteAndScopesDoNotLeak() {
        String sql = plugin.parserSingle("WITH app_agent AS (SELECT id FROM app_tool) "
                + "SELECT id FROM public.app_agent", null);
        assertTrue(sql.endsWith("public.app_agent WHERE deleted = 0"), sql);
        String nested = plugin.parserSingle("SELECT id FROM app_agent WHERE EXISTS "
                + "(WITH app_agent AS (SELECT id FROM app_tool) SELECT id FROM app_agent)", null);
        assertTrue(nested.endsWith(") AND deleted = 0"), nested);
        assertEquals("SELECT * FROM app_agent WHERE deleted = 0", plugin.parserSingle("SELECT * FROM app_agent", null));
    }

    @Test
    void sameNameCteWorksAcrossCompleteTenantAndLogicDeleteChain() {
        var chain = new MybatisPlusConfig().mybatisPlusInterceptor().getInterceptors();
        var tenant = (TenantLineInnerInterceptor) chain.get(0);
        String sql = tenant.parserSingle("WITH app_agent AS (SELECT id FROM app_tool) SELECT id FROM app_agent", null);
        sql = plugin.parserSingle(sql, null);
        assertTrue(sql.endsWith("SELECT id FROM app_agent"), sql);
        assertTrue(sql.contains("app_tool.tenant_id = 1"), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
    }

    @Test
    void innerJoinReusesWhereButLeftJoinPreservesRetainedRows() {
        String inner = "SELECT a.id FROM app_agent a INNER JOIN app_tool t ON t.id = a.id "
                + "WHERE a.deleted = 0 AND t.deleted = 0";
        assertEquals(inner, plugin.parserSingle(inner, null));
        String left = plugin.parserSingle("SELECT a.id FROM app_agent a LEFT JOIN app_tool t "
                + "ON t.id = a.id AND a.deleted = 0", null);
        assertTrue(left.contains("WHERE a.deleted = 0"), left);
        assertTrue(left.contains("t.deleted = 0"), left);
    }

    @Test
    void usesEntityColumnAndValuesAndDoesNotRequireDeletedTimeField() {
        assertEquals("SELECT * FROM custom_logic WHERE removed = 'N'",
                plugin.parserSingle("SELECT * FROM custom_logic", null));
        assertEquals("INSERT INTO custom_logic (id, removed) VALUES (?, 'N')",
                plugin.parserSingle("INSERT INTO custom_logic (id) VALUES (?)", null));
        assertEquals("UPDATE custom_logic SET removed = 'Y', removed_at = CURRENT_TIMESTAMP WHERE id = ? AND removed = 'N'",
                plugin.parserSingle("DELETE FROM custom_logic WHERE id = ?", null));
        assertEquals("SELECT * FROM nullable_logic WHERE removed IS NULL",
                plugin.parserSingle("SELECT * FROM nullable_logic WHERE removed IS NULL", null));
        assertEquals("UPDATE nullable_logic SET removed = now() WHERE id = ? AND removed IS NULL",
                plugin.parserSingle("DELETE FROM nullable_logic WHERE id = ?", null));
    }

    @Test
    void preservesExplicitDeletedTimeAndDoesNotFillTimeOnNormalUpdates() {
        assertEquals("UPDATE app_agent SET deleted = 1, deleted_time = ? WHERE id = ? AND deleted = 0",
                plugin.parserSingle("UPDATE app_agent SET deleted = 1, deleted_time = ? WHERE id = ?", null));
        assertEquals("UPDATE app_agent SET name = ? WHERE id = ? AND deleted = 0",
                plugin.parserSingle("UPDATE app_agent SET name = ? WHERE id = ?", null));
    }

    @Test
    void scopesRemainIndependentAcrossConcurrentRequests() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cte = executor.submit(() -> plugin.parserSingle(
                    "WITH app_agent AS (SELECT id FROM app_tool) SELECT id FROM app_agent", null));
            var physical = executor.submit(() -> plugin.parserSingle("SELECT id FROM app_agent", null));
            assertTrue(cte.get().endsWith("SELECT id FROM app_agent"));
            assertTrue(physical.get().endsWith("WHERE deleted = 0"));
        }
    }
}
