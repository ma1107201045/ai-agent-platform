package com.agent.platform.aspect;

import com.agent.platform.common.security.UserContext;
import com.agent.platform.service.sys.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;

/**
 * 操作日志切面：自动记录 controller 中 POST/PUT/DELETE 写操作。
 *
 * <p>仅记录已登录用户发起的写操作；高频执行类路径（聊天、流式运行、通知已读等）自动跳过，
 * 避免产生噪音日志。module/operation 由请求路径启发式推导。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final Set<String> ACTION_WORDS = Set.of(
            "publish", "rollback", "restore", "instantiate", "enabled", "enable",
            "run", "upload", "import", "export", "test", "cancel", "create", "remove", "save");

    private static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("app", "应用管理"), Map.entry("agents", "智能体"), Map.entry("prompts", "提示词库"),
            Map.entry("templates", "应用模板"), Map.entry("schedules", "定时任务"), Map.entry("marketplace", "应用市场"),
            Map.entry("knowledge", "知识库"), Map.entry("tool", "工具"), Map.entry("model", "模型"),
            Map.entry("publish", "发布管理"), Map.entry("eval", "评测"), Map.entry("asset", "素材管理"),
            Map.entry("memory", "记忆管理"), Map.entry("datastore", "数据存储"), Map.entry("guard", "内容安全"),
            Map.entry("sys", "系统管理"), Map.entry("chat", "对话记录"), Map.entry("ops", "观测"),
            Map.entry("orchestrator", "多智能体"));

    private final SysOperLogService operLogService;

    @Around("within(com.agent.platform.controller..*) && execution(public * *(..))")
    public Object record(ProceedingJoinPoint pjp) throws Throwable {
        // 仅记录已登录用户的写操作
        if (UserContext.getUserId() == null) {
            return pjp.proceed();
        }
        HttpServletRequest request = currentRequest();
        String httpMethod = httpMethod(pjp);
        if (httpMethod == null || request == null) {
            return pjp.proceed();
        }
        String uri = request.getRequestURI();
        if (isNoise(uri)) {
            return pjp.proceed();
        }
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            record(request, uri, httpMethod, true, null, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable throwable) {
            record(request, uri, httpMethod, false, summarize(throwable), System.currentTimeMillis() - start);
            if (throwable instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(throwable);
        }
    }

    private void record(HttpServletRequest request, String uri, String httpMethod,
                        boolean success, String errorMsg, long cost) {
        try {
            operLogService.record(
                    moduleOf(uri),
                    actionOf(httpMethod, uri),
                    httpMethod,
                    uri,
                    clientIp(request),
                    success,
                    errorMsg,
                    cost);
        } catch (Exception e) {
            log.warn("写入操作日志失败 uri={}", uri, e);
        }
    }

    /* ---------------- 辅助推导 ---------------- */

    /** 是否为 POST/PUT/DELETE 写操作，返回对应 HTTP 方法；GET 返回 null */
    private String httpMethod(ProceedingJoinPoint pjp) {
        var signature = pjp.getSignature();
        if (!(signature instanceof org.aspectj.lang.reflect.MethodSignature methodSignature)) {
            return null;
        }
        java.lang.reflect.Method method = methodSignature.getMethod();
        if (method.isAnnotationPresent(PostMapping.class)) {
            return "POST";
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return "PUT";
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return "DELETE";
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return null;
        }
        for (var requestMethod : mapping.method()) {
            if (requestMethod.name().equals("POST") || requestMethod.name().equals("PUT")
                    || requestMethod.name().equals("DELETE")) {
                return requestMethod.name();
            }
        }
        return null;
    }

    private String moduleOf(String uri) {
        String rest = uri.startsWith("/api/") ? uri.substring(5) : uri;
        String[] segs = rest.split("/");
        if (segs.length == 0) {
            return "其他";
        }
        String first = segs[0];
        if ("app".equals(first) && segs.length > 1) {
            return MODULES.getOrDefault(segs[1], "应用管理");
        }
        if ("publish".equals(first) && segs.length > 1) {
            return MODULES.getOrDefault(segs[1], "发布管理");
        }
        return MODULES.getOrDefault(first, "其他");
    }

    private String actionOf(String httpMethod, String uri) {
        String rest = uri.startsWith("/api/") ? uri.substring(5) : uri;
        String[] segs = rest.split("/");
        String tail = segs.length > 0 ? segs[segs.length - 1] : "";
        // 去掉 ID 段，取倒数第二个有意义的片段
        if (tail.matches("\\d+") && segs.length > 1) {
            tail = segs[segs.length - 2];
        }
        if ("DELETE".equals(httpMethod)) {
            return "删除";
        }
        String word = ACTION_WORDS.contains(tail) ? tail : "";
        return switch (word) {
            case "publish" -> "发布";
            case "rollback" -> "回滚";
            case "restore" -> "恢复";
            case "instantiate" -> "从模板创建";
            case "enabled", "enable" -> "启停切换";
            case "run" -> "执行";
            case "upload" -> "上传";
            case "import" -> "导入";
            case "export" -> "导出";
            case "test" -> "测试";
            case "cancel" -> "取消";
            case "create", "save" -> "新增";
            case "remove" -> "删除";
            default -> "POST".equals(httpMethod) ? "新增" : "修改";
        };
    }

    private boolean isNoise(String uri) {
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        if (uri.contains("/run-stream") || uri.contains("/notifications/") || uri.contains("feedback")
                || uri.endsWith("/chat") || uri.contains("/agent/chat") || uri.contains("/login")
                || uri.contains("/logout") || uri.endsWith("/cancel") || uri.contains("/read")) {
            return true;
        }
        // 对话调试中的同步运行会产生高频噪音
        return uri.startsWith("/api/app/agents/") && (uri.endsWith("/run") || uri.endsWith("/chat"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String summarize(Throwable throwable) {
        String msg = throwable.getMessage();
        if (StringUtils.hasText(msg)) {
            return msg.length() <= 300 ? msg : msg.substring(0, 300);
        }
        return throwable.getClass().getSimpleName();
    }
}
