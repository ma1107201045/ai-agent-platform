package com.agent.platform.graph.node;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 排他分支的出边 handle 约定
 * <p>
 * 条件类节点执行后返回 {@link NodeResult#branch(BranchHandle)}，
 * 引擎据此仅释放 {@code sourceHandle} 与之匹配的出边。
 */
public enum BranchHandle {

    /** 条件成立分支 */
    TRUE("true"),
    /** 条件不成立分支 */
    FALSE("false");

    private final String code;

    BranchHandle(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /** 布尔值转 handle */
    public static BranchHandle of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /** handle 转布尔值 */
    public boolean toBoolean() {
        return this == TRUE;
    }
}
