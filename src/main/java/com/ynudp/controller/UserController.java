package com.ynudp.controller;


import com.ynudp.dto.LoginFormDTO;
import com.ynudp.dto.Result;
import com.ynudp.dto.UserDTO;
import com.ynudp.entity.User;
import com.ynudp.entity.UserInfo;
import com.ynudp.service.UserInfoService;
import com.ynudp.service.UserService;
import com.ynudp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserInfoService userInfoService;




    /**
     * 发送手机验证码
     */
    @PostMapping("code")
//    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
    public Result sendCode(String phone, HttpSession session) {
        // 发送短信验证码并保存验证码

        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    // 用json传递的对象必须用@RequestBody接收，用query传递的可以不加，直接用属性或者对象接收
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        log.info("登录: " + loginForm);
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    // 点击我的才会调用me
    @GetMapping("/me")
    public Result me(){
        log.info("获取当前登录用户");
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.success(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.success();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.success(info);
    }
}
