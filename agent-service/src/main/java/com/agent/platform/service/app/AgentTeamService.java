package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AgentTeam;
import com.agent.platform.dao.entity.app.AgentTeamMember;
import com.agent.platform.dao.entity.app.AgentTeamRun;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.mapper.app.AgentTeamMemberMapper;
import com.agent.platform.dao.mapper.app.AgentTeamRunMapper;
import com.agent.platform.dao.mapper.app.AgentTeamMapper;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.service.common.AppExecuteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多智能体编排服务：团队 CRUD、成员角色管理、first_match/round_robin/all 路由执行与运行记录。
 */
@Service
@RequiredArgsConstructor
public class AgentTeamService {

    private final AgentTeamMapper teamMapper;
    private final AgentTeamMemberMapper memberMapper;
    private final AgentTeamRunMapper runMapper;
    private final AppAgentMapper appAgentMapper;
    private final AppExecuteService appExecuteService;
    private final ObjectMapper objectMapper;

    // ---------- 团队 ----------

    public Page<AgentTeam> page(long page, long size, String keyword) {
        return teamMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<AgentTeam>()
                .eq(AgentTeam::getTenantId, tenant())
                .and(StringUtils.hasText(keyword),
                        w -> w.like(AgentTeam::getName, keyword).or().like(AgentTeam::getDescription, keyword))
                .orderByDesc(AgentTeam::getId));
    }

    public AgentTeam get(Long id) {
        AgentTeam team = teamMapper.selectById(id);
        if (team == null) {
            throw new BizException("团队不存在: " + id);
        }
        if (!team.getTenantId().equals(tenant())) {
            throw new BizException("无权操作该团队");
        }
        return team;
    }

    public AgentTeam create(AgentTeam team) {
        validateTeam(team);
        LocalDateTime now = LocalDateTime.now();
        team.setId(null);
        team.setTenantId(tenant());
        team.setStatus(team.getStatus() == null ? 1 : team.getStatus());
        team.setRunCount(0);
        team.setCreateTime(now);
        team.setUpdateTime(now);
        teamMapper.insert(team);
        return team;
    }

    public AgentTeam update(Long id, AgentTeam req) {
        AgentTeam team = get(id);
        if (StringUtils.hasText(req.getName())) {
            team.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            team.setDescription(req.getDescription());
        }
        if (StringUtils.hasText(req.getRouting())) {
            team.setRouting(req.getRouting());
        }
        if (req.getStatus() != null) {
            team.setStatus(req.getStatus());
        }
        validateTeam(team);
        team.setUpdateTime(LocalDateTime.now());
        teamMapper.updateById(team);
        return team;
    }

    private void validateTeam(AgentTeam team) {
        if (!StringUtils.hasText(team.getName())) {
            throw new BizException("团队名称不能为空");
        }
        if (team.getRouting() == null
                || (!"first_match".equals(team.getRouting())
                && !"round_robin".equals(team.getRouting())
                && !"all".equals(team.getRouting()))) {
            throw new BizException("请选择路由策略(first_match/round_robin/all)");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        get(id);
        teamMapper.deleteById(id);
        memberMapper.delete(new LambdaQueryWrapper<AgentTeamMember>().eq(AgentTeamMember::getTeamId, id));
        runMapper.delete(new LambdaQueryWrapper<AgentTeamRun>().eq(AgentTeamRun::getTeamId, id));
    }

    /** 详情：团队 + 成员（含应用名） */
    public Map<String, Object> detail(Long id) {
        AgentTeam team = get(id);
        List<AgentTeamMember> members = membersOf(id);
        return Map.of("team", team, "members", withAppNames(members));
    }

    private List<AgentTeamMember> membersOf(Long teamId) {
        return memberMapper.selectList(new LambdaQueryWrapper<AgentTeamMember>()
                .eq(AgentTeamMember::getTeamId, teamId)
                .orderByAsc(AgentTeamMember::getPriority)
                .orderByAsc(AgentTeamMember::getId));
    }

    private List<Map<String, Object>> withAppNames(List<AgentTeamMember> members) {
        Set<Long> appIds = members.stream().map(AgentTeamMember::getAppId).collect(Collectors.toSet());
        Map<Long, AppAgent> appMap = appIds.isEmpty() ? Collections.emptyMap()
                : appAgentMapper.selectBatchIds(appIds).stream().collect(Collectors.toMap(AppAgent::getId, a -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentTeamMember m : members) {
            Map<String, Object> row = new LinkedHashMap<>();
            AppAgent app = appMap.get(m.getAppId());
            row.put("member", m);
            row.put("appName", app == null ? "应用已删除" : app.getName());
            row.put("appType", app == null ? null : app.getType());
            row.put("appStatus", app == null ? null : app.getStatus());
            out.add(row);
        }
        return out;
    }

    /**
     * 全量保存成员：页面提交全部成员（含既有与新增），删除未出现的旧成员。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> saveMembers(Long teamId, List<AgentTeamMember> input) {
        get(teamId);
        List<AgentTeamMember> existing = membersOf(teamId);
        Map<Long, AgentTeamMember> oldById = existing.stream()
                .collect(Collectors.toMap(AgentTeamMember::getId, m -> m));
        List<Long> keepIds = new ArrayList<>();
        if (input != null) {
            int order = 0;
            for (AgentTeamMember m : input) {
                order++;
                validateMember(m);
                if (m.getId() != null && oldById.containsKey(m.getId())) {
                    AgentTeamMember old = oldById.get(m.getId());
                    old.setName(m.getName());
                    old.setDescription(m.getDescription());
                    old.setAppId(m.getAppId());
                    old.setKeywords(m.getKeywords());
                    old.setPriority(m.getPriority() == null ? order : m.getPriority());
                    old.setEnabled(m.getEnabled() == null ? 1 : m.getEnabled());
                    old.setUpdateTime(LocalDateTime.now());
                    memberMapper.updateById(old);
                    keepIds.add(old.getId());
                } else {
                    AgentTeamMember nm = new AgentTeamMember();
                    nm.setTeamId(teamId);
                    nm.setName(m.getName());
                    nm.setDescription(m.getDescription());
                    nm.setAppId(m.getAppId());
                    nm.setKeywords(m.getKeywords());
                    nm.setPriority(m.getPriority() == null ? order : m.getPriority());
                    nm.setEnabled(m.getEnabled() == null ? 1 : m.getEnabled());
                    LocalDateTime now = LocalDateTime.now();
                    nm.setCreateTime(now);
                    nm.setUpdateTime(now);
                    memberMapper.insert(nm);
                    keepIds.add(nm.getId());
                }
            }
        }
        if (!existing.isEmpty()) {
            List<Long> drop = existing.stream().map(AgentTeamMember::getId)
                    .filter(id -> !keepIds.contains(id)).toList();
            if (!drop.isEmpty()) {
                memberMapper.delete(new LambdaQueryWrapper<AgentTeamMember>()
                        .eq(AgentTeamMember::getTeamId, teamId)
                        .in(AgentTeamMember::getId, drop));
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) detail(teamId).get("members");
        return members;
    }

    private void validateMember(AgentTeamMember m) {
        if (!StringUtils.hasText(m.getName())) {
            throw new BizException("成员名称不能为空");
        }
        if (m.getAppId() == null) {
            throw new BizException("成员必须绑定应用");
        }
        AppAgent app = appAgentMapper.selectById(m.getAppId());
        if (app == null || app.getStatus() == null || app.getStatus() != 1) {
            throw new BizException("绑定的应用不存在或未发布: app#" + m.getAppId());
        }
    }

    // ---------- 运行 ----------

    /**
     * 执行一次团队会话：按路由策略选中一个或多个成员应用执行并汇总。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentTeamRun run(Long teamId, String input) {
        AgentTeam team = get(teamId);
        if (team.getStatus() == null || team.getStatus() != 1) {
            throw new BizException("团队已停用，无法运行");
        }
        if (!StringUtils.hasText(input)) {
            throw new BizException("请输入消息内容");
        }
        List<AgentTeamMember> enabled = memberMapper.selectList(new LambdaQueryWrapper<AgentTeamMember>()
                .eq(AgentTeamMember::getTeamId, teamId)
                .eq(AgentTeamMember::getEnabled, 1)
                .orderByAsc(AgentTeamMember::getPriority)
                .orderByAsc(AgentTeamMember::getId));
        if (enabled.isEmpty()) {
            throw new BizException("团队未配置启用的成员");
        }
        List<AgentTeamMember> picked = pickMembers(team, enabled, input.trim());

        long start = System.currentTimeMillis();
        List<Map<String, Object>> steps = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (AgentTeamMember member : picked) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("memberId", member.getId());
            step.put("memberName", member.getName());
            step.put("appId", member.getAppId());
            long ms = System.currentTimeMillis();
            try {
                AppExecuteService.Reply reply = appExecuteService.runApp(member.getAppId(), input);
                step.put("status", "success");
                step.put("answer", reply == null ? "" : reply.answer());
                step.put("costMs", System.currentTimeMillis() - ms);
                if (reply != null && StringUtils.hasText(reply.answer())) {
                    answers.add(reply.answer());
                } else {
                    answers.add("（无输出）");
                }
            } catch (Exception e) {
                step.put("status", "failed");
                step.put("error", e.getMessage());
                step.put("costMs", System.currentTimeMillis() - ms);
                failed.add(member.getName() + ": " + e.getMessage());
            }
            steps.add(step);
        }
        long cost = System.currentTimeMillis() - start;

        AgentTeamRun run = new AgentTeamRun();
        run.setTenantId(tenant());
        run.setTeamId(teamId);
        run.setInput(input);
        run.setRoutedMember(picked.stream().map(AgentTeamMember::getName).collect(Collectors.joining("、")));
        try {
            run.setTraceJson(objectMapper.writeValueAsString(steps));
        } catch (Exception ignored) {
            run.setTraceJson(null);
        }
        if (failed.size() == picked.size()) {
            run.setStatus("failed");
            run.setError(String.join("；", failed));
            run.setAnswer(null);
        } else {
            run.setStatus("success");
            run.setError(null);
            String answer;
            if (answers.size() == 1) {
                answer = answers.get(0);
            } else {
                StringBuilder sb = new StringBuilder();
                int i = 0;
                for (Map<String, Object> step : steps) {
                    if ("success".equals(step.get("status"))) {
                        sb.append("【").append(step.get("memberName")).append("】\n")
                                .append(String.valueOf(step.getOrDefault("answer", ""))).append("\n\n");
                    }
                    i++;
                }
                answer = sb.toString().trim();
            }
            run.setAnswer(answer);
        }
        run.setCostMs(cost);
        run.setCreateTime(LocalDateTime.now());
        run.setFinishTime(LocalDateTime.now());
        runMapper.insert(run);

        // 自增运行计数（round_robin 用于下一个游标）
        int next = (team.getRunCount() == null ? 0 : team.getRunCount()) + 1;
        teamMapper.update(null, new LambdaUpdateWrapper<AgentTeam>()
                .eq(AgentTeam::getId, teamId)
                .set(AgentTeam::getRunCount, next)
                .set(AgentTeam::getUpdateTime, LocalDateTime.now()));
        return run;
    }

    private List<AgentTeamMember> pickMembers(AgentTeam team, List<AgentTeamMember> enabled, String input) {
        if ("round_robin".equals(team.getRouting())) {
            int n = enabled.size();
            int idx = (team.getRunCount() == null ? 0 : team.getRunCount()) % n;
            return List.of(enabled.get(idx));
        }
        if ("all".equals(team.getRouting())) {
            return enabled;
        }
        // first_match：意图关键词优先，未命中回退到优先级最低的成员
        for (AgentTeamMember m : enabled) {
            if (!StringUtils.hasText(m.getKeywords())) {
                continue;
            }
            String lower = input.toLowerCase();
            for (String kw : m.getKeywords().split("[,，、\\s]+")) {
                if (StringUtils.hasText(kw) && lower.contains(kw.toLowerCase())) {
                    return List.of(m);
                }
            }
        }
        return List.of(enabled.get(0));
    }

    public Page<AgentTeamRun> pageRuns(Long teamId, long page, long size) {
        return runMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<AgentTeamRun>()
                .eq(AgentTeamRun::getTeamId, teamId)
                .orderByDesc(AgentTeamRun::getId));
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
