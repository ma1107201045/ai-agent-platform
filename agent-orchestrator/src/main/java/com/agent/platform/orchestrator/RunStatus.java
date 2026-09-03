package com.agent.platform.orchestrator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流整体运行状态
 * <p>
 * 与单节点状态 {@link NodeStatus} 区分：本枚举描述一次 run 的整体结果。
 * 序列化为小写标识（running / success / failed / canceled / timeout），前端契约友好。
 */
public enum RunStatus {

    /** 运行中（事件流场景下前端可感知） */
    RUNNING("running"),
    /** 执行成功 */
    SUCCESS("success"),
    /** 执行失败（节点错误且未走 continue/fallback，或 DSL 非法） */
    FAILED("failed"),
    /** 被调用方取消 */
    CANCELED("canceled"),
    /** 整体超时兜底 */
    TIMEOUT("timeout");

    private static final Map<String, RunStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(RunStatus::getCode, Function.identity()));

    private final String code;

    RunStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED || this == TIMEOUT;
    }

    /** 按 code 解析；未知或 null 返回 null */
    @JsonCreator
    public static RunStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        return BY_CODE.get(code.trim().toLowerCase());
    }
}
