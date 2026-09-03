package com.agent.platform.controller.model;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.entity.model.ModelProvider;
import com.agent.platform.service.model.ModelService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理接口：模型供应商 / 模型 CRUD、可用模型列表
 * <p>对话 / 流式对话 / 向量化等模型调用接口见 {@link ModelInvokeController}
 */
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    // ---------- 供应商 ----------

    @GetMapping("/providers")
    public Result<Page<ModelProvider>> providerPage(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String keyword) {
        return Result.ok(modelService.providerPage(page, size, keyword));
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

    @PutMapping("/models/{id}")
    public Result<Void> updateModel(@PathVariable Long id, @RequestBody ModelInfo model) {
        model.setId(id);
        modelService.updateModel(model);
        return Result.ok();
    }

    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
        return Result.ok();
    }

    // ---------- 可用模型列表（供前端下拉） ----------

    /** 可用对话模型列表 */
    @GetMapping("/models/chat-models")
    public Result<List<ModelService.ChatModelInfo>> chatModels() {
        return Result.ok(modelService.chatModels());
    }

    /** 可用向量模型列表（供知识库配置下拉） */
    @GetMapping("/models/embedding-models")
    public Result<List<ModelService.ChatModelInfo>> embeddingModels() {
        return Result.ok(modelService.embeddingModels());
    }

    /** 可用重排序模型列表 */
    @GetMapping("/models/rerank-models")
    public Result<List<ModelService.ChatModelInfo>> rerankModels() {
        return Result.ok(modelService.rerankModels());
    }
}
