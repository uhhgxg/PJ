package com.merchant.review.config;

import com.merchant.review.interceptor.LoginInterceptor;
import com.merchant.review.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * MVC配置 - 注册双拦截器：
 * 1. RefreshTokenInterceptor（order=0）：对所有请求刷新token并存入ThreadLocal，始终放行
 * 2. LoginInterceptor（order=1）：对需要登录的路径校验用户是否存在，未登录返回401
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截器1：Token刷新拦截器，对所有请求生效，始终放行
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // 拦截器2：登录校验拦截器，仅对需要登录的路径生效
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns(
                        "/user/me",
                        "/user/logout",
                        "/blog",
                        "/blog/like/**",
                        "/blog/of/me",
                        "/voucher-order/**",
                        "/upload/**"
                )
                .order(1);
    }
}
