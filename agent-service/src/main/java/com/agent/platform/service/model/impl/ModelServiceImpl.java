package com.agent.platform.service.model.impl;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.CryptoUtil;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.agent.platform.service.model.ModelService;

/**
 * ModelService 接口实现。
 */
@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final ModelProviderMapper providerMapper;

    private final ModelInfoMapper modelInfoMapper;

    private final CryptoUtil cryptoUtil;

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
        result.getRecords().forEach(this::maskApiKey);
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
        provider.setApiKey(cryptoUtil.encrypt(provider.getApiKey()));
        providerMapper.insert(provider);
        return maskApiKey(provider);
    }

    @Override
    public void updateProvider(ModelProvider provider) {
        getProvider(provider.getId());
        // 前端回传的是脱敏掩码（含 ****）或空值时，不修改已存储的密钥；真实新密钥加密后落库
        if (!StringUtils.hasText(provider.getApiKey()) || provider.getApiKey().contains("****")) {
            provider.setApiKey(null);
        } else {
            provider.setApiKey(cryptoUtil.encrypt(provider.getApiKey()));
        }
        provider.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(provider);
    }

    /** apiKey 脱敏：先解密（若为密文）再保留前 3 后 4 位，中间以 **** 替代，避免密钥随接口明文返回前端 */
    private ModelProvider maskApiKey(ModelProvider provider) {
        if (provider != null) {
            provider.setApiKey(mask(cryptoUtil.decrypt(provider.getApiKey())));
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
        return listEnabledModels("llm");
    }

    /**
     * 全部启用的模型列表（不限类型，供模型网关等场景下拉选择）
     */
    @Override
    public List<ModelInfoVO> allModels() {
        return listEnabledModels(null);
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
        return listEnabledModels("embedding");
    }

    /**
     * 可用的重排序模型列表
     */
    @Override
    public List<ModelInfoVO> rerankModels() {
        return listEnabledModels("rerank");
    }

    /**
     * 启用模型列表：一次性批量查询供应商（避免逐条 selectById 的 N+1），
     * 仅返回供应商启用 + 模型启用的条目。
     *
     * @param modelType 模型类型过滤，null 表示不限
     */
    private List<ModelInfoVO> listEnabledModels(String modelType) {
        LambdaQueryWrapper<ModelInfo> qw = new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId);
        if (modelType != null) {
            qw.eq(ModelInfo::getModelType, modelType);
        }
        List<ModelInfo> infos = modelInfoMapper.selectList(qw);
        if (infos.isEmpty()) {
            return List.of();
        }
        Map<Long, ModelProvider> providers = enabledProvidersById(
                infos.stream().map(ModelInfo::getProviderId).collect(Collectors.toSet()));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providers.get(info.getProviderId());
            if (provider != null) {
                result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
            }
        }
        return result;
    }

    /** 按 id 集合批量加载供应商，仅保留启用状态的，返回 id → 供应商 映射 */
    private Map<Long, ModelProvider> enabledProvidersById(Set<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Map.of();
        }
        List<ModelProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<ModelProvider>()
                .in(ModelProvider::getId, providerIds)
                .eq(ModelProvider::getStatus, 1));
        return providers.stream().collect(Collectors.toMap(ModelProvider::getId, p -> p));
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
        if (infos.isEmpty()) {
            return List.of();
        }
        // 批量加载供应商（含禁用，由前端标注状态），避免 N+1
        Set<Long> providerIds = infos.stream().map(ModelInfo::getProviderId).collect(Collectors.toSet());
        Map<Long, ModelProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<ModelProvider>()
                        .in(ModelProvider::getId, providerIds))
                .stream().collect(Collectors.toMap(ModelProvider::getId, p -> p));
        List<ModelPlaygroundVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providers.get(info.getProviderId());
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
