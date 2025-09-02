package com.ynudp.service.impl;

import com.ynudp.entity.UserInfo;
import com.ynudp.mapper.UserInfoMapper;
import com.ynudp.service.UserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

}
