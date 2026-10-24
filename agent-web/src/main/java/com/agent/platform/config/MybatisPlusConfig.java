package com.agent.platform.config;

import com.agent.platform.common.security.UserContext;
import com.agent.platform.config.plugins.handler.LogicDeleteHandler;
import com.agent.platform.config.plugins.inner.LogicDeleteInnerInterceptor;
import com.agent.platform.config.plugins.inner.ScopedTenantLineInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.Set;

/**
 * MyBatis-Plus 配置
 */
@Configuration
public class MybatisPlusConfig {

    @Value("${platform.mybatis.default-schema:public}")
    private String defaultSchema = "public";

    /**
     * 未登录/免鉴权场景（如公开对话门户、系统任务）的兜底租户。
     * 与历史数据约定一致：种子数据与早期数据均为 tenant_id = 1。
     */
    private static final long DEFAULT_TENANT_ID = 1L;

    /**
     * 无 tenant_id 列的表，租户插件直接跳过。
     * 其中版本/明细类表通过父表关联隔离，如需独立租户约束请先补列再移出本清单。
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "sys_tenant",
            "agent_team_member",
            "app_agent_version",
            "app_prompt_version",
            "app_skill_version",
            "chat_agent_run",
            "chat_message",
            "data_record",
            "eval_run_case",
            "eval_sample",
            "knowledge_chunk",
            "knowledge_document",
            "model_info"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 租户隔离：自动为 SQL 追加 tenant_id 条件（SELECT/UPDATE/DELETE/INSERT）
        TenantLineInnerInterceptor tenant = new ScopedTenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = UserContext.getTenantId();
                return new LongValue(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TABLES.contains(tableName);
            }
        });
        interceptor.addInnerInterceptor(tenant);

        // 在租户隔离之后、分页之前处理，使分页总数与数据查询使用相同的删除过滤条件。
        interceptor.addInnerInterceptor(new LogicDeleteInnerInterceptor(new LogicDeleteHandler() {
            @Override
            public String getDefaultSchema() {
                return defaultSchema;
            }
        }));

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
