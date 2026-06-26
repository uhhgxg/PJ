package com.merchant.review.interceptor;

import cn.hutool.json.JSONUtil;
import com.merchant.review.dto.Result;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 商家角色拦截器：校验当前用户是否为商家（role=1）
 * 依赖 RefreshTokenInterceptor 先执行，将用户信息存入 ThreadLocal
 */
@Slf4j
public class MerchantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var user = UserHolder.getUser();
        if (user == null || !Integer.valueOf(1).equals(user.getRole())) {
            log.warn("【商家拦截】非商家用户访问商家端接口：{}", request.getRequestURI());
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSONUtil.toJsonStr(Result.fail("仅商家可执行此操作")));
            return false;
        }
        return true;
    }
}
