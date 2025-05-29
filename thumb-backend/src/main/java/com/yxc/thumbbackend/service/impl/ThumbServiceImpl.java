package com.yxc.thumbbackend.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.hash.BloomFilter;
import com.yxc.thumbbackend.constant.ThumbConstant;
import com.yxc.thumbbackend.exception.BusinessException;
import com.yxc.thumbbackend.exception.ErrorCode;
import com.yxc.thumbbackend.model.dto.DoThumbRequest;
import com.yxc.thumbbackend.model.dto.ThumbCacheData;
import com.yxc.thumbbackend.model.entity.Blog;
import com.yxc.thumbbackend.model.entity.Thumb;
import com.yxc.thumbbackend.mapper.ThumbMapper;
import com.yxc.thumbbackend.model.entity.User;
import com.yxc.thumbbackend.service.BlogService;
import com.yxc.thumbbackend.service.ThumbService;
import com.yxc.thumbbackend.service.UserService;
import com.yxc.thumbbackend.utils.DistributedLockUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * @author fishstar
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-05-21 00:50:35
 */
@Service
@Slf4j
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private DistributedLockUtil distributedLockUtil;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private BloomFilter<String> thumbBloomFilter;

    /**
     * 点赞锁的key前缀
     */
    private static final String THUMB_LOCK_PREFIX = "thumb:";

    /**
     * 取消点赞锁的key前缀
     */
    private static final String UNDO_THUMB_LOCK_PREFIX = "undo_thumb:";

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        //1.校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        //2.获取登录用户
        User loginUser = userService.getLoginUser(request);
        //3.构造分布式锁的key：用户ID + 博客ID
        String lockKey = THUMB_LOCK_PREFIX + loginUser.getId() + ":" + doThumbRequest.getBlogId();
        //4.使用分布式锁防止重复点赞，使用编程式事务确保事务提交或回滚
        return distributedLockUtil.executeWithLock(lockKey, () -> {
            return transactionTemplate.execute(status -> {
                //5.查询是否点赞过（使用优化后的查询逻辑）
                Boolean exists = this.hasThumb(doThumbRequest.getBlogId(), loginUser.getId());

                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "请勿重复点赞");
                }
                //6.blog表中blog点赞数+1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, doThumbRequest.getBlogId())
                        .setSql("thumbCount = thumbCount + 1")
                        .update();
                //7.thumb表中插入一条点赞记录
                Thumb thumb = new Thumb();
                thumb.setBlogid(doThumbRequest.getBlogId());
                thumb.setUserid(loginUser.getId());
                boolean save = this.save(thumb);    
                //8.双写：将点赞记录存入redis中（带过期时间）和布隆过滤器
                if (update && save) {
                    // 添加到布隆过滤器
                    String bloomKey = generateBloomKey(loginUser.getId(), doThumbRequest.getBlogId());
                    thumbBloomFilter.put(bloomKey);
                    
                    // 根据冷热数据策略选择缓存过期时间
                    Boolean isHot = isHotData(doThumbRequest.getBlogId());
                    long expireTime;
                    if (isHot) {
                        expireTime = System.currentTimeMillis() + ThumbConstant.HOT_DATA_CACHE_EXPIRE_TIME;
                        log.info("点赞热数据，使用长缓存时间: blogId={}", doThumbRequest.getBlogId());
                    } else {
                        expireTime = System.currentTimeMillis() + ThumbConstant.COLD_DATA_CACHE_EXPIRE_TIME;
                        log.info("点赞冷数据，使用短缓存时间: blogId={}", doThumbRequest.getBlogId());
                    }
                    
                    // 存入Redis（使用JSON格式存储thumbId和过期时间）
                    ThumbCacheData cacheData = new ThumbCacheData(thumb.getId().toString(), expireTime);
                    stringRedisTemplate.opsForHash().put(
                            ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), 
                            doThumbRequest.getBlogId().toString(), 
                            JSONUtil.toJsonStr(cacheData)
                    );
                }

                return update && save;
            });
        });
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        //1.校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        //2.获取登录用户
        User loginUser = userService.getLoginUser(request);
        //3.构造分布式锁的key：用户ID + 博客ID
        String lockKey = UNDO_THUMB_LOCK_PREFIX + loginUser.getId() + ":" + doThumbRequest.getBlogId();
        //4.使用分布式锁防止重复取消点赞，使用编程式事务确保事务提交或回滚
        return distributedLockUtil.executeWithLock(lockKey, () -> {
            return transactionTemplate.execute(status -> {
                //5.查询是否点赞过（使用优化后的查询逻辑）
                Boolean exists = this.hasThumb(doThumbRequest.getBlogId(), loginUser.getId());

                if (!exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "无点赞记录");
                }

                //6.blog表中blog点赞数-1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, doThumbRequest.getBlogId())
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                //7.thumb表中删除一条点赞记录
                boolean remove = this.lambdaUpdate()
                        .eq(Thumb::getBlogid, doThumbRequest.getBlogId())
                        .eq(Thumb::getUserid, loginUser.getId())
                        .remove();
                //8.双写：将点赞记录从redis中删除（注意：布隆过滤器不支持删除操作）
                if (update && remove) {
                    stringRedisTemplate.opsForHash().delete(
                            ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), 
                            doThumbRequest.getBlogId().toString()
                    );
                }

                return update && remove;
            });
        });
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        // 1.先通过布隆过滤器判断是否可能存在
        String bloomKey = generateBloomKey(userId, blogId);
        if (!thumbBloomFilter.mightContain(bloomKey)) {
            // 布隆过滤器说不存在，那就一定不存在
            return false;
        }      
        // 2.判断是否为热数据，决定查询策略
        Boolean isHot = isHotData(blogId);
        
        if (isHot) {
            // 热数据：优先查Redis，未命中再查数据库
            return hasThumbForHotData(blogId, userId, bloomKey);
        } else {
            // 冷数据：直接查数据库，减少Redis压力
            return hasThumbForColdData(blogId, userId, bloomKey);
        }
    }
    
    /**
     * 热数据点赞查询逻辑
     */
    private Boolean hasThumbForHotData(Long blogId, Long userId, String bloomKey) {
        String redisKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        Object cacheValue = stringRedisTemplate.opsForHash().get(redisKey, blogId.toString());
        
        if (cacheValue != null) {
            // Redis中存在，检查是否过期
            try {
                ThumbCacheData cacheData = JSONUtil.toBean(cacheValue.toString(), ThumbCacheData.class);
                if (!cacheData.isExpired()) {
                    return true;
                } else {
                    // 已过期，异步删除Redis中的数据
                    stringRedisTemplate.opsForHash().delete(redisKey, blogId.toString());
                    log.info("删除过期的热数据点赞缓存: userId={}, blogId={}", userId, blogId);
                }
            } catch (Exception e) {
                log.error("解析热数据点赞缓存失败: userId={}, blogId={}", userId, blogId, e);
                // 解析失败，删除异常数据
                stringRedisTemplate.opsForHash().delete(redisKey, blogId.toString());
            }
        }
        
        // Redis中不存在或已过期，查询数据库
        boolean existsInDb = this.lambdaQuery()
                .eq(Thumb::getBlogid, blogId)
                .eq(Thumb::getUserid, userId)
                .exists();
                
        // 如果数据库中存在，回写到Redis（热数据使用较长的缓存时间）
        if (existsInDb) {
            Thumb thumb = this.lambdaQuery()
                    .eq(Thumb::getBlogid, blogId)
                    .eq(Thumb::getUserid, userId)
                    .one();
            if (thumb != null) {
                // 热数据使用较长的缓存时间
                long expireTime = System.currentTimeMillis() + ThumbConstant.HOT_DATA_CACHE_EXPIRE_TIME;
                ThumbCacheData cacheData = new ThumbCacheData(thumb.getId().toString(), expireTime);
                stringRedisTemplate.opsForHash().put(redisKey, blogId.toString(), JSONUtil.toJsonStr(cacheData));
                
                // 确保布隆过滤器中也有这条记录
                thumbBloomFilter.put(bloomKey);
                
                log.info("回写热数据点赞记录到缓存: userId={}, blogId={}", userId, blogId);
            }
        }
        
        return existsInDb;
    }
    
    /**
     * 冷数据点赞查询逻辑
     */
    private Boolean hasThumbForColdData(Long blogId, Long userId, String bloomKey) {
        // 冷数据策略：先查数据库，如果存在再考虑是否缓存
        boolean existsInDb = this.lambdaQuery()
                .eq(Thumb::getBlogid, blogId)
                .eq(Thumb::getUserid, userId)
                .exists();
        
        if (existsInDb) {
            // 冷数据存在时，可以选择性地缓存（使用较短的过期时间）
            String redisKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
            Object cacheValue = stringRedisTemplate.opsForHash().get(redisKey, blogId.toString());
            
            if (cacheValue == null) {
                // Redis中不存在，考虑是否要缓存
                Thumb thumb = this.lambdaQuery()
                        .eq(Thumb::getBlogid, blogId)
                        .eq(Thumb::getUserid, userId)
                        .one();
                if (thumb != null) {
                    // 冷数据使用较短的缓存时间，减少内存占用
                    long expireTime = System.currentTimeMillis() + ThumbConstant.COLD_DATA_CACHE_EXPIRE_TIME;
                    ThumbCacheData cacheData = new ThumbCacheData(thumb.getId().toString(), expireTime);
                    stringRedisTemplate.opsForHash().put(redisKey, blogId.toString(), JSONUtil.toJsonStr(cacheData));
                    
                    // 确保布隆过滤器中也有这条记录
                    thumbBloomFilter.put(bloomKey);
                    
                    log.info("缓存冷数据点赞记录: userId={}, blogId={}", userId, blogId);
                }
            }
        }
        
        return existsInDb;
    }
    
    @Override
    public Boolean isHotData(Long blogId) {
        // 1.先从Redis缓存中获取博客创建时间
        String cacheKey = ThumbConstant.BLOG_CREATE_TIME_KEY_PREFIX + blogId;
        String createTimeStr = stringRedisTemplate.opsForValue().get(cacheKey);
        
        long createTime;
        if (createTimeStr != null) {
            try {
                createTime = Long.parseLong(createTimeStr);
            } catch (NumberFormatException e) {
                log.error("解析博客创建时间缓存失败: blogId={}", blogId, e);
                // 解析失败，从数据库查询
                createTime = getBlogCreateTimeFromDb(blogId);
            }
        } else {
            // 缓存中不存在，从数据库查询
            createTime = getBlogCreateTimeFromDb(blogId);
            
            // 将创建时间缓存到Redis（设置较长的过期时间，因为创建时间不会变）
            if (createTime > 0) {
                stringRedisTemplate.opsForValue().set(cacheKey, String.valueOf(createTime), 
                        java.time.Duration.ofDays(7)); // 缓存7天
            }
        }
        
        if (createTime <= 0) {
            // 博客不存在，默认当作冷数据处理
            return false;
        }
        
        // 2.判断是否为热数据（发布时间在1个月内）
        long currentTime = System.currentTimeMillis();
        return (currentTime - createTime) <= ThumbConstant.HOT_DATA_TIME_THRESHOLD;
    }
    
    /**
     * 从数据库获取博客创建时间
     */
    private long getBlogCreateTimeFromDb(Long blogId) {
        try {
            // 这里需要注入BlogService，但为了避免循环依赖，直接查询
            // 或者可以通过ApplicationContext获取BlogService
            Blog blog = blogService.getById(blogId);
            if (blog != null && blog.getCreatetime() != null) {
                return blog.getCreatetime().getTime();
            }
        } catch (Exception e) {
            log.error("查询博客创建时间失败: blogId={}", blogId, e);
        }
        return 0;
    }
    
    @Override
    public void initBloomFilter() {
        log.info("开始初始化布隆过滤器...");
        
        // 分批查询所有点赞记录，避免内存溢出
        int pageSize = 1000;
        int currentPage = 0;
        
        while (true) {
            List<Thumb> thumbList = this.lambdaQuery()
                    .last("LIMIT " + (currentPage * pageSize) + ", " + pageSize)
                    .list();
                    
            if (thumbList.isEmpty()) {
                break;
            }
            
            // 将点赞记录添加到布隆过滤器
            for (Thumb thumb : thumbList) {
                String bloomKey = generateBloomKey(thumb.getUserid(), thumb.getBlogid());
                thumbBloomFilter.put(bloomKey);
            }
            
            log.info("已加载第{}批点赞记录到布隆过滤器，数量: {}", currentPage + 1, thumbList.size());
            currentPage++;
        }
        
        log.info("布隆过滤器初始化完成，共处理{}批数据", currentPage);
    }
    
    /**
     * 生成布隆过滤器的key
     */
    private String generateBloomKey(Long userId, Long blogId) {
        return userId + ThumbConstant.BLOOM_FILTER_KEY_SEPARATOR + blogId;
    }
}




