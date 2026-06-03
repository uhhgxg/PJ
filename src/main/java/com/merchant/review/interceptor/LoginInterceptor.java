package com.merchant.review.interceptor;

import cn.hutool.json.JSONUtil;
import com.merchant.review.dto.Result;
import com.merchant.review.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：校验用户是否已登录，未登录则返回401
 * 依赖RefreshTokenInterceptor先执行，从Redis获取用户并存入ThreadLocal
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 判断ThreadLocal中是否有用户
        if (UserHolder.getUser() == null) {
            // 未登录，返回401
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write(JSONUtil.toJsonStr(Result.fail("用户未登录！")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return false;
        }
        // 已登录，放行
        return true;
    }
}
