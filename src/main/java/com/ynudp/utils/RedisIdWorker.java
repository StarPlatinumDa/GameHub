package com.ynudp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    // 2025年1月1日0时0分0秒  开始时间戳
    private static final long BEGIN_TIMESTAMP = 1735689600L;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取下一个id
     * @param keyprefix 不同的key有不同的自增长
     * @return
     */
    public long nextId(String keyprefix){
        // 符号位0+32bit时间戳+32bit自增序列号
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        ValueOperations operations = redisTemplate.opsForValue();
        // key长时间也需要有变化，因为redis自增只有2^64，或者说64位迟早会超过后面的32位序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 使得每天的key都不同，就不会重复了 "20250819"
        Long count = operations.increment("icr:" + keyprefix + ":" + date);

        // 3.拼接返回 加号也可以，或运算更快
        return timestamp<<32|count;
    }

//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }

}
