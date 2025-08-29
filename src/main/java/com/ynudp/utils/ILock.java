package com.ynudp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁超时时间
     * @return true代表获取锁成功
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
