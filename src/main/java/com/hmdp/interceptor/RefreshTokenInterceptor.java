package com.hmdp.interceptor;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Token刷新拦截器：从请求头获取token，从Redis查询用户并存入ThreadLocal
 * 对所有请求生效，始终放行，仅负责刷新token和设置ThreadLocal
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate; // Redis操作模板

    /**
     * 构造函数，注入StringRedisTemplate
     * @param stringRedisTemplate Redis操作模板
     */
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 前置处理方法：在请求处理前执行
     * @param request 当前HTTP请求
     * @param response 当前HTTP响应
     * @param handler 请求处理方法
     * @return true表示继续流程，false表示终端流程
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true; // 如果token为空，直接放行
        }
        // 2. 从Redis获取用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            return true; // 如果用户信息为空，直接放行
        }
        // 3. 将Map转为UserDTO
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.valueOf((String) userMap.get("id")));
        userDTO.setNickName((String) userMap.get("nickName"));
        userDTO.setIcon((String) userMap.get("icon"));
        // 4. 存入ThreadLocal
        UserHolder.saveUser(userDTO);
        // 5. 刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true; // 放行请求
    }

    /**
     * 完成处理方法：在请求完成后执行
     * @param request 当前HTTP请求
     * @param response 当前HTTP响应
     * @param handler 请求处理方法
     * @param ex 处理过程中抛出的异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser(); // 清理ThreadLocal中的用户信息
    }
}
