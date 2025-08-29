package com.ynudp.service;

import com.ynudp.dto.Result;
import com.ynudp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface VoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);

    void createVoucherOrderAsyn(VoucherOrder order);
}
