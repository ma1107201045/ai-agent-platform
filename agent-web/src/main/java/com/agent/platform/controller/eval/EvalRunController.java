package com.agent.platform.controller.eval;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.eval.EvalRun;
import com.agent.platform.dao.entity.eval.EvalRunCase;
import com.agent.platform.dao.mapper.eval.EvalRunCaseMapper;
import com.agent.platform.dao.vo.eval.EvalRunVO;
import com.agent.platform.service.eval.EvalRunService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评测-评测中心（任务）接口
 *
 * <p>URL：/api/eval/runs</p>
 */
@RestController
@RequestMapping("/api/eval/runs")
@RequiredArgsConstructor
public class EvalRunController {

    private final EvalRunService runService;
    private final EvalRunCaseMapper caseMapper;

    /** 评测任务分页 */
    @GetMapping
    public Result<Page<EvalRunVO>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long datasetId,
                                        @RequestParam(required = false) Long experimentId,
                                        @RequestParam(required = false) String keyword) {
        return Result.ok(runService.page(page, size, status, datasetId, experimentId, keyword));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(runService.stats());
    }

    @GetMapping("/{id}")
    public Result<EvalRunVO> get(@PathVariable Long id) {
        return Result.ok(runService.get(id));
    }

    /** 创建并立即执行 */
    @PostMapping
    public Result<EvalRunVO> create(@RequestBody EvalRun req) {
        return Result.ok(runService.createAndRun(req));
    }

    /** 重跑（成功/失败/停止过的任务） */
    @PostMapping("/{id}/rerun")
    public Result<EvalRunVO> rerun(@PathVariable Long id) {
        return Result.ok(runService.rerun(id));
    }

    /** 停止运行中任务 */
    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable Long id) {
        runService.stop(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        runService.delete(id);
        return Result.ok();
    }

    /** 任务用例明细 */
    @GetMapping("/{id}/cases")
    public Result<Page<EvalRunCase>> cases(@PathVariable Long id,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) Integer passed,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(caseMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EvalRunCase>()
                        .eq(EvalRunCase::getRunId, id)
                        .eq(passed != null, EvalRunCase::getPassed, passed)
                        .and(StringUtils.hasText(keyword),
                                w -> w.like(EvalRunCase::getQuestion, keyword)
                                        .or().like(EvalRunCase::getAnswer, keyword))
                        .orderByDesc(EvalRunCase::getId)));
    }
}
