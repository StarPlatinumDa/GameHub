package com.ynudp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ynudp.dto.LoginFormDTO;
import com.ynudp.dto.Result;
import com.ynudp.dto.UserDTO;
import com.ynudp.entity.User;
import com.ynudp.mapper.UserMapper;
import com.ynudp.service.UserService;
import com.ynudp.utils.RedisConstants;
import com.ynudp.utils.RegexUtils;
import com.ynudp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    // 如果是RedisTemplate就需要自定义配置类，并配置序列化器
    @Autowired//默认使用 StringRedisSerializer序列化器
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            // 2.1不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 2.2符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 3.保存验证码到session->redis中  验证码就是String类型
//        session.setAttribute("code", code);
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        // set key value ex 120    key=login:code:phone
        valueOperations.set(RedisConstants.LOGIN_CODE_KEY+phone,code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);


        // 4.发送验证码
        log.debug("发送验证码成功：{}", code);
        return Result.success();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 每次都要校验手机号的格式，避免乱填
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        // 1.校验前端给的验证码是否与 后端生成的一致
//        String  code = (String) session.getAttribute("code");
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        String code = valueOperations.get(RedisConstants.LOGIN_CODE_KEY + phone);

        // 不一致，返回错误信息
        if(code == null|| !loginForm.getCode().equals(code)){
            return Result.fail("验证码错误");
        }
        // 2.在数据库中查询手机号是否存在
        User user = query().eq("phone", phone).one();

        if (user == null){
            // 3.1不存在，创建新用户保存到数据库
            user=createUserWithPhone(phone);
        }
        // 3.2存在,保存用户到 session->redis

        // 弹幕说要保存到userDTO中，不然后面会报错
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);

        // 保存用户到redis，key为随机生成的token true不带中划线的简化版本
        String token = UUID.randomUUID().toString(true);
//        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        // 对象中有Long，所以用redisTemplate自定义序列化
        HashOperations hashOperations = redisTemplate.opsForHash();
        // 将userDTO转化成map存储
        // hutool的BeanUtil
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        hashOperations.putAll(RedisConstants.LOGIN_USER_KEY+token,userMap);
        // hash存的时候不能设置有效期,存完再设置   类似session，30分钟有效期
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,30,TimeUnit.MINUTES);

//        session.setAttribute("user", userDTO);
        return Result.success(token);
    }

    @Override
    public Result sign() {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDate now = LocalDate.now();
        // 拼接获得key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:"+userId+":"+keySuffix;
        // 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();// 1-31所以后续要减一
        // 写入bitmap
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        valueOperations.setBit(key,dayOfMonth-1,true);
        return Result.success();
    }

    /**
     * 统计连续签到天数
     * @return
     */
    public Result signCount() {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDate now = LocalDate.now();
        // 拼接获得key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:"+userId+":"+keySuffix;
        // 获取今天是本月的第几天,就查几个bit位
        int dayOfMonth = now.getDayOfMonth();


        // 不一样：获取本月截至今天为止的所有签到记录，返回的是一个十进制的数字
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        // 返回集合是因为bitfield可以同时做多个命令，这里我们只做一个，取0即可
        List<Long> result = valueOperations.bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result == null){
            // 没有任何签到记录
            return Result.success();
        }
        Long num = result.get(0);
        if (num == null|| num == 0L)return Result.success();
        // 循环遍历
        int count=0;
        while (num != 0){
            if(num%2==0){
                // 当前最后一位为0，未签到(即找到第一次不签到)
                break;
            }else {
                count++;
                num = num/2;
            }
        }
        return Result.success(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setNickName("user_"+RandomUtil.randomString(10));

//        user.setCreateTime(LocalDateTime.now());

        user.setPhone(phone);

        // mybatisplus保存，即insert
//        save(user);
        // mybatisplus会自动把自增id回填到user的id上
        userMapper.insert(user);

        return user;
    }
}
