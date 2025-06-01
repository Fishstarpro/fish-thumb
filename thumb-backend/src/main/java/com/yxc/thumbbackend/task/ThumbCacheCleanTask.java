package com.yxc.thumbbackend.task;

import cn.hutool.json.JSONUtil;
import com.yxc.thumbbackend.constant.ThumbConstant;
import com.yxc.thumbbackend.model.dto.ThumbCacheData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 点赞缓存清理定时任务
 * 定期清理Redis中过期的点赞缓存数据
 */
@Component
@Slf4j
public class ThumbCacheCleanTask {

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每天凌晨2点执行缓存清理任务
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredThumbCache() {
        log.info("开始执行点赞缓存清理任务...");
        
        long startTime = System.currentTimeMillis();
        int cleanedCount = 0;
        int totalChecked = 0;
        
        try {
            //1.扫描所有以thumb:开头的key
            Set<String> keys = stringRedisTemplate.keys(ThumbConstant.USER_THUMB_KEY_PREFIX + "*");
            
            if (keys == null || keys.isEmpty()) {
                log.info("没有找到需要清理的点赞缓存");
                return;
            }
            
            //2.遍历所有key，检查并清理过期数据
            for (String key : keys) {
                //获取hash中的所有field和value
                Map<Object, Object> thumbMap = stringRedisTemplate.opsForHash().entries(key);
                
                for (Map.Entry<Object, Object> entry : thumbMap.entrySet()) {
                    totalChecked++;
                    String blogId = entry.getKey().toString();
                    String cacheValue = entry.getValue().toString();
                    
                    try {
                        ThumbCacheData cacheData = JSONUtil.toBean(cacheValue, ThumbCacheData.class);
                        
                        //检查是否过期
                        if (cacheData.isExpired()) {
                            stringRedisTemplate.opsForHash().delete(key, blogId);
                            cleanedCount++;
                            
                            if (cleanedCount % 100 == 0) {
                                log.info("已清理{}条过期缓存", cleanedCount);
                            }
                        }
                    } catch (Exception e) {
                        //解析失败的数据也删除
                        stringRedisTemplate.opsForHash().delete(key, blogId);
                        cleanedCount++;
                        log.warn("删除无法解析的缓存数据: key={}, blogId={}", key, blogId);
                    }
                }
            }
            
            //3.记录清理结果
            long endTime = System.currentTimeMillis();
            log.info("点赞缓存清理任务完成，耗时: {}ms, 检查: {}条, 清理: {}条", 
                    endTime - startTime, totalChecked, cleanedCount);
                    
        } catch (Exception e) {
            log.error("执行点赞缓存清理任务失败", e);
        }
    }
} 