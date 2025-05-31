package com.yxc.thumbbackend.model.dto;

import lombok.Data;

/**
 * 点赞缓存数据DTO
 * 用于在Redis中存储点赞记录的ID和过期时间
 */
@Data
public class ThumbCacheData {
    
    /**
     * 点赞记录ID
     */
    private String thumbId;
    
    /**
     * 过期时间戳（毫秒）
     */
    private Long expireTime;
    
    /**
     * 点赞创建时间戳（毫秒）
     */
    private Long createTime;
    
    public ThumbCacheData() {}
    
    public ThumbCacheData(String thumbId, Long expireTime) {
        this.thumbId = thumbId;
        this.expireTime = expireTime;
        this.createTime = System.currentTimeMillis();
    }
    
    public ThumbCacheData(String thumbId, Long expireTime, Long createTime) {
        this.thumbId = thumbId;
        this.expireTime = expireTime;
        this.createTime = createTime;
    }
    
    /**
     * 判断是否已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }
} 