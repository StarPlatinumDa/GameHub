package com.ynudp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ynudp.dto.LoginFormDTO;
import com.ynudp.dto.Result;
import com.ynudp.entity.User;

import javax.servlet.http.HttpSession;


public interface UserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
