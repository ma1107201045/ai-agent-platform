package com.agent.platform.common.exception;

import com.agent.platform.common.result.Result;
import com.agent.platform.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.fail(e.getCode(), e.getMessage(), e.getActionHint());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + defaultMessage(f))
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public Result<Void> handleBadRequest(Exception e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "资源不存在: " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        String msg = e.getMessage() == null ? "" : e.getMessage();
        // LLM 上游错误：不透传原始 JSON，映射为可理解的提示与下一步动作
        // （LlmException 在 agent-llm 模块，此处按消息前缀识别，避免模块依赖倒置）
        if (msg.startsWith("LLM 调用失败 HTTP ") || msg.startsWith("LLM 流式调用失败 HTTP ")) {
            return llmError(msg);
        }
        if (msg.startsWith("LLM 调用 IO 异常") || msg.startsWith("LLM 流式调用 IO 异常")) {
            return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), "模型服务网络异常",
                    "请检查供应商服务地址（baseUrl）是否可达，稍后重试；持续失败请到「模型管理」测试连通性");
        }
        return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), "系统内部错误: " + msg);
    }

    /** LLM 上游 HTTP 错误 → 友好提示 + 操作引导 */
    private Result<Void> llmError(String msg) {
        int idx = msg.indexOf("HTTP ") + 5;
        int end = msg.indexOf(':', idx);
        int status = -1;
        try {
            status = Integer.parseInt(msg.substring(idx, end > 0 ? end : msg.length()).trim());
        } catch (NumberFormatException ignored) {
        }
        return switch (status) {
            case 400 -> Result.fail(500, "模型拒绝了请求（参数或上下文超限）",
                    "请缩短输入或清空会话历史后重试；若使用长文档，请分段提问");
            case 401, 403 -> Result.fail(500, "模型供应商鉴权失败",
                    "请到「模型管理 → 供应商管理」检查 API Key 是否正确或已过期");
            case 429 -> Result.fail(500, "模型供应商限流或余额不足",
                    "请稍后再试，或到供应商控制台检查额度/欠费情况");
            case 404 -> Result.fail(500, "模型不存在或已下线",
                    "请到「模型管理」确认模型名称与供应商控制台一致");
            default -> Result.fail(500, "模型调用失败（HTTP " + status + "）",
                    "请稍后重试；持续失败请到「模型管理」测试该模型连通性");
        };
    }

    private String defaultMessage(FieldError f) {
        return f.getDefaultMessage() != null ? f.getDefaultMessage() : "参数校验失败";
    }
}
