package com.yxc.thumbbackend.constant;  
  
import org.springframework.data.redis.core.script.DefaultRedisScript;  
import org.springframework.data.redis.core.script.RedisScript;  
  
public class RedisLuaScriptConstant {  
  
    /**  
     * 点赞 Lua 脚本  
     * KEYS[1]       -- 临时计数键  
     * KEYS[2]       -- 用户点赞状态键  
     * ARGV[1]       -- 用户 ID  
     * ARGV[2]       -- 博客 ID  
     * 返回:  
     * -1: 已点赞  
     * 1: 操作成功  
     */  
    public static final RedisScript<Long> THUMB_SCRIPT = new DefaultRedisScript<>("""  
            local tempThumbKey = KEYS[1]       -- 临时计数键（如 thumb:temp:{timeSlice}）  
            local userThumbKey = KEYS[2]       -- 用户点赞状态键（如 thumb:{userId}）  
            local userId = ARGV[1]             -- 用户 ID  
            local blogId = ARGV[2]             -- 博客 ID  
              
            -- 1. 获取旧值（不存在则默认为 0）  
            local hashKey = userId .. ':' .. blogId  
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)  
              
            -- 2. 计算新值  
            local newNumber = oldNumber + 1  
              
            -- 3. 原子性更新：先尝试标记用户已点赞，失败则说明已点赞
            local thumbResult = redis.call('HSETNX', userThumbKey, blogId, 1)
            if thumbResult == 0 then
                return -1  -- 已点赞，返回 -1 表示失败
            end
            -- 成功标记后更新临时计数
            redis.call('HSET', tempThumbKey, hashKey, newNumber)  
              
            return 1  -- 返回 1 表示成功  
            """, Long.class);  
  
    /**  
     * 取消点赞 Lua 脚本  
     * 参数同上  
     * 返回：  
     * -1: 未点赞  
     * 1: 操作成功  
     */  
    public static final RedisScript<Long> UNTHUMB_SCRIPT = new DefaultRedisScript<>("""  
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
            local deleteResult = redis.call('HDEL', userThumbKey, blogId)
            if deleteResult == 0 then
                return -1  -- 未点赞，返回 -1 表示失败
            end
            -- 成功删除后更新临时计数
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
              
            return 1  -- 返回 1 表示成功  
            """, Long.class);  
}
