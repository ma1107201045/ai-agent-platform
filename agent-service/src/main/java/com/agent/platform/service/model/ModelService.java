package com.agent.platform.service.model;

import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.entity.model.ModelProvider;
import com.agent.platform.dao.vo.model.ModelInfoVO;
import com.agent.platform.dao.vo.model.ModelPlaygroundVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/**
 * 模型管理服务
 */
public interface ModelService {

    Page<ModelProvider> providerPage(long page, long size, String keyword);

    ModelProvider getProvider(Long id);

    ModelProvider createProvider(ModelProvider provider);

    void updateProvider(ModelProvider provider);

    void deleteProvider(Long id);

    List<ModelInfo> modelsOf(Long providerId);

    ModelInfo createModel(ModelInfo model);

    void updateModel(ModelInfo model);

    void deleteModel(Long id);

    /**
     * 可用的对话模型列表（供应商启用 + 模型启用），供前端下拉选择
     */
    List<ModelInfoVO> chatModels();

    /**
     * 全部启用的模型列表（不限类型，供模型网关等场景下拉选择）
     */
    List<ModelInfoVO> allModels();

    /**
     * 获取默认对话模型 ID（第一个启用的 LLM），无可用模型返回 null
     */
    Long defaultChatModelId();

    /**
     * 可用的向量模型列表（供知识库配置下拉）
     */
    List<ModelInfoVO> embeddingModels();

    /**
     * 可用的重排序模型列表
     */
    List<ModelInfoVO> rerankModels();

    /**
     * 模型广场目录：列出全部供应商与其下模型（含已禁用，由前端标注状态），供广场浏览与试玩。
     * 注意：不返回供应商 apiKey 等敏感字段。
     */
    List<ModelPlaygroundVO> playgroundModels();
}
