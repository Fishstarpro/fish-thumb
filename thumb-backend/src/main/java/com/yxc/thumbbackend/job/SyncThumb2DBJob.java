package com.yxc.thumbbackend.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxc.thumbbackend.constant.ThumbConstant;
import com.yxc.thumbbackend.mapper.BlogMapper;
import com.yxc.thumbbackend.model.entity.Thumb;
import com.yxc.thumbbackend.model.enums.ThumbTypeEnum;
import com.yxc.thumbbackend.service.ThumbService;
import com.yxc.thumbbackend.utils.RedisKeyUtil;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**  
 * 定时将 Redis 中的临时点赞数据同步到数据库  
 *  
 */  
@Component  
@Slf4j  
public class SyncThumb2DBJob {  
  
    @Resource  
    private ThumbService thumbService;  
  
    @Resource  
    private BlogMapper blogMapper;  
  
    @Resource  
    private RedisTemplate<String, Object> redisTemplate;  
  
    @Resource  
    private StringRedisTemplate stringRedisTemplate;  
  
    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {  
        log.info("开始执行");  
        DateTime nowDate = DateUtil.date();
        // 获取当前秒数并计算要同步的时间片
        int currentSecond = DateUtil.second(nowDate);
        int timeSlice = (currentSecond / 10) * 10; // 当前时间片
        
        // 同步上一个时间片的数据（延迟一个时间片同步，确保数据写入完成）
        int syncTimeSlice = timeSlice - 10;
        DateTime syncDate = nowDate;
        
        if (syncTimeSlice < 0) {
            // 如果计算结果小于0，说明要同步上一分钟的最后一个时间片
            syncTimeSlice = 50;
            syncDate = DateUtil.offsetMinute(nowDate, -1);
        }
        
        String date = DateUtil.format(syncDate, "HH:mm:") + syncTimeSlice;
        log.info("同步时间片: {} (当前时间: {})", date, DateUtil.format(nowDate, "HH:mm:ss"));
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");
    }  
  
    public void syncThumb2DBByDate(String date) {  
        // 获取到临时点赞和取消点赞数据  
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);  
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);  
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);  
  
        // 同步 点赞 到数据库  
        // 构建插入列表并收集blogId  
        Map<Long, Long> blogThumbCountMap = new HashMap<>();  
        if (thumbMapEmpty) {  
            return;  
        }  
        ArrayList<Thumb> thumbList = new ArrayList<>();  
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();  
        boolean needRemove = false;  
        for (Object userIdBlogIdObj : allTempThumbMap.keySet()) {  
            String userIdBlogId = (String) userIdBlogIdObj;  
            String[] userIdAndBlogId = userIdBlogId.split(StrPool.COLON);  
            Long userId = Long.valueOf(userIdAndBlogId[0]);  
            Long blogId = Long.valueOf(userIdAndBlogId[1]);  
            // -1 取消点赞，1 点赞  
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdBlogId).toString());  
            if (thumbType == ThumbTypeEnum.INCR.getValue()) {  
                Thumb thumb = new Thumb();  
                thumb.setUserid(userId);
                thumb.setBlogid(blogId);
                thumbList.add(thumb);  
            } else if (thumbType == ThumbTypeEnum.DECR.getValue()) {  
                // 拼接查询条件，批量删除  
                needRemove = true;  
                wrapper.or().eq(Thumb::getUserid, userId).eq(Thumb::getBlogid, blogId);  
            } else {  
                if (thumbType != ThumbTypeEnum.NON.getValue()) {  
                    log.warn("数据异常：{}", userId + "," + blogId + "," + thumbType);  
                }  
                continue;  
            }  
            // 计算点赞增量  
            blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);  
        }  
        // 批量插入  
        thumbService.saveBatch(thumbList);  
        // 批量删除  
        if (needRemove) {  
            thumbService.remove(wrapper);  
        }  
        // 批量更新博客点赞量  
        if (!blogThumbCountMap.isEmpty()) {  
            blogMapper.batchUpdateThumbCount(blogThumbCountMap);  
        }  
        
        // 清理删除标记（针对本时间片的取消点赞操作）
        cleanupDeletedFlags(allTempThumbMap);
        
        // 异步删除  
        Thread.startVirtualThread(() -> {  
            redisTemplate.delete(tempThumbKey);  
        });  
    }  
    
    /**
     * 清理删除标记
     */
    private void cleanupDeletedFlags(Map<Object, Object> tempThumbMap) {
        try {
            for (Object userIdBlogIdObj : tempThumbMap.keySet()) {
                String userIdBlogId = (String) userIdBlogIdObj;
                String[] userIdAndBlogId = userIdBlogId.split(StrPool.COLON);
                Long userId = Long.valueOf(userIdAndBlogId[0]);
                Long blogId = Long.valueOf(userIdAndBlogId[1]);
                Integer thumbType = Integer.valueOf(tempThumbMap.get(userIdBlogId).toString());
                
                // 如果是取消点赞操作，清理对应的删除标记
                if (thumbType == ThumbTypeEnum.DECR.getValue()) {
                    String deletedKey = ThumbConstant.USER_THUMB_DELETED_KEY_PREFIX + userId;
                    stringRedisTemplate.opsForHash().delete(deletedKey, blogId.toString());
                    log.debug("清理删除标记: userId={}, blogId={}", userId, blogId);
                }
            }
        } catch (Exception e) {
            log.error("清理删除标记失败", e);
        }
    }
}
    