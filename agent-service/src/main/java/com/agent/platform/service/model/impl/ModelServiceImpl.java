package com.agent.platform.service.model.impl;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.entity.model.ModelProvider;
import com.agent.platform.dao.mapper.model.ModelInfoMapper;
import com.agent.platform.dao.mapper.model.ModelProviderMapper;
import com.agent.platform.dao.vo.model.ModelInfoVO;
import com.agent.platform.dao.vo.model.ModelPlaygroundVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.agent.platform.service.model.ModelService;

/**
 * ModelService 接口实现。
 */
@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final ModelProviderMapper providerMapper;

    private final ModelInfoMapper modelInfoMapper;

    // ---------- 供应商 ----------

    @Override
    public Page<ModelProvider> providerPage(long page, long size, String keyword) {
        LambdaQueryWrapper<ModelProvider> qw = new LambdaQueryWrapper<ModelProvider>()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(ModelProvider::getName, keyword)
                                .or().like(ModelProvider::getType, keyword)
                                .or().like(ModelProvider::getBaseUrl, keyword))
                .orderByDesc(ModelProvider::getId);
        Page<ModelProvider> result = providerMapper.selectPage(new Page<>(page, size), qw);
        result.getRecords().forEach(ModelServiceImpl::maskApiKey);
        return result;
    }

    @Override
    public ModelProvider getProvider(Long id) {
        ModelProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException("模型供应商不存在: " + id);
        }
        return maskApiKey(provider);
    }

    @Override
    public ModelProvider createProvider(ModelProvider provider) {
        LocalDateTime now = LocalDateTime.now();
        provider.setId(null);
        provider.setStatus(provider.getStatus() == null ? 1 : provider.getStatus());
        provider.setCreateTime(now);
        provider.setUpdateTime(now);
        if (provider.getTenantId() == null) {
            provider.setTenantId(1L);
        }
        providerMapper.insert(provider);
        return maskApiKey(provider);
    }

    @Override
    public void updateProvider(ModelProvider provider) {
        getProvider(provider.getId());
        // 前端回传的是脱敏掩码（含 ****）或空值时，不修改已存储的密钥
        if (!StringUtils.hasText(provider.getApiKey()) || provider.getApiKey().contains("****")) {
            provider.setApiKey(null);
        }
        provider.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(provider);
    }

    /** apiKey 脱敏：保留前 3 后 4 位，中间以 **** 替代，避免密钥随接口明文返回前端 */
    private static ModelProvider maskApiKey(ModelProvider provider) {
        if (provider != null) {
            provider.setApiKey(mask(provider.getApiKey()));
        }
        return provider;
    }

    private static String mask(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }

    @Override
    public void deleteProvider(Long id) {
        getProvider(id);
        providerMapper.deleteById(id);
        modelInfoMapper.delete(new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getProviderId, id));
    }

    // ---------- 模型 ----------

    @Override
    public List<ModelInfo> modelsOf(Long providerId) {
        getProvider(providerId);
        return modelInfoMapper.selectList(
                new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getProviderId, providerId).orderByDesc(ModelInfo::getId));
    }

    @Override
    public ModelInfo createModel(ModelInfo model) {
        getProvider(model.getProviderId());
        LocalDateTime now = LocalDateTime.now();
        model.setId(null);
        model.setStatus(model.getStatus() == null ? 1 : model.getStatus());
        model.setCreateTime(now);
        model.setUpdateTime(now);
        modelInfoMapper.insert(model);
        return model;
    }

    @Override
    public void updateModel(ModelInfo model) {
        if (modelInfoMapper.selectById(model.getId()) == null) {
            throw new BizException("模型不存在: " + model.getId());
        }
        model.setUpdateTime(LocalDateTime.now());
        modelInfoMapper.updateById(model);
    }

    @Override
    public void deleteModel(Long id) {
        if (modelInfoMapper.deleteById(id) == 0) {
            throw new BizException("模型不存在: " + id);
        }
    }

    // ---------- 可用模型列表（供前端下拉 / 默认模型选择） ----------

    /**
     * 可用的对话模型列表（供应商启用 + 模型启用），供前端下拉选择
     */
    @Override
    public List<ModelInfoVO> chatModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getModelType, "llm")
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    /**
     * 全部启用的模型列表（不限类型，供模型网关等场景下拉选择）
     */
    @Override
    public List<ModelInfoVO> allModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    /**
     * 获取默认对话模型 ID（第一个启用的 LLM），无可用模型返回 null
     */
    @Override
    public Long defaultChatModelId() {
        List<ModelInfoVO> list = chatModels();
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /**
     * 可用的向量模型列表（供知识库配置下拉）
     */
    @Override
    public List<ModelInfoVO> embeddingModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getModelType, "embedding")
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    /**
     * 可用的重排序模型列表
     */
    @Override
    public List<ModelInfoVO> rerankModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getModelType, "rerank")
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    // ---------- 模型广场 ----------

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 模型广场目录：列出全部供应商与其下模型（含已禁用，由前端标注状态），供广场浏览与试玩。
     * 注意：不返回供应商 apiKey 等敏感字段。
     */
    @Override
    public List<ModelPlaygroundVO> playgroundModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .orderByAsc(ModelInfo::getProviderId)
                .orderByAsc(ModelInfo::getId));
        List<ModelPlaygroundVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null) {
                continue;
            }
            result.add(ModelPlaygroundVO.builder()
                    .modelId(info.getId())
                    .modelName(info.getName())
                    .modelType(info.getModelType())
                    .contextWindow(info.getContextWindow())
                    .maxTokens(info.getMaxTokens())
                    .capabilities(parseCapabilities(info.getCapabilities()))
                    .modelStatus(info.getStatus())
                    .providerId(provider.getId())
                    .providerName(provider.getName())
                    .providerType(provider.getType())
                    .providerStatus(provider.getStatus())
                    .build());
        }
        return result;
    }

    private List<String> parseCapabilities(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
