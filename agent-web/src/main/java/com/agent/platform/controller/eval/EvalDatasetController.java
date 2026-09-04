package com.agent.platform.controller.eval;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.eval.EvalDataset;
import com.agent.platform.dao.entity.eval.EvalSample;
import com.agent.platform.service.eval.EvalDatasetService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评测-数据集接口
 *
 * <p>URL：/api/eval/datasets</p>
 */
@RestController
@RequestMapping("/api/eval/datasets")
@RequiredArgsConstructor
public class EvalDatasetController {

    private final EvalDatasetService datasetService;

    @GetMapping
    public Result<Page<EvalDataset>> page(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status) {
        return Result.ok(datasetService.page(page, size, keyword, status));
    }

    @GetMapping("/options")
    public Result<List<EvalDataset>> options() {
        return Result.ok(datasetService.options());
    }

    @GetMapping("/{id}")
    public Result<EvalDataset> get(@PathVariable Long id) {
        return Result.ok(datasetService.get(id));
    }

    @PostMapping
    public Result<EvalDataset> create(@RequestBody EvalDataset dataset) {
        return Result.ok(datasetService.create(dataset));
    }

    @PutMapping("/{id}")
    public Result<EvalDataset> update(@PathVariable Long id, @RequestBody EvalDataset req) {
        return Result.ok(datasetService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return Result.ok();
    }

    // ---------- 样本 ----------

    @GetMapping("/{id}/samples")
    public Result<Page<EvalSample>> samples(@PathVariable Long id,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String keyword) {
        return Result.ok(datasetService.samples(page, size, id, keyword));
    }

    @PostMapping("/{id}/samples")
    public Result<EvalSample> addSample(@PathVariable Long id, @RequestBody EvalSample sample) {
        return Result.ok(datasetService.addSample(id, sample));
    }

    @PutMapping("/{id}/samples/{sampleId}")
    public Result<EvalSample> updateSample(@PathVariable Long id, @PathVariable Long sampleId,
                                           @RequestBody EvalSample req) {
        return Result.ok(datasetService.updateSample(id, sampleId, req));
    }

    @DeleteMapping("/{id}/samples/{sampleId}")
    public Result<Void> deleteSample(@PathVariable Long id, @PathVariable Long sampleId) {
        datasetService.deleteSample(id, sampleId);
        return Result.ok();
    }

    /** 批量导入（JSON 数组 / 每行“提问\t参考答案”） */
    @PostMapping("/{id}/samples/import")
    public Result<Map<String, Object>> importSamples(@PathVariable Long id, @RequestBody Map<String, String> body) {
        int count = datasetService.importSamples(id, body.get("text"));
        return Result.ok(Map.of("imported", count));
    }
}
