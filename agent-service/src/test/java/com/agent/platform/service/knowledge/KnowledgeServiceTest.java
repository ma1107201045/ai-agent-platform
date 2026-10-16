package com.agent.platform.service.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KnowledgeService 静态算法测试：文本分块与余弦相似度
 */
class KnowledgeServiceTest {

    // ---------- splitText ----------

    @Test
    void shortTextIsSingleChunk() {
        List<String> chunks = KnowledgeService.splitText("短文本", 500, 50);
        assertEquals(1, chunks.size());
        assertEquals("短文本", chunks.get(0));
    }

    @Test
    void splitsAtSentenceBoundary() {
        // 构造 "句子。" 重复的文本，块大小 20：应在句号处断开
        String text = "一二三四五六七八九。".repeat(10); // 100 字
        List<String> chunks = KnowledgeService.splitText(text, 20, 5);
        assertTrue(chunks.size() >= 5, "应被切成多块");
        for (String c : chunks) {
            assertTrue(c.length() <= 20, "单块不应超过 chunkSize: " + c.length());
            assertTrue(c.endsWith("。"), "应优先在句号处切分: " + c);
        }
        // 拼接后内容应覆盖全文（无丢字）
        String joined = String.join("", chunks);
        assertEquals(text, joined);
    }

    @Test
    void noPunctuationFallsBackToHardCut() {
        String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 30 个无标点字符
        List<String> chunks = KnowledgeService.splitText(text, 10, 0);
        assertEquals(3, chunks.size());
        assertEquals("aaaaaaaaaa", chunks.get(0));
    }

    @Test
    void invalidParamsUseDefaultsWithoutHanging() {
        String text = "测试文本".repeat(100);
        // chunkSize<=0 默认 500；overlap>=chunkSize 时兜底防死循环
        assertEquals(1, KnowledgeService.splitText(text, 0, 0).size());
        List<String> chunks = KnowledgeService.splitText(text, 20, 20);
        assertTrue(chunks.size() > 1);
    }

    // ---------- cosine / norm ----------

    @Test
    void cosineBasics() {
        assertEquals(1.0, KnowledgeService.cosine(new float[]{1, 0}, new float[]{1, 0}), 1e-6);
        assertEquals(0.0, KnowledgeService.cosine(new float[]{1, 0}, new float[]{0, 1}), 1e-6);
        assertEquals(0.0, KnowledgeService.cosine(new float[]{0, 0}, new float[]{1, 1}), "零向量返回 0");
        assertEquals(0.0, KnowledgeService.cosine(new float[]{1}, new float[]{1, 2}), "维度不一致返回 0");
    }

    @Test
    void normCalculation() {
        assertEquals(5.0, KnowledgeService.norm(new float[]{3, 4}), 1e-6);
        assertEquals(0.0, KnowledgeService.norm(new float[0]));
    }

    @Test
    void cosineWithNormsMatchesCosine() {
        float[] a = {0.3f, -1.2f, 2.5f, 0.7f};
        float[] b = {1.1f, 0.4f, -0.9f, 2.2f};
        double expected = KnowledgeService.cosine(a, b);
        double actual = KnowledgeService.cosineWithNorms(a, KnowledgeService.norm(a), b, KnowledgeService.norm(b));
        assertEquals(expected, actual, 1e-6, "预计算模长版本应与原始实现一致");
    }

    @Test
    void cosineWithNormsHandlesZeroNorm() {
        assertEquals(0.0, KnowledgeService.cosineWithNorms(new float[]{0, 0}, 0, new float[]{1, 1}, Math.sqrt(2)));
    }
}
