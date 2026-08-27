package com.agent.platform.config;

import com.agent.platform.common.result.Result;
import com.agent.platform.common.result.ResultCode;
import com.agent.platform.common.security.JwtUtil;
import com.agent.platform.common.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 登录鉴权拦截器：校验 Authorization: Bearer <token>，写入用户上下文
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; // 预检请求放行
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(response, "未登录");
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            UserContext.set(new UserContext.LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("tenantId", Long.class),
                    claims.get("username", String.class)));
            return true;
        } catch (Exception e) {
            return unauthorized(response, "登录已过期，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.UNAUTHORIZED.getCode(), message)));
        return false;
    }
}
