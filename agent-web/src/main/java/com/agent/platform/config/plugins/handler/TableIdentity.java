package com.agent.platform.config.plugins.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** PostgreSQL 表标识：未引用的标识符转小写，引用标识符保留大小写及内部的点。 */
record TableIdentity(String schema, String table) {
    static TableIdentity of(String name, String defaultSchema) {
        List<String> parts = identifiers(name);
        if (parts.size() == 1) {
            List<String> schema = identifiers(defaultSchema);
            if (schema.size() != 1) throw new IllegalArgumentException("默认 schema 必须是单个标识符");
            return new TableIdentity(schema.getFirst(), parts.getFirst());
        }
        if (parts.size() == 2) return new TableIdentity(parts.get(0), parts.get(1));
        throw new IllegalArgumentException("不支持的表限定名称：" + name);
    }

    private static List<String> identifiers(String name) {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean quoted = false;
        for (int i = 0; i < name.length(); i++) {
            char value = name.charAt(i);
            if (quote != 0) {
                if (value == quote) {
                    if (i + 1 < name.length() && name.charAt(i + 1) == quote) {
                        token.append(value);
                        i++;
                    } else quote = 0;
                } else token.append(value);
            } else if (value == '"' || value == '`') {
                quote = value;
                quoted = true;
            } else if (value == '.') {
                result.add(identifier(token, quoted));
                token.setLength(0);
                quoted = false;
            } else if (!Character.isWhitespace(value)) token.append(value);
        }
        if (quote != 0) throw new IllegalArgumentException("表名引号未闭合：" + name);
        result.add(identifier(token, quoted));
        return result;
    }

    private static String identifier(StringBuilder value, boolean quoted) {
        if (value.isEmpty()) throw new IllegalArgumentException("表标识符不能为空");
        return quoted ? value.toString() : value.toString().toLowerCase(Locale.ROOT);
    }
}
