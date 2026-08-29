package com.agent.platform.workflow;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量渲染器：{{input}} 取用户输入；{{节点id}} 取该节点输出文本。
 * <p>
 * 无状态，供引擎与各节点处理器共用。
 */
@Component
public class VariableRenderer {

    /** 变量名允许字母、数字、下划线、点、中划线（节点 id 形如 node_1756... 或含点号路径） */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");

    /**
     * 渲染模板
     *
     * @param template  模板文本
     * @param userInput 用户原始输入
     * @param outputs   各节点输出（key 为节点 id）
     */
    public String render(String template, String userInput, Map<String, String> outputs) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = "input".equals(key)
                    ? (userInput == null ? "" : userInput)
                    : (outputs == null ? "" : outputs.getOrDefault(key, ""));
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
