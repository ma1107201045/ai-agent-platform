package com.agent.platform.config.plugins.inner;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.WithItem;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 每个拦截器独立持有的 CTE 可见域；退出作用域时始终清理线程状态。
 */
final class CteScope {
    private final ThreadLocal<Deque<Set<String>>> scopes = new ThreadLocal<>();

    void process(List<WithItem> items, Consumer<SelectBody> processBody, Runnable main) {
        Set<String> visible = new HashSet<>(current());
        if (items != null) {
            if (items.stream().anyMatch(WithItem::isRecursive)) {
                items.forEach(item -> visible.add(key(item.getName())));
            }
            for (WithItem item : items) {
                Set<String> definition = new HashSet<>(visible);
                if (item.isRecursive()) {
                    definition.add(key(item.getName()));
                }
                run(definition, () -> {
                    if (item.getSubSelect() != null) {
                        process(item.getSubSelect().getWithItemsList(), processBody, () -> processBody.accept(item.getSubSelect().getSelectBody()));
                    }
                });
                visible.add(key(item.getName()));
            }
        }
        run(visible, main);
    }

    boolean isCte(Table table) {
        return (table.getSchemaName() == null || table.getSchemaName().isEmpty()) && current().contains(key(table.getName()));
    }

    private Set<String> current() {
        Deque<Set<String>> stack = scopes.get();
        return stack == null || stack.isEmpty() ? Set.of() : stack.peek();
    }

    private void run(Set<String> names, Runnable action) {
        Deque<Set<String>> stack = scopes.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            scopes.set(stack);
        }
        stack.push(names);
        try {
            action.run();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                scopes.remove();
            }
        }
    }

    private String key(String name) {
        return name.startsWith("\"") ? name.replace("\"", "") : name.toLowerCase(Locale.ROOT);
    }
}
