package com.merchant.review.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.LoginFormDTO;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.entity.User;
import com.merchant.review.mapper.UserMapper;
import com.merchant.review.service.IUserService;
import com.merchant.review.utils.PasswordEncoder;
import com.merchant.review.utils.RedisUtils;
import com.merchant.review.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.merchant.review.utils.RedisConstants.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private RedisUtils redisUtils;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        log.info("【发送验证码】收到验证码请求，手机号：{}", phone);
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.warn("【发送验证码】手机号格式校验失败：{}", phone);
            return Result.fail("手机号格式不正确！");
        }
        String code = RandomUtil.randomNumbers(6);
        redisUtils.setPlain(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("【发送验证码】验证码已存入Redis，手机号：{}，验证码：{}，有效期：{}分钟", phone, code, LOGIN_CODE_TTL);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        String password = loginForm.getPassword();
        log.info("【用户登录】登录请求，手机号：{}，登录方式：{}", phone, code != null ? "验证码登录" : "密码登录");
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.warn("【用户登录】手机号格式校验失败：{}", phone);
            return Result.fail("手机号格式不正确！");
        }
        if (code != null && !code.isEmpty()) {
            return loginByCode(phone, code);
        }
        return loginByPassword(phone, password);
    }

    @Override
    public Result logout(String token) {
        log.info("【用户登出】开始登出，token：{}", token);
        redisUtils.delete(LOGIN_USER_KEY + token);
        log.info("【用户登出】Redis中的用户信息已清除");
        return Result.ok();
    }

    private Result loginByCode(String phone, String code) {
        log.info("【验证码登录】开始校验验证码，手机号：{}", phone);
        if (RegexUtils.isCodeInvalid(code)) {
            log.warn("【验证码登录】验证码格式不正确：{}", code);
            return Result.fail("验证码格式不正确！");
        }
        String cacheCode = redisUtils.getPlain(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            log.warn("【验证码登录】验证码错误或已过期，手机号：{}，用户输入：{}，Redis中：{}", phone, code, cacheCode);
            return Result.fail("验证码错误或已过期！");
        }
        redisUtils.delete(LOGIN_CODE_KEY + phone);
        log.info("【验证码登录】验证码校验通过，手机号：{}", phone);
        User user = query().eq("phone", phone).one();
        if (user == null) {
            log.info("【验证码登录】该手机号为新用户，自动创建账号");
            user = createUserWithPhone(phone);
        } else {
            log.info("【验证码登录】已有账号，用户ID：{}，昵称：{}", user.getId(), user.getNickName());
        }
        return saveUserToRedis(user);
    }

    private Result loginByPassword(String phone, String password) {
        log.info("【密码登录】开始校验密码，手机号：{}", phone);
        if (password == null || password.isEmpty()) {
            log.warn("【密码登录】密码为空");
            return Result.fail("密码不能为空！");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            log.warn("【密码登录】用户不存在，手机号：{}", phone);
            return Result.fail("用户不存在！");
        }
        if (!PasswordEncoder.matches(user.getPassword(), password)) {
            log.warn("【密码登录】密码错误，手机号：{}", phone);
            return Result.fail("密码错误！");
        }
        log.info("【密码登录】密码校验通过，用户ID：{}", user.getId());
        return saveUserToRedis(user);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        log.info("【创建用户】新用户创建成功，手机号：{}，用户ID：{}，昵称：{}", phone, user.getId(), user.getNickName());
        return user;
    }

    private Result saveUserToRedis(User user) {
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", userDTO.getId().toString());
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon() == null ? "" : userDTO.getIcon());
        userMap.put("role", String.valueOf(user.getRole() != null ? user.getRole() : 0));

        String key = LOGIN_USER_KEY + token;
        redisUtils.hashPutAll(key, userMap, LOGIN_USER_TTL, TimeUnit.SECONDS);
        log.info("【保存登录态】用户登录信息已存入Redis，用户ID：{}，token：{}，有效期：{}秒", user.getId(), token, LOGIN_USER_TTL);
        return Result.ok(token);
    }
}
