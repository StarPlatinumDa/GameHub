package com.ynudp.service;

import com.ynudp.dto.Result;
import com.ynudp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;


public interface ShopService extends IService<Shop> {

    Result queryShopById(Long id);


    void updateShop(Shop shop);

    void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException;
}
