package com.merchant.review.config;

import com.merchant.review.interceptor.LoginInterceptor;
import com.merchant.review.interceptor.MerchantInterceptor;
import com.merchant.review.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

/**
 * MVC配置 - 注册拦截器：
 * 1. RefreshTokenInterceptor（order=0）：对所有请求刷新token并存入ThreadLocal，始终放行
 * 2. LoginInterceptor（order=1）：对用户端需要登录的路径校验，未登录返回401
 * 3. MerchantInterceptor（order=2）：对商家端接口校验角色是否为商家
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
                        "/upload/**",
                        "/merchant/**",
                        "/review/**"
                )
                .order(1);

        // 拦截器3：商家角色校验拦截器
        registry.addInterceptor(new MerchantInterceptor())
                .addPathPatterns("/merchant/**")
                .order(2);
    }
}
