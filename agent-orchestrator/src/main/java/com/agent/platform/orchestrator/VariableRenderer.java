package com.agent.platform.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量渲染器（结构化变量 v2，渐进兼容 v1）。
 * <p>
 * 语法：
 * <ul>
 *   <li>{@code {{input}}} 取用户输入；{@code {{节点id}}} / {@code {{变量名}}} 取对应输出文本（v1 原义，行为不变）</li>
 *   <li>{@code {{节点id.字段}} / {{节点id.rows[0].name}} / {{input.field}} …} 结构路径引用（v2）：
 *       基础值若为合法 JSON 对象 / 数组，则沿路径取值：标量转文本、对象 / 数组序列化为紧凑 JSON；不可解析或路径不存在渲染为空字符串</li>
 * </ul>
 * 存储层仍是字符串（向下兼容），结构化能力仅在「路径引用 + JSON 值」场景激活，
 * 纯文本变量引用与 v1 完全一致，无状态，供引擎与各节点处理器共用。
 */
@Component
@RequiredArgsConstructor
public class VariableRenderer {

    /** 变量名 / 路径：name、name.field、name[0].x、name.rows[0].name… */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w-]+(?:\\.[\\w-]+|\\[[0-9]+\\])*)\\s*}}");

    /** 路径段拆分：先字段名后下标（如 rows[0] 拆为 rows 与 0） */
    private static final Pattern PATH_PART = Pattern.compile("([\\w-]+)|\\[([0-9]+)\\]");

    private final ObjectMapper objectMapper;

    private record Segment(String field, int index) {
        boolean isIndex() {
            return field == null;
        }
    }

    /**
     * 渲染模板
     *
     * @param template  模板文本
     * @param userInput 用户原始输入
     * @param outputs   各节点输出（key 为节点 id / 变量名）
     */
    public String render(String template, String userInput, Map<String, String> outputs) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String replacement = resolve(expr, userInput, outputs);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String resolve(String expr, String userInput, Map<String, String> outputs) {
        List<Segment> segments = new ArrayList<>();
        Matcher pm = PATH_PART.matcher(expr);
        while (pm.find()) {
            if (pm.group(1) != null) {
                segments.add(new Segment(pm.group(1), -1));
            } else {
                segments.add(new Segment(null, Integer.parseInt(pm.group(2))));
            }
        }
        if (segments.isEmpty()) {
            return "";
        }
        // 1) 完整匹配既存键（兼容 v1 中可能包含点号的老式命名），优先级最高，保持旧行为
        String exact = outputs == null ? null : outputs.get(expr);
        if (exact != null) {
            return exact;
        }
        // 2) 输入与输出共享一套键空间；input 永不与 outputs 冲突
        boolean isInput = "input".equals(segments.get(0).field());
        String raw = isInput ? (userInput == null ? "" : userInput)
                : (outputs == null ? "" : outputs.getOrDefault(segments.get(0).field(), ""));
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // 3) 单段：直接原文本（结构化 JSON 也不擅自改写，避免破坏字面引用）
        if (segments.size() == 1) {
            return raw;
        }
        // 4) 多段：要求基础值为合法 JSON 对象 / 数组，否则视为不可结构化引用
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            return "";
        }
        if (root == null || root.isMissingNode()) {
            return "";
        }
        JsonNode cur = root;
        for (int i = 1; i < segments.size() && cur != null; i++) {
            Segment seg = segments.get(i);
            cur = seg.isIndex() ? cur.path(seg.index()) : cur.path(seg.field());
        }
        return stringify(cur);
    }

    private String stringify(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isContainerNode()) {
            try {
                return objectMapper.writeValueAsString(node);
            } catch (Exception e) {
                return node.toString();
            }
        }
        return node.asText();
    }
}
