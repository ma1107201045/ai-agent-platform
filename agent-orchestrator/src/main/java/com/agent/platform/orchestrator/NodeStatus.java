package com.agent.platform.orchestrator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 节点执行状态枚举
 * <p>
 * 序列化为约定的小写标识（success / skipped / error），前端契约不变。
 */
public enum NodeStatus {

    /** 执行成功 */
    SUCCESS("success"),
    /** 未执行：位于未选中的分支，或上游出错导致连锁跳过 */
    SKIPPED("skipped"),
    /** 执行失败 */
    ERROR("error"),
    /** 运行被取消时，已入队 / 中断的节点终态 */
    CANCELED("canceled");

    private static final Map<String, NodeStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(NodeStatus::getCode, Function.identity()));

    private final String code;

    NodeStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isError() {
        return this == ERROR;
    }

    /** 按 code 解析；未知或 null 返回 null */
    @JsonCreator
    public static NodeStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        return BY_CODE.get(code.trim().toLowerCase());
    }
}
