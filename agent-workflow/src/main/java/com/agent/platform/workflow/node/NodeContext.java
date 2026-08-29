package com.agent.platform.workflow.node;

import com.agent.platform.workflow.VariableRenderer;
import com.agent.platform.workflow.WorkflowGraph;
import com.agent.platform.workflow.spi.AgentRunner;
import com.agent.platform.workflow.spi.KnowledgeProvider;
import com.agent.platform.workflow.spi.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 节点执行上下文：向 {@link NodeHandler} 暴露执行所需的一切能力。
 * <p>
 * 处理器通过它读取配置、渲染变量、写出输出、调用外部能力，
 * 从而与引擎调度逻辑解耦。
 * <p>
 * 外部能力以 SPI 形式注入（{@link ModelProvider} / {@link KnowledgeProvider} / {@link AgentRunner}），
 * 引擎模块不依赖任何业务实现类。
 *
 * @param appId     所属应用 ID，可为 null（Agent 节点回退应用绑定配置时使用）
 * @param userInput 用户原始输入
 * @param outputs   全局节点输出表（key 为节点 id）；处理器通过 {@link #emit(String)} 写入自己的输出
 */
public record NodeContext(WorkflowGraph.WorkflowNode node, Long appId, String userInput,
                          Map<String, String> outputs, VariableRenderer renderer, ModelProvider modelProvider,
                          KnowledgeProvider knowledgeProvider, AgentRunner agentRunner, ObjectMapper objectMapper) {

    public NodeContext(WorkflowGraph.WorkflowNode node,
                       Long appId,
                       String userInput,
                       Map<String, String> outputs,
                       VariableRenderer renderer,
                       ModelProvider modelProvider,
                       KnowledgeProvider knowledgeProvider,
                       AgentRunner agentRunner,
                       ObjectMapper objectMapper) {
        this.node = node;
        this.appId = appId;
        this.userInput = userInput == null ? "" : userInput;
        this.outputs = outputs;
        this.renderer = renderer;
        this.modelProvider = modelProvider;
        this.knowledgeProvider = knowledgeProvider;
        this.agentRunner = agentRunner;
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

    /**
     * 读取布尔配置（支持 true/1/yes/on），缺省返回 def
     */
    public boolean cfgBool(String key, boolean def) {
        Object v = config().get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        String s = cfgStr(key);
        if (s == null) {
            return def;
        }
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
    }

    /**
     * 读取长整型数组配置（前端多选为 number[]，历史数据可能是 JSON 字符串），永远非 null
     */
    public List<Long> cfgLongList(String key) {
        Object v = config().get(key);
        List<Long> ids = new ArrayList<>();
        if (v instanceof Iterable<?> it) {
            for (Object o : it) {
                Long id = toLong(o);
                if (id != null) {
                    ids.add(id);
                }
            }
            return ids;
        }
        String s = cfgStr(key);
        if (s == null) {
            return ids;
        }
        for (String part : s.replaceAll("[\\[\\]\"']", "").split(",")) {
            Long id = toLong(part.trim());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(s);
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

    /**
     * 写入一个全局变量（供下游 {{变量名}} 引用），常用于开始节点定义流程变量
     */
    public void putVar(String name, String value) {
        if (name == null || name.isBlank()) {
            return;
        }
        outputs.put(name.trim(), value == null ? "" : value);
    }
}
