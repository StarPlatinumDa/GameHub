package com.ynudp.utils;

import com.ynudp.dto.UserDTO;
import com.ynudp.entity.User;

// 改回成User而非UserDTO   然后因为User包含的敏感信息太多，还是改回成UserDTO
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
