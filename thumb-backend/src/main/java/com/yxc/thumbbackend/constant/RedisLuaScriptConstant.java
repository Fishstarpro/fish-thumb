package com.yxc.thumbbackend.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;  
  
public class RedisLuaScriptConstant {

    /**
     * 点赞脚本,定时任务版本,添加临时点赞记录
     */
    public static final RedisScript<Long> SCHEDULED_THUMB_SCRIPT = new DefaultRedisScript<>("""  
            local tempThumbKey = KEYS[1]       -- 临时计数键（如 thumb:temp:{timeSlice}）  
            local userThumbKey = KEYS[2]       -- 用户点赞状态键（如 thumb:{userId}）  
            local userId = ARGV[1]             -- 用户 ID  
            local blogId = ARGV[2]             -- 博客 ID  
            local thumbCacheData = ARGV[3]      -- 点赞缓存数据
              
            -- 1. 获取旧值（不存在则默认为 0）  
            local hashKey = userId .. ':' .. blogId  
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)  
              
            -- 2. 计算新值  
            local newNumber = oldNumber + 1  
              
            -- 3. 判断是否已点赞
            local exists = redis.call('HEXISTS', userThumbKey, blogId)
            if exists == 1 then
                return -1  -- 已点赞，返回 -1 表示失败
            end
            
            -- 4. 添加临时点赞记录和用户点赞标记
            redis.call('HSET', tempThumbKey, hashKey, newNumber)  
              
            redis.call("HSET", userThumbKey, blogId, thumbCacheData)
            
            return 1  -- 返回 1 表示成功  
            """, Long.class);

    /**
     * 点赞脚本,MQ版,直接添加点赞记录
     */
    public static final RedisScript<Long> MQ_THUMB_SCRIPT = new DefaultRedisScript<>("""  
            local userThumbKey = KEYS[1]       -- 用户点赞状态键（如 thumb:{userId}）  
            local blogId = ARGV[1]             -- 博客 ID  
            local thumbCacheData = ARGV[2]      -- 点赞缓存数据

            -- 1. 判断用户是否已点赞
            if redis.call('HEXISTS', userThumbKey, blogId) == 1 then
                return -1  -- 已点赞，返回 -1 表示失败
            end
              
            -- 2. 添加点赞记录
            redis.call("HSET", userThumbKey, blogId, thumbCacheData)
            
            return 1  -- 返回 1 表示成功  
            """, Long.class);

    /**
     * 取消点赞脚本,定时任务版本,添加临时点赞记录
     */
    public static final RedisScript<Long> SCHEDULED_UNTHUMB_SCRIPT = new DefaultRedisScript<>("""  
            local tempThumbKey = KEYS[1]      -- 临时计数键（如 thumb:temp:{timeSlice}）  
            local userThumbKey = KEYS[2]      -- 用户点赞状态键（如 thumb:{userId}）  
            local userId = ARGV[1]            -- 用户 ID  
            local blogId = ARGV[2]            -- 博客 ID  
              
            -- 1. 获取当前临时计数（若不存在则默认为 0）  
            local hashKey = userId .. ':' .. blogId  
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)  
              
            -- 2. 计算新值  
            local newNumber = oldNumber - 1  
              
            -- 3. 原子性操作：先尝试删除用户点赞标记，失败则说明未点赞
            if redis.call('HEXISTS', userThumbKey, blogId) == 0 then
                return -1  -- 未点赞，返回 -1 表示失败
            end
            
            -- 4.添加临时点赞记录并删除用户点赞标记
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
              
            redis.call("HDEL", userThumbKey, blogId)
            
            return 1  -- 返回 1 表示成功  
            """, Long.class);

    /**
     * 取消点赞脚本,MQ版,直接删除点赞记录
     */
    public static final RedisScript<Long> MQ_UNTHUMB_SCRIPT = new DefaultRedisScript<>("""
            local userThumbKey = KEYS[1]      -- 用户点赞状态键（如 thumb:{userId}）
            local blogId = ARGV[1]            -- 博客 ID
            
            -- 1. 判断用户是否已点赞
            if redis.call('HEXISTS', userThumbKey, blogId) == 0 then
                return -1  -- 未点赞，返回 -1 表示失败
            end
            
            -- 2. 删除点赞记录
            redis.call("HDEL", userThumbKey, blogId)
            
            return 1  -- 返回 1 表示成功
            """, Long.class);
}
