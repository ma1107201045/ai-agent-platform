package com.agent.platform.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariableRendererTest {

    private final VariableRenderer renderer = new VariableRenderer();

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
}
