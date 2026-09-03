package com.agent.platform.orchestrator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流节点类型枚举
 * <p>
 * 引擎内部一律使用本枚举表达节点类型，避免散落的字符串字面量。
 * 与前端交互时通过 {@link #getCode()} 序列化为约定的小写标识（如 {@code "llm"}），
 * 因此替换后对外 JSON 契约保持不变。
 * <p>
 * 新增节点类型：在此增加枚举值，并实现对应的 {@code NodeHandler}。
 */
public enum NodeType {

    START("start", false, "开始"),
    END("end", false, "结束"),
    LLM("llm", false, "LLM"),
    AGENT("agent", false, "Agent"),
    CONDITION("condition", true, "条件分支"),
    CODE("code", false, "表达式计算"),
    HTTP("http", false, "HTTP 请求"),
    TEMPLATE("template", false, "模板"),
    KNOWLEDGE("knowledge", false, "知识库检索");

    private static final Map<String, NodeType> BY_CODE = Arrays.stream(values()).collect(Collectors.toMap(NodeType::getCode, Function.identity()));

    /** 与前端 DSL 一致的类型标识 */
    private final String code;
    /** 是否为排他分支节点：执行后仅释放选中的一条出边 */
    @Getter
    private final boolean branch;
    /** 默认展示名（节点未配置 label 时回退使用） */
    @Getter
    private final String label;

    NodeType(String code, boolean branch, String label) {
        this.code = code;
        this.branch = branch;
        this.label = label;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 按 code 解析；未知或 null 返回 null。
     * 大小写与首尾空白不敏感。
     */
    @JsonCreator
    public static NodeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        return BY_CODE.get(code.trim().toLowerCase());
    }
}
