package com.yxc.thumbbackend.constant;

public interface ThumbConstant {
  
    String USER_THUMB_KEY_PREFIX = "thumb:";  
  
    /**
     * 点赞记录缓存过期时间（毫秒）- 30天
     */
    long THUMB_CACHE_EXPIRE_TIME = 30L * 24 * 60 * 60 * 1000;
    
    /**
     * 布隆过滤器key前缀
     */
    String BLOOM_FILTER_KEY_SEPARATOR = ":";
    
    /**
     * 热数据时间阈值（毫秒）- 1个月
     * 博客发布时间在1个月内的认为是热数据
     */
    long HOT_DATA_TIME_THRESHOLD = 30L * 24 * 60 * 60 * 1000;
    
    /**
     * 热数据缓存过期时间（毫秒）- 7天
     */
    long HOT_DATA_CACHE_EXPIRE_TIME = 7L * 24 * 60 * 60 * 1000;
    
    /**
     * 冷数据缓存过期时间（毫秒）- 1天
     */
    long COLD_DATA_CACHE_EXPIRE_TIME = 1L * 24 * 60 * 60 * 1000;
    
    /**
     * 博客创建时间缓存key前缀
     */
    String BLOG_CREATE_TIME_KEY_PREFIX = "blog:createTime:";
  
}
