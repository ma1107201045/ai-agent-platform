package com.agent.platform.service.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分块向量缓存测试
 */
class ChunkVectorCacheTest {

    private List<ChunkVectorCache.CachedChunk> fakeChunks(int n) {
        java.util.List<ChunkVectorCache.CachedChunk> list = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new ChunkVectorCache.CachedChunk((long) i, 1L, i, "内容" + i,
                    new float[]{1, 0}, 1.0));
        }
        return list;
    }

    @Test
    void loaderCalledOnlyOncePerDataset() {
        ChunkVectorCache cache = new ChunkVectorCache();
        AtomicInteger loads = new AtomicInteger();
        List<ChunkVectorCache.CachedChunk> first = cache.get(1L, () -> {
            loads.incrementAndGet();
            return fakeChunks(3);
        });
        List<ChunkVectorCache.CachedChunk> second = cache.get(1L, () -> {
            loads.incrementAndGet();
            return fakeChunks(3);
        });
        assertEquals(1, loads.get(), "命中缓存时 loader 不应重复执行");
        assertSame(first, second, "命中缓存应返回同一列表实例");
        assertEquals(1, cache.cachedDatasetCount());
    }

    @Test
    void evictForcesReload() {
        ChunkVectorCache cache = new ChunkVectorCache();
        AtomicInteger loads = new AtomicInteger();
        cache.get(1L, () -> {
            loads.incrementAndGet();
            return fakeChunks(1);
        });
        cache.evict(1L);
        cache.get(1L, () -> {
            loads.incrementAndGet();
            return fakeChunks(2);
        });
        assertEquals(2, loads.get(), "失效后应重新加载");
    }

    @Test
    void evictNullIsNoop() {
        ChunkVectorCache cache = new ChunkVectorCache();
        cache.evict(null);
        assertEquals(0, cache.cachedDatasetCount());
    }

    @Test
    void lruEvictsOldestWhenOverflow() throws InterruptedException {
        ChunkVectorCache cache = new ChunkVectorCache();
        for (long i = 1; i <= ChunkVectorCache.MAX_DATASETS; i++) {
            long id = i;
            cache.get(id, () -> fakeChunks(1));
            Thread.sleep(1); // 拉开 lastAccess
        }
        assertEquals(ChunkVectorCache.MAX_DATASETS, cache.cachedDatasetCount());
        // 新数据集进入，应淘汰最久未访问的 id=1
        cache.get(999L, () -> fakeChunks(1));
        assertEquals(ChunkVectorCache.MAX_DATASETS, cache.cachedDatasetCount(), "超上限后数量不应增长");
        AtomicInteger reloads = new AtomicInteger();
        cache.get(1L, () -> {
            reloads.incrementAndGet();
            return fakeChunks(1);
        });
        assertEquals(1, reloads.get(), "最久未访问的数据集应已被淘汰");
        assertTrue(cache.cachedDatasetCount() <= ChunkVectorCache.MAX_DATASETS);
    }
}
