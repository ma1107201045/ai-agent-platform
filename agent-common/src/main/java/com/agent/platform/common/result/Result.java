package com.agent.platform.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果。
 * actionHint：错误响应中可选的"下一步操作提示"，仅失败时可能携带，为 null 时不序列化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String actionHint;

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    public static <T> Result<T> fail(int code, String message, String actionHint) {
        return new Result<>(code, message, null, actionHint);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, null);
    }

}
