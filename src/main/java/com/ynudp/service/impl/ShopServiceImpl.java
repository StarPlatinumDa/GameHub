package com.ynudp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ynudp.dto.Result;
import com.ynudp.entity.Shop;
import com.ynudp.mapper.ShopMapper;
import com.ynudp.service.ShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ynudp.utils.CacheClient;
import com.ynudp.utils.RedisConstants;
import com.ynudp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CacheClient cacheClient;

    // 不自己写线程，性能不好，用线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);



    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    public Result queryShopById(Long id) {
        // 缓存穿透解决，返回空对象
//        Shop shop=queryShopIdThrough(id);
//        Shop shop = queryShopIdMutexLock(id);

        // 互斥锁解决缓存击穿
//        Shop shop = queryShopIdLogicalExpire(id);

        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.SHOP_KEY,id,Shop.class,
                tempId->getById(tempId),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿 缓存击穿默认是热点信息，在redis中已有(人工加入)，所以不适合用来处理普通信息
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.SHOP_KEY,id,Shop.class,
//                tempId->getById(tempId),20L, TimeUnit.SECONDS);

        if (shop==null)throw new RuntimeException("店铺不存在");


        return Result.success(shop);
    }


    // 缓存穿透代码
    public Shop queryShopIdThrough(long id){
        // 1.尝试从redis中查询商铺缓存
        String key=RedisConstants.SHOP_KEY+id;
        ValueOperations operations = redisTemplate.opsForValue();
        Shop shop = (Shop) operations.get(key);

        // 2.判断商铺缓存是否命中
        if (shop != null) {
            // 命中空对象
            if (shop.getId()==null){
                return null;
            }
            // 命中真实数据
            else return shop;
        }
        // 3.1.未命中，查询数据库
        shop = getById(id);
        if (shop == null) {
            // 3.2.数据库不存在，返回错误404
            // 该成保存空对象，设置过期时间，解决缓存穿透  过期时间比较短2minute
            operations.set(key,new Shop(),RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 3.3.数据库存在，写入redis
        operations.set(key,shop,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    // 互斥锁解决缓存击穿
    public Shop queryShopIdMutexLock(long id){
        // 1.尝试从redis中查询商铺缓存
        String key=RedisConstants.SHOP_KEY+id;
        ValueOperations operations = redisTemplate.opsForValue();
        Shop shop = (Shop) operations.get(key);

        // 2.判断商铺缓存是否命中
        // 命中
        if (shop != null) {
            // 命中空对象
            if (shop.getId()==null){
                return null;
            }
            // 命中真实数据
            else return shop;
        }
        // 3.1.未命中，查询数据库

        // 实现缓存重建
        // (1)新逻辑未命中要获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        // 这个try其实是为了里面的sleep，但其实获取锁的过程也需要，所以一块包围了
        try {
            boolean lock = tryGetLock(lockKey);
            // (2)是否成功获取锁
            if(!lock){
                // (3)失败，则休眠并重试整个代码块
                Thread.sleep(50);//50毫秒
                // 重试就用递归模拟了
                return queryShopIdMutexLock(id);
            }

            // (4)成功，查询数据库并根据情况写入redis

            shop = getById(id);
//            // 模拟超时
//            Thread.sleep(20000);

            if (shop == null) {
                // 3.2.数据库不存在，返回错误404
                // 该成保存空对象，设置过期时间，解决缓存穿透  过期时间比较短2minute
                operations.set(key,new Shop(),RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            // 3.3.数据库存在，写入redis
            operations.set(key,shop,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // (5)释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }


    // 逻辑过期解决缓存击穿
    public Shop queryShopIdLogicalExpire(long id){
        // 1.尝试从redis中查询商铺缓存
        String key=RedisConstants.SHOP_KEY+id;
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
        Shop shop = JSONUtil.toBean(data, Shop.class);

        LocalDateTime expireTime = redisData.getExpireTime();


        // 4.1未过期，直接返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        // 4.2 已过期，需要缓存重建

        // 5 尝试获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        boolean lock = tryGetLock(lockKey);
        if (!lock){
            // 5.1未获取到互斥锁 直接返回旧的缓存店铺信息
            return shop;
        }

        // 5.2获取到互斥锁 开启独立线程 查询数据库并更新redis 进行缓存重建，并重新设置过期时间
        // 任务是lambda表达式形式写
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                // 重建缓存
                saveShop2Redis(id,20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁 一定得执行，所以写在finally中
                unLock(lockKey);
            }

        });

        return shop;
    }






    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @Transactional
    public void updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null)throw new RuntimeException("shop id不能为空");

        // 先更新数据库
        updateById(shop);

        // 再删除缓存
        String key=RedisConstants.SHOP_KEY+shop.getId();
        redisTemplate.delete(key);
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

    // 人为redis热点数据预热
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        // 模拟延迟  会在第一次查询热点数据发现过期，但直接返回旧数据，后面更新缓存后就是新的，也不用查询数据库了
//        Thread.sleep(2000);

        // 查询店铺数据
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 写入redis  只有逻辑过期时间，实际永久有效
        redisTemplate.opsForValue().set(RedisConstants.SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

}
