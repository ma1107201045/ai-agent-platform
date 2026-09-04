package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AgentTeam;
import com.agent.platform.dao.entity.app.AgentTeamMember;
import com.agent.platform.dao.entity.app.AgentTeamRun;
import com.agent.platform.service.app.AgentTeamService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多智能体编排接口
 *
 * <p>URL：/api/agent-teams</p>
 */
@RestController
@RequestMapping("/api/agent-teams")
@RequiredArgsConstructor
public class AgentTeamController {

    private final AgentTeamService teamService;

    @GetMapping
    public Result<Page<AgentTeam>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String keyword) {
        return Result.ok(teamService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(teamService.detail(id));
    }

    @PostMapping
    public Result<AgentTeam> create(@RequestBody AgentTeam team) {
        return Result.ok(teamService.create(team));
    }

    @PutMapping("/{id}")
    public Result<AgentTeam> update(@PathVariable Long id, @RequestBody AgentTeam team) {
        return Result.ok(teamService.update(id, team));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return Result.ok();
    }

    /** 全量保存成员（新增/更新/删除），返回最新成员列表 */
    @PutMapping("/{id}/members")
    public Result<List<Map<String, Object>>> saveMembers(@PathVariable Long id, @RequestBody List<AgentTeamMember> members) {
        return Result.ok(teamService.saveMembers(id, members));
    }

    /** 执行一次团队会话 {input} */
    @PostMapping("/{id}/run")
    public Result<AgentTeamRun> run(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String input = body.get("input") == null ? null : String.valueOf(body.get("input"));
        return Result.ok(teamService.run(id, input));
    }

    @GetMapping("/{id}/runs")
    public Result<Page<AgentTeamRun>> runs(@PathVariable Long id,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(teamService.pageRuns(id, page, size));
    }
}
