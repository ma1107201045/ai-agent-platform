package com.agent.platform.config.plugins;

import com.agent.platform.common.security.UserContext;
import com.agent.platform.config.MybatisPlusConfig;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 使用专用测试库；每项测试只创建事务内临时表，不修改永久表。 */
@EnabledIfEnvironmentVariable(named = "LOGIC_DELETE_TEST_JDBC_URL", matches = ".+")
class LogicDeletePostgresTest {
    @TableName("test_logic_agent")
    public static class Agent {
        @TableId public Long id;
        public Long tenantId;
        public String name;
        @TableLogic public Integer deleted;
        public LocalDateTime deletedTime;
    }

    @TableName("test_logic_tool")
    public static class Tool {
        @TableId public Long id;
        public Long tenantId;
        public String name;
        @TableLogic public Integer deleted;
        public LocalDateTime deletedTime;
    }

    public interface ToolMapper extends BaseMapper<Tool> {}

    public interface AgentMapper extends BaseMapper<Agent> {
        @Select("WITH test_logic_agent AS (SELECT id FROM test_logic_tool) SELECT id FROM test_logic_agent ORDER BY id")
        List<Long> cteIds();

        @Update("UPDATE test_logic_agent a SET name = 'changed' FROM test_logic_tool t WHERE a.id = t.id")
        int updateFrom();

        @Update("UPDATE test_logic_agent SET name = 'changed' WHERE id IN (SELECT id FROM test_logic_tool)")
        int updateSubquery();

        @Insert("INSERT INTO test_logic_agent (id, name, tenant_id) SELECT id + 100, name, tenant_id FROM test_logic_tool")
        int copyWithExplicitTenant();

        @Insert("INSERT INTO test_logic_agent (id, name) SELECT id + 100, name FROM test_logic_tool WHERE id = 1 "
                + "UNION ALL SELECT id + 200, name FROM test_logic_tool WHERE id = 6")
        int copyUnion();

        @Select("SELECT a.id AS id, t.id AS tool_id FROM test_logic_agent a LEFT JOIN test_logic_tool t "
                + "ON a.id = t.id ORDER BY a.id")
        List<Map<String, Object>> leftJoin();

        @InterceptorIgnore(others = {"logicDelete@true"})
        @Update("UPDATE test_logic_agent SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
        int restore(@Param("id") long id);

        @InterceptorIgnore(others = {"logicDelete@true"})
        @Delete("DELETE FROM test_logic_agent WHERE id = #{id}")
        int purge(@Param("id") long id);

        @Delete("DELETE FROM test_logic_agent WHERE id = #{id}")
        int remove(@Param("id") long id);
    }

    private SqlSession session;
    private AgentMapper mapper;

    @BeforeEach
    void setup() throws Exception {
        UserContext.set(new UserContext.LoginUser(1L, 1L, "test"));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("logic-delete-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", System.getenv("LOGIC_DELETE_TEST_JDBC_URL"),
                        "postgres", System.getenv("LOGIC_DELETE_TEST_DB_PASSWORD"))));
        config.addInterceptor(new MybatisPlusConfig().mybatisPlusInterceptor());
        config.addMapper(AgentMapper.class);
        config.addMapper(ToolMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(false);
        mapper = session.getMapper(AgentMapper.class);
        try (var statement = session.getConnection().createStatement()) {
            for (String table : List.of("test_logic_agent", "test_logic_tool")) {
                statement.execute("CREATE TEMP TABLE " + table + " (id bigint PRIMARY KEY, tenant_id bigint NOT NULL, "
                        + "name text, deleted integer NOT NULL DEFAULT 0, deleted_time timestamp)");
            }
            statement.execute("INSERT INTO test_logic_agent (id, tenant_id, name, deleted) VALUES "
                    + "(1,1,'one',0),(2,1,'two',0),(3,1,'three',0),(4,1,'four',1),(5,2,'five',0)");
            statement.execute("INSERT INTO test_logic_tool (id, tenant_id, name, deleted) VALUES "
                    + "(1,1,'tool-one',0),(2,1,'tool-two',1),(3,2,'tool-three',0),(6,1,'tool-six',0)");
        }
    }

    @AfterEach
    void teardown() {
        try {
            if (session != null) {
                session.rollback(true);
                session.close();
            }
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void nativeDeleteWritesTimestampAndRespectsTenant() throws Exception {
        assertEquals(1, mapper.deleteById(1L));
        assertEquals(0, mapper.deleteById(5L));
        assertEquals(0, mapper.deleteById(4L));
        assertNull(mapper.selectById(1L));
        assertEquals(1, rawLong("SELECT deleted FROM test_logic_agent WHERE id = 1"));
        assertEquals(1, rawLong("SELECT count(*) FROM test_logic_agent WHERE id = 1 AND deleted_time IS NOT NULL"));
    }

    @Test
    void customDeleteHasSameTimestampBehavior() throws Exception {
        assertEquals(1, mapper.remove(1L));
        assertEquals(1, rawLong("SELECT count(*) FROM test_logic_agent WHERE id = 1 AND deleted = 1 AND deleted_time IS NOT NULL"));
    }

    @Test
    void updateFromExcludesDeletedAndOtherTenantSources() {
        assertEquals(1, mapper.updateFrom());
        assertEquals("changed", mapper.selectById(1L).name);
        assertEquals("two", mapper.selectById(2L).name);
        assertEquals("three", mapper.selectById(3L).name);
    }

    @Test
    void updateWhereSubqueryExcludesDeletedAndOtherTenantSources() {
        assertEquals(1, mapper.updateSubquery());
    }

    @Test
    void insertSelectFiltersSourceDespiteExplicitTenantColumn() throws Exception {
        assertEquals(2, mapper.copyWithExplicitTenant());
        assertNotNull(mapper.selectById(101L));
        assertNotNull(mapper.selectById(106L));
        assertEquals(0, rawLong("SELECT count(*) FROM test_logic_agent WHERE id IN (102, 103)"));
    }

    @Test
    void insertUnionAppendsTenantAndLogicColumnsToEveryBranch() throws Exception {
        assertEquals(2, mapper.copyUnion());
        assertEquals(2, rawLong("SELECT count(*) FROM test_logic_agent WHERE id IN (101, 206) AND tenant_id = 1 AND deleted = 0"));
    }

    @Test
    void cteDoesNotRequireTenantOrDeletedProjection() {
        assertEquals(List.of(1L, 6L), mapper.cteIds());
    }

    @Test
    void leftJoinPreservesRowsWithDeletedOrOtherTenantMatches() {
        var rows = mapper.leftJoin();
        assertEquals(3, rows.size());
        assertEquals(1L, rows.get(0).get("tool_id"));
        assertNull(rows.get(1).get("tool_id"));
        assertNull(rows.get(2).get("tool_id"));
    }

    @Test
    void restoreAndPurgeBypassOnlyLogicDelete() throws Exception {
        assertEquals(1, mapper.restore(4L));
        assertNotNull(mapper.selectById(4L));
        assertEquals(1, mapper.purge(4L));
        assertEquals(0, mapper.purge(5L));
        assertEquals(0, rawLong("SELECT count(*) FROM test_logic_agent WHERE id = 4"));
        assertEquals(1, rawLong("SELECT count(*) FROM test_logic_agent WHERE id = 5"));
    }

    private long rawLong(String sql) throws Exception {
        try (var statement = session.getConnection().createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
