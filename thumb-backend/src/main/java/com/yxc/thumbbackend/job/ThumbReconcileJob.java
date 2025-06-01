package com.yxc.thumbbackend.job;

import cn.hutool.json.JSONUtil;
import com.google.common.collect.Sets;
import com.yxc.thumbbackend.constant.ThumbConstant;
import com.yxc.thumbbackend.listener.thumb.msg.ThumbEvent;
import com.yxc.thumbbackend.model.dto.ThumbCacheData;
import com.yxc.thumbbackend.model.entity.Thumb;
import com.yxc.thumbbackend.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service  
@Slf4j  
public class ThumbReconcileJob {  
    @Resource  
    private RedisTemplate<String, Object> redisTemplate;  
  
    @Resource  
    private ThumbService thumbService;  
  
    @Resource  
    private PulsarTemplate<ThumbEvent> pulsarTemplate;  
  
    /**  
     * 定时任务入口（每天凌晨2点执行）  
     */  
    @Scheduled(cron = "0 0 2 * * ?")  
    public void run() {  
        long startTime = System.currentTimeMillis();  
        
        // 计算今天凌晨1点的时间戳，只处理1点之前的数据
        LocalDateTime todayOneAM = LocalDateTime.now().withHour(1).withMinute(0).withSecond(0).withNano(0);
        long oneAMTimestamp = todayOneAM.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        log.info("开始对账任务，只处理{}之前的数据", todayOneAM);
  
        // 1. 获取该分片下的所有用户ID  
        Set<Long> userIds = new HashSet<>();  
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";  
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {  
            while (cursor.hasNext()) {  
                String key = cursor.next();  
                Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));  
                userIds.add(userId);  
            }  
        }  
  
        // 2. 逐用户比对  
        userIds.forEach(userId -> {  
            Set<Long> redisBlogIds = getValidRedisBlogIds(userId, oneAMTimestamp);
            Set<Long> mysqlBlogIds = Optional.ofNullable(thumbService.lambdaQuery()  
                            .eq(Thumb::getUserid, userId)  
                            .list()  
                    ).orElse(new ArrayList<>())  
                    .stream()  
                    .map(Thumb::getBlogid)  
                    .collect(Collectors.toSet());  
  
            // 3. 计算差异（Redis有但MySQL无）  
            Set<Long> diffBlogIds = Sets.difference(redisBlogIds, mysqlBlogIds);  
  
            // 4. 发送补偿事件  
            if (!diffBlogIds.isEmpty()) {
                log.info("发现用户{}的数据不一致，Redis有{}条，MySQL有{}条，需要补偿{}条",
                        userId, redisBlogIds.size(), mysqlBlogIds.size(), diffBlogIds.size());
                sendCompensationEvents(userId, diffBlogIds);  
            }
        });  
  
        log.info("对账任务完成，耗时 {}ms", System.currentTimeMillis() - startTime);  
    }  
    
    /**
     * 获取Redis中有效的博客ID（只包含1点之前创建的数据）
     */
    private Set<Long> getValidRedisBlogIds(Long userId, long timeThreshold) {
        Set<Long> validBlogIds = new HashSet<>();
        Set<Object> allBlogIds = redisTemplate.opsForHash().keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId);
        
        for (Object blogIdObj : allBlogIds) {
            try {
                Long blogId = Long.valueOf(blogIdObj.toString());
                Object cacheValueObj = redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
                
                if (cacheValueObj != null) {
                    // 解析缓存数据
                    ThumbCacheData cacheData = JSONUtil.toBean(cacheValueObj.toString(), ThumbCacheData.class);
                    
                    // 检查创建时间是否在1点之前
                    if (cacheData.getCreateTime() != null && cacheData.getCreateTime() <= timeThreshold) {
                        // 检查是否已过期
                        if (!cacheData.isExpired()) {
                            validBlogIds.add(blogId);
                        } else {
                            log.debug("跳过已过期的缓存数据: userId={}, blogId={}", userId, blogId);
                        }
                    } else {
                        log.debug("跳过1点之后创建的数据: userId={}, blogId={}, createTime={}", 
                                userId, blogId, cacheData.getCreateTime());
                    }
                }
            } catch (Exception e) {
                log.error("解析缓存数据失败: userId={}, blogId={}", userId, blogIdObj, e);
            }
        }
        
        return validBlogIds;
    }
  
    /**  
     * 发送补偿事件到Pulsar  
     */  
    private void sendCompensationEvents(Long userId, Set<Long> blogIds) {  
        blogIds.forEach(blogId -> {  
            ThumbEvent thumbEvent = new ThumbEvent(userId, blogId, ThumbEvent.EventType.INCR, LocalDateTime.now());  
            pulsarTemplate.sendAsync("thumb-topic", thumbEvent)  
                    .exceptionally(ex -> {  
                        log.error("补偿事件发送失败: userId={}, blogId={}", userId, blogId, ex);  
                        return null;  
                    });  
        });  
    }  
}
