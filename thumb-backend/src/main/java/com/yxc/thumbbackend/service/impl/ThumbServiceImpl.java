package com.yxc.thumbbackend.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.hash.BloomFilter;
import com.yxc.thumbbackend.constant.RedisLuaScriptConstant;
import com.yxc.thumbbackend.constant.ThumbConstant;
import com.yxc.thumbbackend.exception.BusinessException;
import com.yxc.thumbbackend.exception.ErrorCode;
import com.yxc.thumbbackend.listener.thumb.msg.ThumbEvent;
import com.yxc.thumbbackend.manager.cache.AddResult;
import com.yxc.thumbbackend.manager.cache.CacheManager;
import com.yxc.thumbbackend.manager.cache.TopK;
import com.yxc.thumbbackend.mapper.ThumbMapper;
import com.yxc.thumbbackend.model.dto.DoThumbRequest;
import com.yxc.thumbbackend.model.dto.ThumbCacheData;
import com.yxc.thumbbackend.model.entity.Blog;
import com.yxc.thumbbackend.model.entity.Thumb;
import com.yxc.thumbbackend.model.entity.User;
import com.yxc.thumbbackend.model.enums.LuaStatusEnum;
import com.yxc.thumbbackend.service.BlogService;
import com.yxc.thumbbackend.service.ThumbService;
import com.yxc.thumbbackend.service.UserService;
import com.yxc.thumbbackend.utils.DistributedLockUtil;
import com.yxc.thumbbackend.utils.RedisKeyUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    @Resource
    private CacheManager cacheManager;

    @Resource
    private TopK hotKeyDetector;

    @Resource
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

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
        return doThumbWithMQ(doThumbRequest, request);
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        return undoThumbWithMQ(doThumbRequest, request);
    }

    /**
     * 定时任务版本的点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    private Boolean doThumbWithScheduledTask(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1. 校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 2. 获取登录用户
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        // 3. 外部预检查（冷热数据分离优化）
        Boolean exists = this.hasThumb(blogId, loginUser.getId());
        if (exists) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请勿重复点赞");
        }

        // 4. 构建脚本需要的参数
        String timeSlice = getTimeSlice();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 根据冷热数据策略选择缓存过期时间
        Boolean isHot = isHotData(blogId);
        long expireTime;
        if (isHot) {
            expireTime = System.currentTimeMillis() + ThumbConstant.HOT_DATA_CACHE_EXPIRE_TIME;
            log.info("点赞热数据，使用长缓存时间: blogId={}", blogId);
        } else {
            expireTime = System.currentTimeMillis() + ThumbConstant.COLD_DATA_CACHE_EXPIRE_TIME;
            log.info("点赞冷数据，使用短缓存时间: blogId={}", blogId);
        }

        // 存入用户点赞状态缓存（使用JSON格式存储过期时间和创建时间，thumbId用临时ID）
        long thumbCreateTime = System.currentTimeMillis();
        String thumbCacheData = JSONUtil.toJsonStr(new ThumbCacheData("temp_" + thumbCreateTime, expireTime, thumbCreateTime));

        // 5. 执行Lua脚本
        Long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.SCHEDULED_THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId().toString(),
                blogId.toString(),
                thumbCacheData
        );

        // 6. 处理执行结果
        if (result != null && result.equals(LuaStatusEnum.FAIL.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞失败，已点赞");
        }

        boolean success = result != null && result.equals(LuaStatusEnum.SUCCESS.getValue());

        // 7. 如果Lua脚本执行成功，更新缓存
        if (success) {
            // 清理可能存在的删除标记（用户重新点赞后应该清除之前的取消点赞标记）
            String deletedKey = ThumbConstant.USER_THUMB_DELETED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().delete(deletedKey, blogId.toString());

            // 修改本地缓存中的值
            cacheManager.putIfPresent(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString(), ThumbConstant.THUMB_CONSTANT);

            // 添加到布隆过滤器
            String bloomKey = generateBloomKey(loginUser.getId(), blogId);
            thumbBloomFilter.put(bloomKey);

            // 设置临时的"已新增"标记，防止定时任务延迟导致数据库查询不到记录(冷热数据分离,冷数据直接从数据库中查找,如果本地缓存没有)
            // 计算当前数据所在时间片何时会被同步完成
            long currentTime = System.currentTimeMillis() / 1000; // 转为秒
            long currentTimeSlice = (currentTime / 10) * 10; // 当前10秒时间片
            long nextSyncTime = currentTimeSlice + 20; // 延迟2个时间片同步
            long syncExpireTime = nextSyncTime + 5; // 同步完成后再保留5秒缓冲

            String addedKey = ThumbConstant.USER_THUMB_ADDED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().put(
                    addedKey,
                    blogId.toString(),
                    "added_" + System.currentTimeMillis()
            );
            // 设置精确的过期时间
            long ttlSeconds = syncExpireTime - (System.currentTimeMillis() / 1000);
            stringRedisTemplate.expire(addedKey, Duration.ofSeconds(Math.max(ttlSeconds, 25)));

            log.info("点赞成功，已更新缓存并设置新增标记到{}秒: userId={}, blogId={}", 
                    syncExpireTime, loginUser.getId(), blogId);
        }

        return success;
    }

    /**
     * 定时任务版本的取消点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    private Boolean undoThumbWithScheduledTask(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1. 校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 2. 获取登录用户
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        // 3. 外部预检查（冷热数据分离优化）
        Boolean exists = this.hasThumb(blogId, loginUser.getId());
        if (!exists) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无点赞记录");
        }

        // 4. 构建Redis键
        String timeSlice = getTimeSlice();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 5. 执行Lua脚本
        Long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.SCHEDULED_UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId().toString(),
                blogId.toString()
        );

        // 6. 处理执行结果
        if (result != null && result.equals(LuaStatusEnum.FAIL.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败，未点赞");
        }

        boolean success = result != null && result.equals(LuaStatusEnum.SUCCESS.getValue());

        // 7. 如果Lua脚本执行成功，清理缓存
        if (success) {
            // 清理可能存在的新增标记（用户取消点赞后应该清除之前的点赞新增标记）
            String addedKey = ThumbConstant.USER_THUMB_ADDED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().delete(addedKey, blogId.toString());

            // 修改本地缓存中的值
            String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();

            cacheManager.putIfPresent(hashKey, blogId.toString(), ThumbConstant.UN_THUMB_CONSTANT);

            // 设置临时的"已删除"标记，防止在定时器同步前重复取消点赞
            // 计算当前数据所在时间片何时会被同步完成
            long currentTime = System.currentTimeMillis() / 1000; // 转为秒
            long currentTimeSlice = (currentTime / 10) * 10; // 当前10秒时间片
            long nextSyncTime = currentTimeSlice + 20; // 延迟2个时间片同步
            long expireTime = nextSyncTime + 5; // 同步完成后再保留5秒缓冲

            String deletedKey = ThumbConstant.USER_THUMB_DELETED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().put(
                    deletedKey,
                    blogId.toString(),
                    "deleted_" + System.currentTimeMillis()
            );
            // 设置精确的过期时间
            long ttlSeconds = expireTime - (System.currentTimeMillis() / 1000);
            stringRedisTemplate.expire(deletedKey, Duration.ofSeconds(Math.max(ttlSeconds, 25)));

            log.info("取消点赞成功，清理新增标记并设置删除标记到{}秒: userId={}, blogId={}",
                    expireTime, loginUser.getId(), blogId);
        }

        return success;
    }

    /**
     * 消息队列版本的点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    private Boolean doThumbWithMQ(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1. 校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 2. 获取登录用户
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        // 3. 外部预检查（冷热数据分离优化）
        Boolean exists = this.hasThumb(blogId, loginUser.getId());
        if (exists) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请勿重复点赞");
        }

        // 4. 构建脚本需要的参数
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 根据冷热数据策略选择缓存过期时间
        Boolean isHot = isHotData(blogId);
        long expireTime;
        if (isHot) {
            expireTime = System.currentTimeMillis() + ThumbConstant.HOT_DATA_CACHE_EXPIRE_TIME;
            log.info("点赞热数据，使用长缓存时间: blogId={}", blogId);
        } else {
            expireTime = System.currentTimeMillis() + ThumbConstant.COLD_DATA_CACHE_EXPIRE_TIME;
            log.info("点赞冷数据，使用短缓存时间: blogId={}", blogId);
        }

        // 存入用户点赞状态缓存（使用JSON格式存储过期时间和创建时间，thumbId用临时ID）
        long thumbCreateTime = System.currentTimeMillis();
        String thumbCacheData = JSONUtil.toJsonStr(new ThumbCacheData("temp_" + thumbCreateTime, expireTime, thumbCreateTime));

        // 5. 执行Lua脚本
        Long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.MQ_THUMB_SCRIPT,
                Arrays.asList(userThumbKey),
                blogId.toString(),
                thumbCacheData
        );

        // 6. 处理执行结果
        if (result != null && result.equals(LuaStatusEnum.FAIL.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞失败，已点赞");
        }

        boolean success = result != null && result.equals(LuaStatusEnum.SUCCESS.getValue());

        // 7. 如果Lua脚本执行成功，更新缓存
        if (success) {
            // 发送消息给MQ(同步数据库)
            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .userId(loginUser.getId())
                    .blogId(blogId)
                    .type(ThumbEvent.EventType.INCR)
                    .eventTime(LocalDateTime.now())
                    .build();

            AtomicReference<Boolean> sendStatus = new AtomicReference<>(true);

            pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {
                sendStatus.set(false);

                //消息发送失败,数据库不能完成同步,必须删除redis中的值
                stringRedisTemplate.opsForHash().delete(userThumbKey, blogId.toString());

                log.error("点赞事件发送失败, userId={}, blogId={}", loginUser.getId(), blogId, ex);

                return null;
            });

            // 消息发送失败直接返回
            if (sendStatus.get() == false) {
                return false;
            }

            // 清理可能存在的删除标记（用户重新点赞后应该清除之前的取消点赞标记）
            String deletedKey = ThumbConstant.USER_THUMB_DELETED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().delete(deletedKey, blogId.toString());

            // 修改本地缓存中的值
            cacheManager.putIfPresent(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString(), ThumbConstant.THUMB_CONSTANT);

            // 添加到布隆过滤器
            String bloomKey = generateBloomKey(loginUser.getId(), blogId);
            thumbBloomFilter.put(bloomKey);

            // 设置临时的"已新增"标记，防止在消息队列消费前由于数据库查询不到记录导致重复点赞(冷热数据分离,冷数据直接从数据库中查找,如果本地缓存没有)
            // 计算当前数据所在时间片何时会被同步完成
            long currentTime = System.currentTimeMillis() / 1000; // 转为秒
            long currentTimeSlice = (currentTime / 10) * 10; // 当前10秒时间片
            long nextSyncTime = currentTimeSlice + 20; // 延迟2个时间片同步
            long syncExpireTime = nextSyncTime + 5; // 同步完成后再保留5秒缓冲

            String addedKey = ThumbConstant.USER_THUMB_ADDED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().put(
                    addedKey,
                    blogId.toString(),
                    "added_" + System.currentTimeMillis()
            );
            // 设置精确的过期时间
            long ttlSeconds = syncExpireTime - (System.currentTimeMillis() / 1000);
            stringRedisTemplate.expire(addedKey, Duration.ofSeconds(Math.max(ttlSeconds, 25)));

            log.info("点赞成功，已更新缓存并设置新增标记到{}秒: userId={}, blogId={}",
                    syncExpireTime, loginUser.getId(), blogId);
        }

        return success;
    }

    /**
     * 消息队列版本的取消点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    private Boolean undoThumbWithMQ(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1. 校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 2. 获取登录用户
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        // 3. 外部预检查（冷热数据分离优化）
        Boolean exists = this.hasThumb(blogId, loginUser.getId());
        if (!exists) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无点赞记录");
        }

        // 4. 构建Redis键
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 为后续MQ发送消息失败使用
        String thumbCacheData = (String) stringRedisTemplate.opsForHash().get(userThumbKey, blogId.toString());

        if (thumbCacheData == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无点赞记录");
        }

        // 5. 执行Lua脚本
        Long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.MQ_UNTHUMB_SCRIPT,
                Arrays.asList(userThumbKey),
                blogId.toString()
        );

        // 6. 处理执行结果
        if (result != null && result.equals(LuaStatusEnum.FAIL.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败，未点赞");
        }

        boolean success = result != null && result.equals(LuaStatusEnum.SUCCESS.getValue());

        // 7. 如果Lua脚本执行成功，清理缓存
        if (success) {
            // 发送消息给MQ(同步数据库)
            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .userId(loginUser.getId())
                    .blogId(blogId)
                    .type(ThumbEvent.EventType.DECR)
                    .eventTime(LocalDateTime.now())
                    .build();

            AtomicReference<Boolean> sendStatus = new AtomicReference<>(true);

            pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {
                sendStatus.set(false);
                //消息发送失败,数据库不能完成同步,必须新增redis中的值
                stringRedisTemplate.opsForHash().put(userThumbKey, blogId.toString(), thumbCacheData);

                log.error("取消点赞事件发送失败, userId={}, blogId={}", loginUser.getId(), blogId, ex);

                return null;
            });

            // 消息发送失败直接返回
            if (sendStatus.get() == false) {
                return false;
            }

            // 清理可能存在的新增标记（用户取消点赞后应该清除之前的点赞新增标记）
            String addedKey = ThumbConstant.USER_THUMB_ADDED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().delete(addedKey, blogId.toString());

            // 修改本地缓存中的值
            String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();

            cacheManager.putIfPresent(hashKey, blogId.toString(), ThumbConstant.UN_THUMB_CONSTANT);

            // 设置临时的"已删除"标记，防止在消息队列消费前重复取消点赞
            // 计算当前数据所在时间片何时会被同步完成
            long currentTime = System.currentTimeMillis() / 1000; // 转为秒
            long currentTimeSlice = (currentTime / 10) * 10; // 当前10秒时间片
            long nextSyncTime = currentTimeSlice + 20; // 延迟2个时间片同步
            long expireTime = nextSyncTime + 5; // 同步完成后再保留5秒缓冲

            String deletedKey = ThumbConstant.USER_THUMB_DELETED_KEY_PREFIX + loginUser.getId();
            stringRedisTemplate.opsForHash().put(
                    deletedKey,
                    blogId.toString(),
                    "deleted_" + System.currentTimeMillis()
            );
            // 设置精确的过期时间
            long ttlSeconds = expireTime - (System.currentTimeMillis() / 1000);
            stringRedisTemplate.expire(deletedKey, Duration.ofSeconds(Math.max(ttlSeconds, 25)));

            log.info("取消点赞成功，清理新增标记并设置删除标记到{}秒: userId={}, blogId={}",
                    expireTime, loginUser.getId(), blogId);
        }

        return success;
    }

    /**
     * 获取时间片
     */
    private String getTimeSlice() {
        DateTime nowDate = DateUtil.date();

        return DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;
    }

    @Deprecated
    /**
     * 旧版点赞方法（使用分布式锁+编程式事务）
     */
    public Boolean doThumbOld(DoThumbRequest doThumbRequest, HttpServletRequest request) {
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

                    // 存入Redis（使用JSON格式存储thumbId、过期时间和创建时间）
                    ThumbCacheData cacheData = new ThumbCacheData(thumb.getId().toString(), expireTime, System.currentTimeMillis());
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

    @Deprecated
    /**
     * 旧版取消点赞方法（使用分布式锁+编程式事务）
     */
    public Boolean undoThumbOld(DoThumbRequest doThumbRequest, HttpServletRequest request) {
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
        //----------------------通过是否有删除标记快速判断----------------------
        // 0.先检查是否有删除标记（表示点赞记录已删除但未来得及同步到数据库,防止重复取消点赞）
        String deletedKey = ThumbConstant.USER_THUMB_DELETED_KEY_PREFIX + userId;
        Object deletedFlag = stringRedisTemplate.opsForHash().get(deletedKey, blogId.toString());
        if (deletedFlag != null) {
            // 存在删除标记，说明已经取消点赞但还未同步到数据库
            return false;
        }

        //----------------------通过是否有新增标记快速判断----------------------
        // 1.检查是否有新增标记（表示点赞记录已新增但未来得及同步到数据库,防止定时任务延迟导致查询不到）
        String addedKey = ThumbConstant.USER_THUMB_ADDED_KEY_PREFIX + userId;
        Object addedFlag = stringRedisTemplate.opsForHash().get(addedKey, blogId.toString());
        if (addedFlag != null) {
            // 存在新增标记，说明已经点赞但还未同步到数据库
            return true;
        }

        //----------------------通过本地缓存判断是否点赞(解决热点key使redis压力过大和用户恶意多次请求redis)--------------
        String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        Object value = cacheManager.get(hashKey, blogId.toString());

        //value!=null的情况有两种,如果value==null,只能说明不是热点key,需要去缓存中查询
        if (value != null && !((Long) value).equals(ThumbConstant.UN_THUMB_CONSTANT)) {
            return true;
        } else if (value != null && ((Long) value).equals(ThumbConstant.UN_THUMB_CONSTANT)) {
            return false;
        }

        //----------------------通过redis判断是否点赞(解决redis缓存穿透和缓存击穿)--------------
        // 2.先通过布隆过滤器判断是否可能存在
        String bloomKey = generateBloomKey(userId, blogId);
        if (!thumbBloomFilter.mightContain(bloomKey)) {
            // 布隆过滤器说不存在，那就一定不存在
            return false;
        }
        // 3.判断是否为热数据，决定查询策略
        Boolean isHot = isHotData(blogId);

        Boolean result;

        if (isHot) {
            // 热数据：优先查Redis，未命中再查数据库
            result = hasThumbForHotData(blogId, userId, bloomKey);
        } else {
            // 冷数据：直接查数据库，减少Redis压力
            result = hasThumbForColdData(blogId, userId, bloomKey);
        }
        // 更新访问记录,如果是hotKey则添加到本地缓存中,这样下一次就不会再查询redis了
        if (value == null) {
            AddResult addResult = hotKeyDetector.add(blogId.toString(), 1);

            if (addResult.isHotKey()) {
                // 如果是热key,则添加到本地缓存中
                if (result == true) {
                    cacheManager.put(hashKey, blogId.toString(), ThumbConstant.THUMB_CONSTANT);
                } else {
                    cacheManager.put(hashKey, blogId.toString(), ThumbConstant.UN_THUMB_CONSTANT);
                }
            }
        }

        return result;
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
                ThumbCacheData cacheData = new ThumbCacheData(thumb.getId().toString(), expireTime, thumb.getCreatetime().getTime());
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
                    ThumbCacheData cacheData = new ThumbCacheData(thumb.getId().toString(), expireTime, thumb.getCreatetime().getTime());
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
                        Duration.ofDays(7)); // 缓存7天
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




