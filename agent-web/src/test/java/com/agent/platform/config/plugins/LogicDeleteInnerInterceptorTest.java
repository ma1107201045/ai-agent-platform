package com.agent.platform.config.plugins;

import com.agent.platform.config.MybatisPlusConfig;
import com.agent.platform.config.plugins.handler.LogicDeleteHandler;
import com.agent.platform.config.plugins.inner.LogicDeleteInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisXMLLanguageDriver;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.agent.platform.dao.mapper.sys.TrashMapper;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.RowBounds;
import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogicDeleteInnerInterceptorTest {
    @BeforeAll
    static void initializeEntityMetadata() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        for (var candidate : scanner.findCandidateComponents("com.agent.platform.dao.entity")) {
            var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), candidate.getBeanClassName());
            assistant.setCurrentNamespace(candidate.getBeanClassName());
            TableInfoHelper.initTableInfo(assistant, Class.forName(candidate.getBeanClassName()));
        }
    }
    private final LogicDeleteInnerInterceptor interceptor =
            new LogicDeleteInnerInterceptor(new LogicDeleteHandler() {});

    @Test
    void filtersSelectAndPreservesOrPrecedenceAndParameters() {
        String sql = interceptor.parserSingle("SELECT * FROM app_agent a WHERE a.id = ? OR a.name = ?", null);
        assertTrue(sql.contains("(a.id = ? OR a.name = ?) AND a.deleted = 0"), sql);
    }

    @Test
    void filtersJoinedTableInOnClauseToPreserveLeftJoin() {
        String sql = interceptor.parserSingle("SELECT a.id FROM app_agent a LEFT JOIN app_tool t ON t.id = a.id", null);
        assertTrue(sql.contains("ON t.id = a.id AND t.deleted = 0"), sql);
        assertTrue(sql.contains("WHERE a.deleted = 0"), sql);
    }

    @Test
    void filtersNestedQueriesUnionAndCte() {
        String sql = interceptor.parserSingle("WITH x AS (SELECT * FROM app_agent) "
                + "SELECT * FROM x UNION ALL SELECT * FROM app_agent WHERE id IN (SELECT id FROM app_tool)", null);
        assertEquals(2, sql.split("app_agent WHERE deleted = 0|AND deleted = 0", -1).length - 1, sql);
        assertTrue(sql.contains("app_tool WHERE deleted = 0"), sql);
        assertFalse(sql.contains("x.deleted"), sql);
    }

    @Test
    void convertsDeleteAndPreservesTenantPredicateAndBindOrder() {
        String sql = interceptor.parserMulti("DELETE FROM app_agent WHERE id = ? AND tenant_id = 2", null);
        assertTrue(sql.startsWith("UPDATE app_agent SET deleted = 1, deleted_time = CURRENT_TIMESTAMP"), sql);
        assertTrue(sql.contains("id = ? AND tenant_id = 2 AND deleted = 0"), sql);
    }

    @Test
    void protectsUpdatesIncludingNativeLogicalDelete() {
        String sql = interceptor.parserMulti("UPDATE app_agent SET deleted = 1 WHERE id = ? AND deleted = 0", null);
        assertEquals("UPDATE app_agent SET deleted = 1, deleted_time = CURRENT_TIMESTAMP WHERE id = ? AND deleted = 0", sql);
    }

    @Test
    void doesNotDuplicateExistingNativeQueryPredicate() {
        assertEquals("SELECT * FROM app_agent WHERE deleted = 0 AND id = ?",
                interceptor.parserSingle("SELECT * FROM app_agent WHERE deleted = 0 AND id = ?", null));
        assertEquals("SELECT * FROM app_agent a WHERE (0 = a.deleted AND a.id = ?)",
                interceptor.parserSingle("SELECT * FROM app_agent a WHERE (0 = a.deleted AND a.id = ?)", null));
    }

    @Test
    void requiresEveryOrBranchToBeProtected() {
        String unsafe = interceptor.parserSingle("SELECT * FROM app_agent WHERE deleted = 0 OR status = 1", null);
        assertTrue(unsafe.contains("(deleted = 0 OR status = 1) AND deleted = 0"), unsafe);
        String safe = "SELECT * FROM app_agent WHERE (deleted = 0 AND id = ?) OR (deleted = 0 AND name = ?)";
        assertEquals(safe, interceptor.parserSingle(safe, null));
    }

    @Test
    void doesNotConfuseOtherAliasesSubqueriesOrParametersWithExistingPredicate() {
        String join = interceptor.parserSingle("SELECT * FROM app_agent a LEFT JOIN app_tool t "
                + "ON t.id = a.id AND a.deleted = 0", null);
        assertTrue(join.contains("AND t.deleted = 0"), join);
        assertTrue(join.contains("WHERE a.deleted = 0"), join);
        String nested = interceptor.parserSingle("SELECT * FROM app_agent a WHERE EXISTS "
                + "(SELECT 1 FROM app_agent a WHERE a.deleted = 0)", null);
        assertTrue(nested.endsWith(") AND a.deleted = 0"), nested);
        String parameter = interceptor.parserSingle("SELECT * FROM app_agent WHERE deleted = ?", null);
        assertTrue(parameter.contains("deleted = ? AND deleted = 0"), parameter);
        String not = interceptor.parserSingle("SELECT * FROM app_agent WHERE NOT (deleted = 0)", null);
        assertTrue(not.endsWith("AND deleted = 0"), not);
    }

    @Test
    void preservesExistingJoinOnPredicateWithoutRepeatingIt() {
        String sql = "SELECT a.id FROM app_agent a LEFT JOIN app_tool t "
                + "ON t.id = a.id AND t.deleted = 0 WHERE a.deleted = 0";
        assertEquals(sql, interceptor.parserSingle(sql, null));
    }

    @Test
    void repeatedInterceptionIsIdempotentAcrossQueryScopes() {
        String sql = "WITH x AS (SELECT * FROM app_agent WHERE deleted = 0) "
                + "SELECT a.id FROM app_agent a LEFT JOIN app_tool t ON t.id = a.id "
                + "WHERE a.id IN (SELECT id FROM app_agent)";
        String once = interceptor.parserSingle(sql, null);
        assertEquals(once, interceptor.parserSingle(once, null));
    }

    @Test
    void convertedDeleteRetainsExistingPredicateOnlyOnce() {
        assertEquals("UPDATE app_agent SET deleted = 1, deleted_time = CURRENT_TIMESTAMP WHERE id = ? AND deleted = 0",
                interceptor.parserMulti("DELETE FROM app_agent WHERE id = ? AND deleted = 0", null));
    }

    @Test
    void doesNotDuplicatePredicatesGeneratedByTableLogic() {
        var config = new MybatisConfiguration();
        config.addMapper(AppAgentMapper.class);
        String namespace = AppAgentMapper.class.getName();
        var select = config.getMappedStatement(namespace + ".selectById");
        var query = select.getBoundSql(3L);
        String parsedQuery = interceptor.parserSingle(query.getSql(), null);
        assertEquals(parsedQuery, interceptor.parserSingle(parsedQuery, null));
        assertEquals(1, parsedQuery.split("deleted = 0", -1).length - 1, parsedQuery);

        var delete = config.getMappedStatement(namespace + ".deleteById");
        var deletion = delete.getBoundSql(3L);
        String parsedDelete = interceptor.parserMulti(deletion.getSql(), null);
        assertTrue(parsedDelete.startsWith("UPDATE app_agent SET deleted = 1"), parsedDelete);
        assertEquals(1, parsedDelete.split("deleted = 0", -1).length - 1, parsedDelete);
    }

    @Test
    void leavesOtherTablesAloneAndInitializesInsertedRows() {
        assertEquals("DELETE FROM chat_message WHERE id = ?",
                interceptor.parserMulti("DELETE FROM chat_message WHERE id = ?", null));
        assertEquals("INSERT INTO app_agent (id, name, deleted) VALUES (?, ?, 0)",
                interceptor.parserSingle("INSERT INTO app_agent (id, name) VALUES (?, ?)", null));
    }

    @Test
    void initializesEveryBatchRow() {
        assertEquals("INSERT INTO app_agent (id, name, deleted) VALUES (?, ?, 0), (?, ?, 0)",
                interceptor.parserMulti("INSERT INTO app_agent (id, name) VALUES (?, ?), (?, ?)", null));
        assertEquals("INSERT INTO app_agent (id, deleted) VALUES (?, 0), (?, 0)",
                interceptor.parserMulti("INSERT INTO app_agent (id) VALUES (?), (?)", null));
        assertEquals("INSERT INTO app_agent (id, deleted) VALUES (?, 0)",
                interceptor.parserMulti("INSERT INTO app_agent (id) VALUES (?)", null));
    }

    @Test
    void preservesExplicitValuesIgnoredTablesAndInsertsWithoutColumnList() {
        assertEquals("INSERT INTO app_agent (id, deleted) VALUES (?, 1)",
                interceptor.parserMulti("INSERT INTO app_agent (id, deleted) VALUES (?, 1)", null));
        assertEquals("INSERT INTO app_agent (id, \"deleted\") VALUES (?, ?)",
                interceptor.parserMulti("INSERT INTO app_agent (id, \"deleted\") VALUES (?, ?)", null));
        assertEquals("INSERT INTO chat_message (id) VALUES (?)",
                interceptor.parserMulti("INSERT INTO chat_message (id) VALUES (?)", null));
        assertEquals("INSERT INTO app_agent VALUES (?, ?)",
                interceptor.parserMulti("INSERT INTO app_agent VALUES (?, ?)", null));
    }

    @Test
    void initializesInsertSelectAndFiltersUnionSources() {
        assertEquals("INSERT INTO app_agent (id, deleted) SELECT id, 0 FROM app_template WHERE deleted = 0 UNION ALL SELECT id, 0 FROM app_tool WHERE deleted = 0",
                interceptor.parserMulti("INSERT INTO app_agent (id) SELECT id FROM app_template "
                        + "UNION ALL SELECT id FROM app_tool", null));
    }

    @Test
    void insertWorksWithTenantPluginAndRemainsIdempotent() {
        var chain = new MybatisPlusConfig().mybatisPlusInterceptor().getInterceptors();
        var tenant = (com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor) chain.get(0);
        String sql = tenant.parserMulti("INSERT INTO app_agent (id) VALUES (?), (?)", null);
        String result = interceptor.parserMulti(sql, null);
        assertEquals("INSERT INTO app_agent (id, tenant_id, deleted) VALUES (?, 1, 0), (?, 1, 0)", result);
        assertEquals(result, interceptor.parserMulti(result, null));
    }

    @Test
    void rejectsDeleteClausesThatCannotBeConvertedWithoutChangingMeaning() {
        assertThrows(UnsupportedOperationException.class,
                () -> interceptor.parserSingle("DELETE FROM app_agent WHERE id = ? RETURNING id", null));
    }

    @Test
    void handlesQuotedTableNames() {
        assertTrue(interceptor.parserSingle("SELECT * FROM \"app_agent\"", null).contains("deleted = 0"));
    }

    @Test
    void runsBeforePaginationAndAfterTenantIsolation() {
        var chain = new MybatisPlusConfig().mybatisPlusInterceptor().getInterceptors();
        assertInstanceOf(LogicDeleteInnerInterceptor.class, chain.get(1));
        assertInstanceOf(PaginationInnerInterceptor.class, chain.get(2));
    }

    interface AnnotationMapper {
        @Select("SELECT * FROM app_agent WHERE id = #{id}")
        Object find(Long id);

        @Insert("INSERT INTO app_agent (id) VALUES (#{id})")
        int insert(Long id);
    }

    @Test
    void interceptsInsertAtStatementPreparationWithoutAddingJdbcParameters() {
        var config = new MybatisConfiguration();
        config.addMapper(AnnotationMapper.class);
        var ms = config.getMappedStatement(AnnotationMapper.class.getName() + ".insert");
        var sql = ms.getBoundSql(3L);
        var sh = config.newStatementHandler(null, ms, 3L, RowBounds.DEFAULT, null, sql);
        interceptor.beforePrepare(sh, null, null);
        assertEquals("INSERT INTO app_agent (id, deleted) VALUES (?, 0)", sql.getSql());
        assertEquals(1, sql.getParameterMappings().size());
    }

    @Test
    void interceptsRealAnnotationBoundSql() {
        var config = new MybatisConfiguration();
        config.addMapper(AnnotationMapper.class);
        var ms = config.getMappedStatement(AnnotationMapper.class.getName() + ".find");
        BoundSql sql = ms.getBoundSql(3L);
        interceptor.beforeQuery(null, ms, 3L, RowBounds.DEFAULT, null, sql);
        assertTrue(sql.getSql().contains("deleted = 0"), sql.getSql());
        assertEquals(1, sql.getParameterMappings().size());
    }

    @Test
    void interceptsDynamicXmlDeleteAtStatementPreparation() {
        var config = new MybatisConfiguration();
        config.setDefaultScriptingLanguage(MybatisXMLLanguageDriver.class);
        String xml = """
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                  "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="test.LogicDeleteXmlMapper">
                  <delete id="remove">
                    DELETE FROM app_agent
                    <where><if test="id != null">id = #{id}</if></where>
                  </delete>
                </mapper>
                """;
        new XMLMapperBuilder(new StringReader(xml), config, "logic-delete-test.xml", config.getSqlFragments()).parse();
        var ms = config.getMappedStatement("test.LogicDeleteXmlMapper.remove");
        var params = Map.of("id", 3L);
        var sql = ms.getBoundSql(params);
        var sh = config.newStatementHandler(null, ms, params, RowBounds.DEFAULT, null, sql);
        interceptor.beforePrepare(sh, null, null);
        assertTrue(sql.getSql().startsWith("UPDATE app_agent SET deleted = 1"), sql.getSql());
        assertTrue(sql.getSql().contains("id = ? AND deleted = 0"), sql.getSql());
        assertEquals(1, sql.getParameterMappings().size());
    }

    @Test
    void trashMapperBypassesLogicDeleteButStillUsesTenantIsolation() throws Exception {
        var config = new MybatisConfiguration();
        config.addMapper(TrashMapper.class);
        var ms = config.getMappedStatement(TrashMapper.class.getName() + ".listDeletedAgent");
        assertTrue(InterceptorIgnoreHelper.willIgnoreOthersByKey(ms.getId(), "logicDelete"));
        var sql = ms.getBoundSql(Map.of("tenantId", 1L));
        for (var plugin : new MybatisPlusConfig().mybatisPlusInterceptor().getInterceptors()) {
            plugin.beforeQuery(null, ms, null, RowBounds.DEFAULT, null, sql);
        }
        assertTrue(sql.getSql().contains("deleted = 1"), sql.getSql());
        assertTrue(sql.getSql().contains("tenant_id = 1"), sql.getSql());
        assertFalse(sql.getSql().contains("deleted = 0"), sql.getSql());

        var restore = config.getMappedStatement(TrashMapper.class.getName() + ".restoreAgent");
        var restoreSql = restore.getBoundSql(3L);
        var sh = config.newStatementHandler(null, restore, 3L, RowBounds.DEFAULT, null, restoreSql);
        interceptor.beforePrepare(sh, null, null);
        assertFalse(restoreSql.getSql().contains("AND deleted = 0"), restoreSql.getSql());

        var purge = config.getMappedStatement(TrashMapper.class.getName() + ".purgeAgent");
        var purgeSql = purge.getBoundSql(3L);
        var purgeHandler = config.newStatementHandler(null, purge, 3L, RowBounds.DEFAULT, null, purgeSql);
        interceptor.beforePrepare(purgeHandler, null, null);
        assertTrue(purgeSql.getSql().startsWith("DELETE FROM app_agent"), purgeSql.getSql());
    }
}
