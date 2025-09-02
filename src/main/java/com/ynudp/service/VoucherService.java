package com.ynudp.service;

import com.ynudp.dto.Result;
import com.ynudp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;


public interface VoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
