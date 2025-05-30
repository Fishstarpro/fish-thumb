package com.yxc.thumbbackend.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yxc.thumbbackend.constant.ThumbConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager {  
    private TopK hotKeyDetector;  
    private Cache<String, Object> localCache;

    // 初始化 TopK 检测器
    @Bean
    public TopK getHotKeyDetector() {  
        hotKeyDetector = new HeavyKeeper(  
                // 监控 Top 100 Key  
                100,  
                // 宽度  
                100000,  
                // 深度  
                5,  
                // 衰减系数  
                0.92,  
                // 最小出现 10 次才记录  
                10  
        );  
        return hotKeyDetector;  
    }  

    // 初始化本地缓存
    @Bean  
    public Cache<String, Object> localCache() {  
        return localCache = Caffeine.newBuilder()
                .maximumSize(1000)  
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();  
    }

    // 辅助方法:获取本地缓存的key
    public String getLocalCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }

    // 从本地缓存中判断是否点赞
    public Object get(String hashKey, String key) {
        String localCacheKey = getLocalCacheKey(hashKey, key);

        // 1. 从本地缓存中获取数据,若不存在,直接返回null,要去redis中找
        Object value = localCache.getIfPresent(localCacheKey);

        if (value == null) {
            return null;
        }

        // 2. 添加访问次数
        hotKeyDetector.add(key, 1);
        return value;
    }

    // 添加数据到本地缓存
    public void put(String hashKey, String key, Object value) {
        String localCacheKey = getLocalCacheKey(hashKey, key);

        localCache.put(localCacheKey, value);
    }

    // 修改本地缓存中的值(前提存在,不然就是是手动添加了一个热点key)
    public void putIfPresent(String hashKey, String key, Object value) {
        String localCacheKey = getLocalCacheKey(hashKey, key);

        Object cacheValue = localCache.getIfPresent(localCacheKey);

        if (cacheValue != null) {
            localCache.put(localCacheKey, value);
        }
    }
}
