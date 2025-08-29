package com.ynudp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;//锁名称
    private RedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "lock:";

    // 用uuid当前缀，防止不同jvm间线程id重复  同时isSimple去掉uuid的横线
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);

    // 提前通过io流获取lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    // 静态常量在静态代码块中初始化
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name, RedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        long threadId = Thread.currentThread().getId();
        ValueOperations operations = redisTemplate.opsForValue();
        Boolean success = operations.setIfAbsent(KEY_PREFIX + name, ID_PREFIX+threadId, timeoutSec, TimeUnit.SECONDS);
        // 防止自动拆箱时，success为null，拆箱后就是空指针
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        String value = ID_PREFIX + Thread.currentThread().getId();
        // 调用Lua脚本
        redisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name), Collections.singletonList(value));
    }

//    @Override
//    public void unLock() {
//        String value = ID_PREFIX + Thread.currentThread().getId();
//        String id = (String) redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(value.equals(id)){
//            // 释放锁
//            redisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
