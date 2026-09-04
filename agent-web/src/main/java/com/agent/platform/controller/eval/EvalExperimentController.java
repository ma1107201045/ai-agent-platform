package com.agent.platform.controller.eval;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.eval.EvalExperiment;
import com.agent.platform.service.eval.EvalExperimentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评测-对比实验接口
 *
 * <p>URL：/api/eval/experiments</p>
 */
@RestController
@RequestMapping("/api/eval/experiments")
@RequiredArgsConstructor
public class EvalExperimentController {

    private final EvalExperimentService experimentService;

    @GetMapping
    public Result<Page<EvalExperiment>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        return Result.ok(experimentService.page(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<EvalExperiment> get(@PathVariable Long id) {
        return Result.ok(experimentService.get(id));
    }

    @PostMapping
    public Result<EvalExperiment> create(@RequestBody EvalExperiment exp) {
        return Result.ok(experimentService.create(exp));
    }

    @PutMapping("/{id}")
    public Result<EvalExperiment> update(@PathVariable Long id, @RequestBody EvalExperiment req) {
        return Result.ok(experimentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        experimentService.delete(id);
        return Result.ok();
    }
}
