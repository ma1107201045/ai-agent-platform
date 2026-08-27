package com.agent.platform.controller;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.ModelInfo;
import com.agent.platform.dao.entity.ModelProvider;
import com.agent.platform.service.ModelService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    // ---------- 供应商 ----------

    @GetMapping("/providers")
    public Result<Page<ModelProvider>> providerPage(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return Result.ok(modelService.providerPage(page, size));
    }

    @PostMapping("/providers")
    public Result<ModelProvider> createProvider(@RequestBody ModelProvider provider) {
        return Result.ok(modelService.createProvider(provider));
    }

    @PutMapping("/providers/{id}")
    public Result<Void> updateProvider(@PathVariable Long id, @RequestBody ModelProvider provider) {
        provider.setId(id);
        modelService.updateProvider(provider);
        return Result.ok();
    }

    @DeleteMapping("/providers/{id}")
    public Result<Void> deleteProvider(@PathVariable Long id) {
        modelService.deleteProvider(id);
        return Result.ok();
    }

    // ---------- 模型 ----------

    @GetMapping("/providers/{providerId}/models")
    public Result<List<ModelInfo>> modelsOf(@PathVariable Long providerId) {
        return Result.ok(modelService.modelsOf(providerId));
    }

    @PostMapping("/providers/{providerId}/models")
    public Result<ModelInfo> createModel(@PathVariable Long providerId, @RequestBody ModelInfo model) {
        model.setProviderId(providerId);
        return Result.ok(modelService.createModel(model));
    }

    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
        return Result.ok();
    }

    /** 可用向量模型列表（供知识库配置下拉） */
    @GetMapping("/embedding-models")
    public Result<List<ModelService.ChatModelInfo>> embeddingModels() {
        return Result.ok(modelService.embeddingModels());
    }

    /** 可用重排序模型列表 */
    @GetMapping("/rerank-models")
    public Result<List<ModelService.ChatModelInfo>> rerankModels() {
        return Result.ok(modelService.rerankModels());
    }
}
