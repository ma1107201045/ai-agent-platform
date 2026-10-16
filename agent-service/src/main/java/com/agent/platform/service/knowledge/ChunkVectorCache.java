package com.agent.platform.service.knowledge;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 知识库分块向量缓存（数据集粒度）。
 * <p>
 * 未优化前每次检索都全量查出数据集所有分块（含 longtext 向量 JSON）并逐条反序列化，
 * O(n) 的 DB IO + JSON 解析随分块数线性劣化。本缓存将「解析后的向量 + 预计算模长」
 * 常驻内存，检索时只做纯计算；文档增删/重建后由 KnowledgeService 在事务提交后失效。
 * <p>
 * 上限 {@value #MAX_DATASETS} 个数据集，按最近访问时间淘汰（LRU）。
 * 单实例内存实现；集群部署需替换为集中式缓存或向量库。
 */
@Component
public class ChunkVectorCache {

    /** 最多缓存的数据集数量 */
    static final int MAX_DATASETS = 32;

    /** 已解析的分块向量（含预计算的模长） */
    public record CachedChunk(Long id, Long documentId, Integer chunkIndex,
                              String content, float[] vector, double norm) {
    }

    private record Entry(List<CachedChunk> chunks, long lastAccess) {
    }

    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();

    /**
     * 取数据集的全部分块向量；未缓存时通过 loader 加载并缓存。
     * 返回的列表只读使用，调用方不得修改。
     */
    public List<CachedChunk> get(Long datasetId, Supplier<List<CachedChunk>> loader) {
        Entry entry = cache.compute(datasetId, (k, old) ->
                old == null ? new Entry(loader.get(), now()) : new Entry(old.chunks(), now()));
        evictIfOverflow();
        return entry.chunks();
    }

    /** 失效指定数据集的缓存（文档增删、重建索引、数据集删除后调用） */
    public void evict(Long datasetId) {
        if (datasetId != null) {
            cache.remove(datasetId);
        }
    }

    /** 当前缓存的数据集数量（监控/测试用） */
    public int cachedDatasetCount() {
        return cache.size();
    }

    private void evictIfOverflow() {
        while (cache.size() > MAX_DATASETS) {
            Long oldest = null;
            long oldestAccess = Long.MAX_VALUE;
            for (Map.Entry<Long, Entry> e : cache.entrySet()) {
                if (e.getValue().lastAccess() < oldestAccess) {
                    oldestAccess = e.getValue().lastAccess();
                    oldest = e.getKey();
                }
            }
            if (oldest == null || cache.remove(oldest) == null) {
                break;
            }
        }
    }

    /** 提取为方法便于测试替换 */
    long now() {
        return System.nanoTime();
    }
}
