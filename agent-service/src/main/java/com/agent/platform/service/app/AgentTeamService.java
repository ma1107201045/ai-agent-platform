package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AgentTeam;
import com.agent.platform.dao.entity.app.AgentTeamMember;
import com.agent.platform.dao.entity.app.AgentTeamRun;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 多智能体编排服务：团队 CRUD、成员角色管理、first_match/round_robin/all 路由执行与运行记录。
 */
public interface AgentTeamService {

    public Page<AgentTeam> page(long page, long size, String keyword);

    public AgentTeam get(Long id);

    public AgentTeam create(AgentTeam team);

    public AgentTeam update(Long id, AgentTeam req);

    public void delete(Long id);

    /** 详情：团队 + 成员（含应用名） */
    public Map<String, Object> detail(Long id);

    /**
     * 全量保存成员：页面提交全部成员（含既有与新增），删除未出现的旧成员。
     */
    public List<Map<String, Object>> saveMembers(Long teamId, List<AgentTeamMember> input);

    /**
     * 执行一次团队会话：按路由策略选中一个或多个成员应用执行并汇总。
     */
    public AgentTeamRun run(Long teamId, String input);

    public Page<AgentTeamRun> pageRuns(Long teamId, long page, long size);
}
