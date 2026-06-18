package com.merchant.review.controller;


import cn.hutool.core.bean.BeanUtil;
import com.merchant.review.dto.LoginFormDTO;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.entity.UserInfo;
import com.merchant.review.entity.User;
import com.merchant.review.service.IUserInfoService;
import com.merchant.review.service.IUserService;
import com.merchant.review.utils.UserHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Tag(name = "用户")
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码 -- Redis版本
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        log.info("【请求】发送验证码，手机号：{}", phone);
        return userService.sendCode(phone, session);
    }
    // ======================== Session版本 ========================
    // @PostMapping("code")
    // public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
    //     // 1. 校验手机号
    //     if (RegexUtils.isPhoneInvalid(phone)) {
    //         return Result.fail("手机号格式不正确！");
    //     }
    //     // 2. 生成验证码
    //     String code = RandomUtil.randomNumbers(6);
    //     // 3. 保存验证码到session
    //     session.setAttribute("code", code);
    //     // 4. 发送验证码（模拟：打印日志）
    //     log.debug("发送短信验证码成功，手机号：{}，验证码：{}", phone, code);
    //     return Result.ok();
    // }

    /**
     * 登录功能 -- Redis版本
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        log.info("【请求】用户登录，手机号：{}", loginForm.getPhone());
        return userService.login(loginForm, session);
    }
    // ======================== Session版本 ========================
    // @PostMapping("/login")
    // public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
    //     String phone = loginForm.getPhone();
    //     String code = loginForm.getCode();
    //     String password = loginForm.getPassword();
    //     // 1. 校验手机号
    //     if (RegexUtils.isPhoneInvalid(phone)) {
    //         return Result.fail("手机号格式不正确！");
    //     }
    //     // 2. 判断登录方式
    //     if (code != null && !code.isEmpty()) {
    //         // 验证码登录
    //         String cacheCode = (String) session.getAttribute("code");
    //         if (cacheCode == null || !cacheCode.equals(code)) {
    //             return Result.fail("验证码错误！");
    //         }
    //     } else {
    //         // 密码登录
    //         User user = userService.query().eq("phone", phone).one();
    //         if (user == null || !PasswordEncoder.matches(user.getPassword(), password)) {
    //             return Result.fail("手机号或密码错误！");
    //         }
    //     }
    //     // 3. 查询用户，不存在则创建
    //     User user = userService.query().eq("phone", phone).one();
    //     if (user == null) {
    //         user = new User();
    //         user.setPhone(phone);
    //         user.setNickName("user_" + RandomUtil.randomString(10));
    //         userService.save(user);
    //     }
    //     // 4. 保存用户到session
    //     UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    //     session.setAttribute("user", userDTO);
    //     return Result.ok();
    // }

    /**
     * 登出功能 -- Redis版本
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token){
        log.info("【请求】用户登出");
        if (token == null) {
            log.warn("【请求】登出失败，token为空");
            return Result.fail("用户未登录！");
        }
        return userService.logout(token);
    }
    // ======================== Session版本 ========================
    // @PostMapping("/logout")
    // public Result logout(HttpSession session){
    //     session.removeAttribute("user");
    //     return Result.ok();
    // }

    /**
     * 获取当前登录用户 -- Redis版本（通过拦截器从ThreadLocal获取）
     */
    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        log.info("【请求】获取当前登录用户，用户ID：{}", user != null ? user.getId() : "未登录");
        if (user == null) {
            return Result.fail("用户未登录！");
        }
        return Result.ok(user);
    }
    // ======================== Session版本 ========================
    // @GetMapping("/me")
    // public Result me(HttpSession session){
    //     UserDTO user = (UserDTO) session.getAttribute("user");
    //     return Result.ok(user);
    // }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        log.info("【请求】查询用户信息，用户ID：{}", userId);
        User user = userService.getById(userId);
        if (user == null) {
            log.warn("【响应】用户不存在，ID：{}", userId);
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @PostMapping
    public Result saveUser(@RequestBody User user){
        log.info("【请求】创建用户，手机号：{}", user.getPhone());
        boolean success = userService.save(user);
        if (!success) {
            log.error("【响应】用户创建失败");
            return Result.fail("用户创建失败");
        }
        log.info("【响应】用户创建成功，ID：{}", user.getId());
        return Result.ok(user.getId());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        log.info("【请求】查询用户详情，用户ID：{}", userId);
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            log.info("【响应】用户详情为空（首次查看）");
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }
}

// ============================================================
// =================== Session版本（完整实现） ===================
// ============================================================
// @Slf4j
// @RestController
// @RequestMapping("/user")
// public class UserController {
//
//     @Resource
//     private IUserService userService;
//
//     @Resource
//     private IUserInfoService userInfoService;
//
//     /**
//      * 发送手机验证码
//      */
//     @PostMapping("code")
//     public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
//         // 1. 校验手机号
//         if (RegexUtils.isPhoneInvalid(phone)) {
//             return Result.fail("手机号格式不正确！");
//         }
//         // 2. 生成验证码
//         String code = RandomUtil.randomNumbers(6);
//         // 3. 保存验证码到session
//         session.setAttribute("code", code);
//         // 4. 发送验证码（模拟：打印日志）
//         log.debug("发送短信验证码成功，手机号：{}，验证码：{}", phone, code);
//         return Result.ok();
//     }
//
//     /**
//      * 登录功能
//      * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
//      */
//     @PostMapping("/login")
//     public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
//         String phone = loginForm.getPhone();
//         String code = loginForm.getCode();
//         String password = loginForm.getPassword();
//         // 1. 校验手机号
//         if (RegexUtils.isPhoneInvalid(phone)) {
//             return Result.fail("手机号格式不正确！");
//         }
//         // 2. 判断登录方式
//         if (code != null && !code.isEmpty()) {
//             // 验证码登录
//             String cacheCode = (String) session.getAttribute("code");
//             if (cacheCode == null || !cacheCode.equals(code)) {
//                 return Result.fail("验证码错误！");
//             }
//             session.removeAttribute("code");
//         } else {
//             // 密码登录
//             User user = userService.query().eq("phone", phone).one();
//             if (user == null || !PasswordEncoder.matches(user.getPassword(), password)) {
//                 return Result.fail("手机号或密码错误！");
//             }
//         }
//         // 3. 查询用户，不存在则创建
//         User user = userService.query().eq("phone", phone).one();
//         if (user == null) {
//             user = new User();
//             user.setPhone(phone);
//             user.setNickName("user_" + RandomUtil.randomString(10));
//             userService.save(user);
//         }
//         // 4. 保存用户到session
//         UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//         session.setAttribute("user", userDTO);
//         return Result.ok();
//     }
//
//     /**
//      * 登出功能
//      */
//     @PostMapping("/logout")
//     public Result logout(HttpSession session){
//         session.removeAttribute("user");
//         return Result.ok();
//     }
//
//     /**
//      * 获取当前登录用户
//      */
//     @GetMapping("/me")
//     public Result me(HttpSession session){
//         UserDTO user = (UserDTO) session.getAttribute("user");
//         if (user == null) {
//             return Result.fail("用户未登录！");
//         }
//         return Result.ok(user);
//     }
//
//     @GetMapping("/info/{id}")
//     public Result info(@PathVariable("id") Long userId){
//         UserInfo info = userInfoService.getById(userId);
//         if (info == null) {
//             return Result.ok();
//         }
//         info.setCreateTime(null);
//         info.setUpdateTime(null);
//         return Result.ok(info);
//     }
// }
