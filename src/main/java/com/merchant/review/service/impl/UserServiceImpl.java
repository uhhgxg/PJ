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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.merchant.review.utils.RedisConstants.*;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private RedisUtils redisUtils;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确！");
        }
        String code = RandomUtil.randomNumbers(6);
        redisUtils.setPlain(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功，手机号：" + phone + "，验证码：" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        String password = loginForm.getPassword();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确！");
        }
        if (code != null && !code.isEmpty()) {
            return loginByCode(phone, code);
        }
        return loginByPassword(phone, password);
    }

    @Override
    public Result logout(String token) {
        redisUtils.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    private Result loginByCode(String phone, String code) {
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式不正确！");
        }
        String cacheCode = redisUtils.getPlain(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误或已过期！");
        }
        redisUtils.delete(LOGIN_CODE_KEY + phone);
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        return saveUserToRedis(user);
    }

    private Result loginByPassword(String phone, String password) {
        if (password == null || password.isEmpty()) {
            return Result.fail("密码不能为空！");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail("用户不存在！");
        }
        if (!PasswordEncoder.matches(user.getPassword(), password)) {
            return Result.fail("密码错误！");
        }
        return saveUserToRedis(user);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    private Result saveUserToRedis(User user) {
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", userDTO.getId().toString());
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon() == null ? "" : userDTO.getIcon());

        String key = LOGIN_USER_KEY + token;
        redisUtils.hashPutAll(key, userMap, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(token);
    }
}
