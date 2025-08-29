package com.ynudp.dto;

import lombok.Data;

@Data
public class LoginFormDTO {
    // 既可以验证码登录也可以密码登录
    private String phone;
    private String code;
    private String password;
}
