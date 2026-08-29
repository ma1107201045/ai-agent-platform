package com.agent.platform.workflow.node;

import lombok.Data;

/**
 * 节点执行结果
 * <p>
 * 扩展节点时通过 {@link #selectedHandle} 决定分支行为：
 * <ul>
 *   <li>为 null：全部出边正常释放（并行 fork）</li>
 *   <li>非 null：仅 sourceHandle 与之匹配的出边正常释放，其余出边及其下游整链跳过（排他分支，如条件节点）</li>
 * </ul>
 * handle 为字符串，条件节点多分支场景下可取任意分支 key，
 * 二分支场景沿用约定值 {@code "true"} / {@code "false"}，默认分支为 {@code "else"}。
 */
@Data
public class NodeResult {

    /** 节点输出文本，可被下游通过 {{节点id}} 引用 */
    private String output;

    /** 排他分支选中的出边 handle；null 表示非排他 */
    private String selectedHandle;

    /** 无输出、非排他 */
    public static NodeResult empty() {
        return new NodeResult();
    }

    /** 有输出、非排他 */
    public static NodeResult of(String output) {
        NodeResult r = new NodeResult();
        r.setOutput(output);
        return r;
    }

    /** 无输出、排他分支（选中 handle） */
    public static NodeResult branch(String selectedHandle) {
        NodeResult r = new NodeResult();
        r.setSelectedHandle(selectedHandle);
        return r;
    }

    /** 有输出 + 排他分支（选中 handle） */
    public static NodeResult of(String output, String selectedHandle) {
        NodeResult r = new NodeResult();
        r.setOutput(output);
        r.setSelectedHandle(selectedHandle);
        return r;
    }
}
