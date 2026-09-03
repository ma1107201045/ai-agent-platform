package com.agent.platform.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariableRendererTest {

    private final VariableRenderer renderer = new VariableRenderer(new ObjectMapper());

    @Test
    void rendersInputAndNodeOutputs() {
        String s = renderer.render("问: {{input}}；答: {{llm1}}", "你好", Map.of("llm1", "好的"));
        assertEquals("问: 你好；答: 好的", s);
    }

    @Test
    void supportsAliasAndWhitespace() {
        String s = renderer.render("{{ alias }}|{{ input }}", "x", Map.of("alias", "A"));
        assertEquals("A|x", s);
    }

    @Test
    void missingVariableBecomesEmpty() {
        assertEquals("ac", renderer.render("a{{nope}}c", "", Map.of()));
    }

    @Test
    void nullTemplateReturnsNull() {
        assertEquals(null, renderer.render(null, "u", Map.of()));
    }

    @Test
    void blankTemplateReturnedAsIs() {
        assertEquals("   ", renderer.render("   ", "u", Map.of()));
    }

    // ---------- 结构化变量 v2：JSON 路径引用 ----------

    @Test
    void plainTextVariableStillRendersRawJson() {
        // 单段引用不擅自改写 JSON 字符串（与 v1 一致）
        String json = "{\"rows\":[{\"name\":\"张三\"}]}";
        assertEquals(json, renderer.render("{{http1}}", "", Map.of("http1", json)));
    }

    @Test
    void readsObjectFieldFromJsonVariable() {
        String json = "{\"code\":0,\"rows\":[{\"name\":\"张三\",\"age\":18},{\"name\":\"李四\",\"age\":20}]}";
        assertEquals("张三", renderer.render("{{http1.rows[0].name}}", "", Map.of("http1", json)));
    }

    @Test
    void readsArrayElementByIndex() {
        assertEquals("20", renderer.render("{{http1.rows[1].age}}", "", Map.of(
                "http1", "{\"rows\":[{\"age\":18},{\"age\":20}]}")));
    }

    @Test
    void readsNestedArrayInsideArray() {
        String json = "[[1,2],{\"a\":{\"b\":\"deep\"}}]";
        assertEquals("deep", renderer.render("{{n1[1].a.b}}", "", Map.of("n1", json)));
    }

    @Test
    void jsonPathOnNonJsonTextRendersEmpty() {
        assertEquals("()", renderer.render("({{n1.a}})", "", Map.of("n1", "这不是JSON")));
    }

    @Test
    void jsonPathMissingKeyRendersEmpty() {
        assertEquals("-", renderer.render("-{{n1.nope.x}}", "", Map.of("n1", "{\"a\":1}")));
    }

    @Test
    void pathOnUserInputSupported() {
        assertEquals("晴", renderer.render("{{input.weather}}", "{\"weather\":\"晴\"}", Map.of()));
    }

    @Test
    void containerValueSerializesAsCompactJson() {
        String out = renderer.render("{{http1.rows[0]}}", "", Map.of(
                "http1", "{\"rows\":[{\"name\":\"张三\",\"age\":18}]}"));
        assertEquals("{\"name\":\"张三\",\"age\":18}", out);
    }

    @Test
    void existingDottedKeyStillTakesPriority() {
        // v1 老式含点命名：完整键存在时保持原义
        assertEquals("old", renderer.render("{{foo.bar}}", "", Map.of("foo.bar", "old")));
    }
}
