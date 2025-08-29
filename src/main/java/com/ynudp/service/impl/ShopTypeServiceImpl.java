package com.ynudp.service.impl;

import com.ynudp.dto.Result;
import com.ynudp.entity.ShopType;
import com.ynudp.mapper.ShopTypeMapper;
import com.ynudp.service.ShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ynudp.utils.CacheClient;
import com.ynudp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CacheClient cacheClient;

    /**
     * 查询并缓存所有商铺类型
     * @return
     */
    public Result queryTypeList() {

        String key= RedisConstants.SHOP_TYPE_KEY;

        ListOperations listOperations = redisTemplate.opsForList();
        // 全部取出
        List shopTypeList = listOperations.range(key, 0, -1);
        // 缓存命中直接返回
        if (shopTypeList != null && shopTypeList.size() > 0) {
            log.info("商铺类型列表缓存命中");
            return Result.success(shopTypeList);
        }
        // 未命中查询数据库
        shopTypeList = query().orderByAsc("sort").list();

        listOperations.leftPushAll(key,shopTypeList);


        return Result.success(shopTypeList);
    }
}
