package com.agent.platform.graph.node;

import com.agent.platform.graph.VariableRenderer;
import com.agent.platform.graph.WorkflowGraph;
import com.agent.platform.graph.spi.KnowledgeProvider;
import com.agent.platform.graph.spi.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

/**
 * 节点执行上下文：向 {@link NodeHandler} 暴露执行所需的一切能力。
 * <p>
 * 处理器通过它读取配置、渲染变量、写出输出、调用外部能力，
 * 从而与引擎调度逻辑解耦。
 * <p>
 * 外部能力以 SPI 形式注入（{@link ModelProvider} / {@link KnowledgeProvider}），
 * 引擎模块不依赖任何业务实现类。
 *
 * @param userInput 用户原始输入
 * @param outputs   全局节点输出表（key 为节点 id）；处理器通过 {@link #emit(String)} 写入自己的输出
 */
public record NodeContext(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs,
                          VariableRenderer renderer, ModelProvider modelProvider,
                          KnowledgeProvider knowledgeProvider, ObjectMapper objectMapper) {

    public NodeContext(WorkflowGraph.WorkflowNode node,
                       String userInput,
                       Map<String, String> outputs,
                       VariableRenderer renderer,
                       ModelProvider modelProvider,
                       KnowledgeProvider knowledgeProvider,
                       ObjectMapper objectMapper) {
        this.node = node;
        this.userInput = userInput == null ? "" : userInput;
        this.outputs = outputs;
        this.renderer = renderer;
        this.modelProvider = modelProvider;
        this.knowledgeProvider = knowledgeProvider;
        this.objectMapper = objectMapper;
    }

    // ---------- 节点信息 ----------

    public String nodeId() {
        return node.getId();
    }

    /**
     * 节点展示名，未设置 label 时回退为类型名
     */
    public String label() {
        String l = node.getLabel();
        return (l == null || l.isBlank()) ? node.getType() : l;
    }

    /**
     * 节点配置，永远非 null
     */
    public Map<String, Object> config() {
        Map<String, Object> cfg = node.getConfig();
        return cfg == null ? Collections.emptyMap() : cfg;
    }

    // ---------- 配置读取（统一处理 null / 空白 / 字符串 "null"）----------

    /**
     * 读取字符串配置；缺失或为 "null"、空白时返回 null
     */
    public String cfgStr(String key) {
        return cfgStr(key, null);
    }

    public String cfgStr(String key, String def) {
        Object v = config().get(key);
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v);
        return (s.isBlank() || "null".equalsIgnoreCase(s)) ? def : s;
    }

    /**
     * 读取整型配置，缺省返回 def
     */
    public int cfgInt(String key, int def) {
        Object v = config().get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = cfgStr(key);
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 读取长整型配置，缺失返回 null
     */
    public Long cfgLong(String key) {
        Object v = config().get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = cfgStr(key);
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取浮点配置，缺失返回 null
     */
    public Double cfgDouble(String key) {
        Object v = config().get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = cfgStr(key);
        if (s == null) {
            return null;
        }
        try {
            return Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------- 变量与输出 ----------

    /**
     * 渲染模板：{{input}} 用户输入；{{节点id}} 对应节点输出
     */
    public String render(String template) {
        return renderer.render(template, userInput, outputs);
    }

    /**
     * 写入本节点输出，供下游 {{节点id}} 引用
     */
    public void emit(String text) {
        outputs.put(node.getId(), text == null ? "" : text);
    }
}
