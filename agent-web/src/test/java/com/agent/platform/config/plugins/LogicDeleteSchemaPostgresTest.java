package com.agent.platform.config.plugins;

import com.agent.platform.common.security.UserContext;
import com.agent.platform.config.MybatisPlusConfig;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "LOGIC_DELETE_TEST_JDBC_URL", matches = ".+")
class LogicDeleteSchemaPostgresTest {
    @TableName(value = "schema_target", schema = "logic_audit")
    public static class Audit {
        @TableId public Long id;
        public Long tenantId;
        public String name;
        @TableLogic public Integer deleted;
        public LocalDateTime deletedTime;
    }
    @TableName(value = "schema_target", schema = "logic_archive")
    public static class Archive {
        @TableId public Long id;
        public Long tenantId;
        @TableLogic(value = "N", delval = "Y") public String removed;
    }
    @TableName(value = "\"Mixed.Table\"", schema = "\"Audit.Mixed\"")
    public static class Quoted {
        @TableId public Long id;
        public Long tenantId;
        @TableLogic public Integer deleted;
    }
    @TableName("schema_target")
    public static class Default {
        @TableId public Long id;
        public Long tenantId;
        @TableLogic(value = "0", delval = "9") public Integer hidden;
    }

    public interface AuditMapper extends BaseMapper<Audit> {
        @Select("SELECT id FROM logic_audit.schema_target ORDER BY id") List<Long> ids();
        @Select("SELECT a.id FROM logic_audit.schema_target a INNER JOIN logic_archive.schema_target b "
                + "ON a.id=b.id ORDER BY a.id") List<Long> joined();
        @Insert("INSERT INTO logic_audit.schema_target (id) VALUES (#{id})") int add(@Param("id") long id);
        @Update("UPDATE logic_audit.schema_target SET name='changed'") int rename();
        @Delete("DELETE FROM logic_audit.schema_target WHERE id=#{id}") int remove(@Param("id") long id);
    }
    public interface ArchiveMapper extends BaseMapper<Archive> {
        @Select("SELECT id FROM logic_archive.schema_target ORDER BY id") List<Long> ids();
    }
    public interface QuotedMapper extends BaseMapper<Quoted> {
        @Select("SELECT id FROM \"Audit.Mixed\".\"Mixed.Table\" ORDER BY id") List<Long> ids();
    }
    public interface DefaultMapper extends BaseMapper<Default> {
        @Select("SELECT id FROM schema_target ORDER BY id") List<Long> ids();
    }

    private SqlSession session;

    @BeforeEach
    void setup() throws Exception {
        UserContext.set(new UserContext.LoginUser(1L, 1L, "schema-test"));
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("schema-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", System.getenv("LOGIC_DELETE_TEST_JDBC_URL"),
                        "postgres", System.getenv("LOGIC_DELETE_TEST_DB_PASSWORD"))));
        config.addInterceptor(new MybatisPlusConfig().mybatisPlusInterceptor());
        config.addMapper(AuditMapper.class);
        config.addMapper(ArchiveMapper.class);
        config.addMapper(QuotedMapper.class);
        config.addMapper(DefaultMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(false);
        try (var st = session.getConnection().createStatement()) {
            st.execute("CREATE SCHEMA logic_audit");
            st.execute("CREATE SCHEMA logic_archive");
            st.execute("CREATE SCHEMA \"Audit.Mixed\"");
            st.execute("CREATE TABLE logic_audit.schema_target (id bigint PRIMARY KEY, tenant_id bigint NOT NULL, name text, deleted int NOT NULL, deleted_time timestamp)");
            st.execute("CREATE TABLE logic_archive.schema_target (id bigint PRIMARY KEY, tenant_id bigint NOT NULL, removed text NOT NULL)");
            st.execute("CREATE TABLE \"Audit.Mixed\".\"Mixed.Table\" (id bigint PRIMARY KEY, tenant_id bigint NOT NULL, deleted int NOT NULL)");
            st.execute("CREATE TEMP TABLE schema_target (id bigint PRIMARY KEY, tenant_id bigint NOT NULL, hidden int NOT NULL)");
            st.execute("INSERT INTO logic_audit.schema_target (id, tenant_id, deleted) VALUES (1,1,0),(2,1,1),(3,2,0)");
            st.execute("INSERT INTO logic_archive.schema_target VALUES (1,1,'N'),(2,1,'Y'),(3,2,'N')");
            st.execute("INSERT INTO \"Audit.Mixed\".\"Mixed.Table\" VALUES (1,1,0),(2,1,1),(3,2,0)");
            st.execute("INSERT INTO schema_target VALUES (1,1,0),(2,1,9),(3,2,0)");
        }
    }

    @AfterEach
    void close() {
        try {
            if (session != null) {
                session.rollback(true);
                session.close();
            }
        } finally { UserContext.clear(); }
    }

    @Test
    void sameNamedTablesUseDifferentDeleteColumnsAndValues() {
        assertEquals(List.of(1L), session.getMapper(AuditMapper.class).ids());
        assertEquals(List.of(1L), session.getMapper(ArchiveMapper.class).ids());
        assertEquals(List.of(1L), session.getMapper(DefaultMapper.class).ids());
        assertEquals(List.of(1L), session.getMapper(AuditMapper.class).joined());
    }

    @Test
    void quotedSchemaAndTableWithDotsExecuteCorrectly() {
        assertEquals(List.of(1L), session.getMapper(QuotedMapper.class).ids());
    }

    @Test
    void qualifiedInsertUpdateDeleteRetainFilteringAndTimestamp() throws Exception {
        var mapper = session.getMapper(AuditMapper.class);
        assertEquals(1, mapper.add(100L));
        assertEquals(2, mapper.rename());
        assertEquals(1, mapper.remove(1L));
        assertEquals(List.of(100L), mapper.ids());
        try (var st = session.getConnection().createStatement();
             var rows = st.executeQuery("SELECT count(*) FROM logic_audit.schema_target WHERE id=1 AND deleted=1 AND deleted_time IS NOT NULL")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
    }
}
