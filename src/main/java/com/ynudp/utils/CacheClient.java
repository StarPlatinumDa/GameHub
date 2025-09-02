package com.ynudp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    @Autowired
    private RedisTemplate redisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        // 把任意对象序列化为 JSON
        String jsonStr = JSONUtil.toJsonStr(value);
        redisTemplate.opsForValue().set(key, jsonStr, time, timeUnit);
    }

    // 不自己写线程，性能不好，用线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);




    // 逻辑过期解决缓存击穿
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        // 把任意对象序列化为 JSON
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisData.setData(value);
        String jsonStr = JSONUtil.toJsonStr(redisData);
        redisTemplate.opsForValue().set(key, jsonStr);
    }

    // 会传入需要进行数据库操作函数，ID为参数，R是返回值   泛型

    /**
     * 普通存入任意对象到redis中
     * @param keyPrefix 缓存前缀
     * @param id 对象id
     * @param type 返回值的泛型  Shop.class
     * @param dbFallback 数据库查询函数 函数式接口，传入lambda表达式
     * @param time 缓存时间
     * @param timeUnit 时间单位
     * @return R
     * @param <R> 返回值的类型 实体种类
     * @param <ID> id的类型
     */
    public <R,ID>R queryWithPassThrough(String keyPrefix,ID id,Class<R> type,
    Function<ID,R> dbFallback,Long time, TimeUnit timeUnit){
        // 1.尝试从redis中查询商铺缓存
        String key=keyPrefix+id;
        ValueOperations operations = redisTemplate.opsForValue();
        String json = (String) operations.get(key);

        // 2.判断商铺缓存是否命中  只是是否有，有可能有缓存，但内部存的是空
        if (StrUtil.isNotBlank(json)) {
            // 命中 直接返回
            return JSONUtil.toBean(json, type);
        }
        // 不命中还有存空值用于解决缓存穿透的情况
        if(json!=null){
            return null;
        }

        // 不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        // 数据库查询为空，存空值解决缓存穿透
        if (r==null){
            redisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 数据库中有值，就存到redis
        this.set(key,r,time,timeUnit);

        return r;
    }


    // 逻辑过期解决缓存击穿 函数式接口，函数式接口，参数是id，返回值是R

    /**
     *
     * @param keyPrefix 缓存前缀
     * @param id 查询id
     * @param type 返回值的泛型  Shop.class
     * @param dbFallback 数据库查询函数 函数式接口，传入lambda表达式
     * @param time 缓存时间
     * @param timeUnit 时间单位
     * @return  R
     * @param <R> 返回值的类型 实体种类
     * @param <ID> id的类型
     */
    public <R,ID>R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,
                                         Function<ID,R> dbFallback,Long time, TimeUnit timeUnit){
        // 1.尝试从redis中查询商铺缓存
        String key=keyPrefix+id;
        ValueOperations operations = redisTemplate.opsForValue();
        String shopJson = (String) operations.get(key);

        // 2.判断商铺缓存是否命中
        if (StrUtil.isBlank(shopJson)) {
            // 未命中缓存 直接返回  这里只考虑热点数据，其实根本不可能出现不命中缓存
            return null;
        }

        // 3.命中缓存 需要判断过期时间

        // json反序列化对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 同时内部的data由于在里层，反序列化一层出来也还是JSON
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();


        // 4.1未过期，直接返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        // 4.2 已过期，需要缓存重建

        // 5 尝试获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        boolean lock = tryGetLock(lockKey);
        if (!lock){
            // 5.1未获取到互斥锁 直接返回旧的缓存店铺信息
            return r;
        }

        // 5.2获取到互斥锁 开启独立线程 查询数据库并更新redis 进行缓存重建，并重新设置过期时间
        // 任务是lambda表达式形式写
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                // 重建缓存 先查数据库   再写redis
                R r1 = dbFallback.apply(id);
                this.setWithLogicalExpire(key,r1,time,timeUnit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁 一定得执行，所以写在finally中
                unLock(lockKey);
            }

        });
        // 新开线程去更新，这个还是返回旧数据
        return r;
    }



    /**
     * 尝试获取锁
     * @param key 锁的key
     * @return 是否获取成功
     */
    private boolean tryGetLock(String key){
        // 10秒足够业务执行
        return redisTemplate.opsForValue().setIfAbsent(key, "1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);
    }

    private void unLock(String key){
        redisTemplate.delete(key);
    }

}
