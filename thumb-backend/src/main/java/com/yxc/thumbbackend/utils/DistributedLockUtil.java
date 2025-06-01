package com.yxc.thumbbackend.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁工具类
 * 
 * @author fishstar
 */
@Slf4j
@Component
public class DistributedLockUtil {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 锁的前缀
     */
    private static final String LOCK_PREFIX = "thumb:lock:";

    /**
     * 默认锁等待时间（秒）
     */
    private static final long DEFAULT_WAIT_TIME = 10L;

    /**
     * 默认锁持有时间（秒）
     */
    private static final long DEFAULT_LEASE_TIME = 30L;

    /**
     * 执行带锁的操作（使用默认超时时间）
     *
     * @param lockKey 锁的key
     * @param supplier 需要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, supplier);
    }

    /**
     * 执行带锁的操作（自定义超时时间）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待锁的时间（秒）
     * @param leaseTime 锁的持有时间（秒）
     * @param supplier 需要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullLockKey);
        
        try {
            // 尝试获取锁
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取分布式锁失败，lockKey: {}", fullLockKey);
                throw new RuntimeException("获取锁失败，请稍后重试");
            }
            
            log.debug("成功获取分布式锁，lockKey: {}", fullLockKey);
            // 执行业务逻辑
            return supplier.get();
            
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，lockKey: {}", fullLockKey, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁，lockKey: {}", fullLockKey);
            }
        }
    }

    /**
     * 执行带锁的操作（无返回值）
     *
     * @param lockKey 锁的key
     * @param runnable 需要执行的操作
     */
    public void executeWithLock(String lockKey, Runnable runnable) {
        executeWithLock(lockKey, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 执行带锁的操作（无返回值，自定义超时时间）
     *
     * @param lockKey 锁的key
     * @param waitTime 等待锁的时间（秒）
     * @param leaseTime 锁的持有时间（秒）
     * @param runnable 需要执行的操作
     */
    public void executeWithLock(String lockKey, long waitTime, long leaseTime, Runnable runnable) {
        executeWithLock(lockKey, waitTime, leaseTime, () -> {
            runnable.run();
            return null;
        });
    }
} 