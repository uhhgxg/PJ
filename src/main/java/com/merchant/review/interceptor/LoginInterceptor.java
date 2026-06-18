package com.merchant.review.interceptor;

import cn.hutool.json.JSONUtil;
import com.merchant.review.dto.Result;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：校验用户是否已登录，未登录则返回401
 * 依赖RefreshTokenInterceptor先执行，从Redis获取用户并存入ThreadLocal
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 判断ThreadLocal中是否有用户（由RefreshTokenInterceptor负责填充）
        if (UserHolder.getUser() == null) {
            log.warn("【登录拦截】用户未登录，拦截请求：{}", request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write(JSONUtil.toJsonStr(Result.fail("用户未登录！")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return false;
        }
        log.debug("【登录拦截】已登录用户访问：{}", request.getRequestURI());
        return true;
    }
}
