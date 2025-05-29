# 点赞功能优化方案

## 1. 优化背景

原有的点赞功能存在以下问题：
- Redis中的点赞数据没有过期时间，导致内存持续增长
- 对于大部分用户未点赞的博客，仍需要查询Redis，增加了不必要的开销
- 没有区分热点数据和冷数据，缓存策略不够精细

## 2. 优化方案

### 2.1 布隆过滤器
- 使用Google Guava的布隆过滤器，预期插入100万条记录，误判率0.01%
- 在查询点赞记录前，先通过布隆过滤器判断是否可能存在
- 如果布隆过滤器返回不存在，则直接返回false，避免无效的Redis和数据库查询

### 2.2 冷热分离策略
- **热数据定义**：博客发布时间在1个月内的认为是热数据
- **热数据策略**：优先查Redis缓存，缓存时间7天，未命中再查数据库并回写缓存
- **冷数据策略**：直接查数据库，如果存在则缓存1天，减少Redis内存占用

### 2.3 数据结构优化
- Redis中存储JSON格式的数据，包含thumbId和expireTime
- 支持在应用层面检查数据是否过期，过期数据自动删除

### 2.4 定时清理任务
- 每天凌晨2点执行缓存清理任务
- 扫描所有点赞缓存，删除过期和异常数据

## 3. 核心实现

### 3.1 ThumbCacheData
```java
public class ThumbCacheData {
    private String thumbId;      // 点赞记录ID
    private Long expireTime;     // 过期时间戳
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }
}
```

### 3.2 查询逻辑
```java
public Boolean hasThumb(Long blogId, Long userId) {
    // 1. 布隆过滤器预判
    if (!thumbBloomFilter.mightContain(bloomKey)) {
        return false;
    }
    
    // 2. 冷热分离策略
    if (isHotData(blogId)) {
        return hasThumbForHotData(blogId, userId, bloomKey);
    } else {
        return hasThumbForColdData(blogId, userId, bloomKey);
    }
}
```

### 3.3 冷热数据判断
```java
public Boolean isHotData(Long blogId) {
    // 获取博客创建时间（优先从Redis缓存获取）
    long createTime = getBlogCreateTime(blogId);
    long currentTime = System.currentTimeMillis();
    
    // 发布时间在1个月内认为是热数据
    return (currentTime - createTime) <= HOT_DATA_TIME_THRESHOLD;
}
```

## 4. 性能优化效果

### 4.1 减少无效查询
- 布隆过滤器可以过滤掉大部分不存在的查询
- 对于100万条记录，误判率仅0.01%，即99.99%的不存在查询可以被过滤

### 4.2 内存优化
- 冷数据使用1天过期时间，相比永不过期节省大量内存
- 热数据使用7天过期时间，保证高频访问的性能

### 4.3 数据库压力减轻
- 热数据优先走缓存，减少数据库查询
- 冷数据虽然查数据库，但访问频率低，总体压力可控

## 5. 配置参数

```java
// 热数据时间阈值 - 1个月
long HOT_DATA_TIME_THRESHOLD = 30L * 24 * 60 * 60 * 1000;

// 热数据缓存过期时间 - 7天
long HOT_DATA_CACHE_EXPIRE_TIME = 7L * 24 * 60 * 60 * 1000;

// 冷数据缓存过期时间 - 1天
long COLD_DATA_CACHE_EXPIRE_TIME = 1L * 24 * 60 * 60 * 1000;
```

## 6. 监控和维护

### 6.1 日志监控
- 记录热数据和冷数据的查询情况
- 监控布隆过滤器的命中率
- 记录缓存清理任务的执行情况

### 6.2 定时任务
- 每天凌晨2点执行缓存清理
- 应用启动时初始化布隆过滤器

## 7. 扩展性考虑

### 7.1 布隆过滤器扩容
- 当数据量超过预期时，可以考虑使用分布式布隆过滤器
- 或者定期重建布隆过滤器

### 7.2 缓存策略调整
- 可以根据实际业务情况调整热数据的时间阈值
- 可以根据内存使用情况调整缓存过期时间

### 7.3 多级缓存
- 可以考虑引入本地缓存（如Caffeine）作为一级缓存
- Redis作为二级缓存，进一步提升性能 