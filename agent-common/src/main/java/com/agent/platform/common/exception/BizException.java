package com.agent.platform.common.exception;

import com.agent.platform.common.result.ResultCode;

/**
 * 业务异常。
 * <p>
 * actionHint：面向用户的"下一步可操作提示"（例如"请前往模型管理检查供应商配置"），
 * 由前端在错误提示中一并展示，避免用户看到报错后不知道去哪处理。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final String actionHint;

    public BizException(String message) {
        this(ResultCode.BAD_REQUEST.getCode(), message, null);
    }

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(String message, String actionHint) {
        this(ResultCode.BAD_REQUEST.getCode(), message, actionHint);
    }

    public BizException(int code, String message, String actionHint) {
        super(message);
        this.code = code;
        this.actionHint = actionHint;
    }

    public BizException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public int getCode() {
        return code;
    }

    public String getActionHint() {
        return actionHint;
    }
}
