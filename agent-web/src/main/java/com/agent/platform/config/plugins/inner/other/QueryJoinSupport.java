package com.agent.platform.config.plugins.inner.other;

import net.sf.jsqlparser.statement.select.Join;

import java.util.List;

/**
 * 公共基类不能安全改写的连接形式必须显式失败，不能静默遗漏过滤条件。
 */
public final class QueryJoinSupport {
    private QueryJoinSupport() {
    }

    public static void validate(List<Join> joins) {
        if (joins != null && joins.stream().anyMatch(join -> join.isFull() || join.isNatural() || join.isCross())) {
            throw new UnsupportedOperationException("暂不支持自动过滤 FULL/NATURAL/CROSS JOIN；CROSS JOIN 可改为 INNER JOIN ... ON 1 = 1");
        }
    }

    public static boolean innerOnly(List<Join> joins) {
        return joins != null && !joins.isEmpty()
                && joins.stream().allMatch(join -> !join.isLeft() && !join.isRight() && !join.isOuter());
    }
}
