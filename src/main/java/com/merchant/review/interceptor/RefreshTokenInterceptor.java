package com.merchant.review.interceptor;

import cn.hutool.core.util.StrUtil;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.merchant.review.utils.RedisConstants.LOGIN_USER_KEY;
import static com.merchant.review.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Token刷新拦截器：从请求头获取token，从Redis查询用户并存入ThreadLocal
 * 对所有请求生效，始终放行，仅负责刷新token和设置ThreadLocal
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 请求前置处理：从请求头读取token → Redis查询用户 → 存入ThreadLocal → 刷新token有效期
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取请求路径
        String requestURI = request.getRequestURI();

        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            log.debug("【Token拦截】请求 {} 无token，放行（游客模式）", requestURI);
            return true;
        }

        // 2. 从Redis查询用户信息
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            log.debug("【Token拦截】token无效或已过期，请求：{}，token：{}", requestURI, token);
            return true;
        }

        // 3. 将Map转为UserDTO
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.valueOf((String) userMap.get("id")));
        userDTO.setNickName((String) userMap.get("nickName"));
        userDTO.setIcon((String) userMap.get("icon"));

        // 4. 将用户信息存入ThreadLocal（当前线程内全局可访问）
        UserHolder.saveUser(userDTO);
        log.debug("【Token拦截】用户已登录：{}（{}），请求：{}", userDTO.getNickName(), userDTO.getId(), requestURI);

        // 5. 刷新token在Redis中的有效期（30分钟无操作自动过期）
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);

        return true;
    }

    /**
     * 请求完成后清理ThreadLocal，防止内存泄漏
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
