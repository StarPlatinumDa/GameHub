package com.ynudp.controller;


import com.ynudp.dto.Result;
import com.ynudp.entity.ShopType;
import com.ynudp.service.ShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private ShopTypeService shopTypeService;

    @GetMapping("list")
    public Result queryTypeList() {
        log.info("查询并缓存所有商铺类型");
        Result result=shopTypeService.queryTypeList();
        return result;
    }
}
