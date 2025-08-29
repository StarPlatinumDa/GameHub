package com.ynudp.service;

import com.ynudp.dto.Result;
import com.ynudp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;


public interface ShopTypeService extends IService<ShopType> {

    Result queryTypeList();

}
