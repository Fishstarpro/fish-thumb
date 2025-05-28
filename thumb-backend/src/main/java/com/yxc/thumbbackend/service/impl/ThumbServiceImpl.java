package com.yxc.thumbbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxc.thumbbackend.exception.BusinessException;
import com.yxc.thumbbackend.exception.ErrorCode;
import com.yxc.thumbbackend.model.dto.DoThumbRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author fishstar
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-05-21 00:50:35
 */
@Service
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
        // 1.校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 2.获取登录用户
        User loginUser = userService.getLoginUser(request);
        
        // 3.构造分布式锁的key：用户ID + 博客ID
        String lockKey = THUMB_LOCK_PREFIX + loginUser.getId() + ":" + doThumbRequest.getBlogId();

        // 4.使用分布式锁防止重复点赞，使用编程式事务确保事务提交或回滚
        return distributedLockUtil.executeWithLock(lockKey, () -> {
            return transactionTemplate.execute(status -> {
                // 5.查询是否点赞过
                boolean exists = this.lambdaQuery()
                        .eq(Thumb::getBlogid, doThumbRequest.getBlogId())
                        .eq(Thumb::getUserid, loginUser.getId())
                        .exists();

                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "请勿重复点赞");
                }

                // 6.blog表中blog点赞数+1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, doThumbRequest.getBlogId())
                        .setSql("thumbCount = thumbCount + 1")
                        .update();

                // 7.thumb表中插入一条点赞记录
                Thumb thumb = new Thumb();
                thumb.setBlogid(doThumbRequest.getBlogId());
                thumb.setUserid(loginUser.getId());
                boolean save = this.save(thumb);

                return update && save;
            });
        });
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1.校验参数
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 2.获取登录用户
        User loginUser = userService.getLoginUser(request);
        
        // 3.构造分布式锁的key：用户ID + 博客ID
        String lockKey = UNDO_THUMB_LOCK_PREFIX + loginUser.getId() + ":" + doThumbRequest.getBlogId();

        // 4.使用分布式锁防止重复取消点赞，使用编程式事务确保事务提交或回滚
        return distributedLockUtil.executeWithLock(lockKey, () -> {
            return transactionTemplate.execute(status -> {
                // 5.查询是否点赞过
                boolean exists = this.lambdaQuery()
                        .eq(Thumb::getBlogid, doThumbRequest.getBlogId())
                        .eq(Thumb::getUserid, loginUser.getId())
                        .exists();

                if (!exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "无点赞记录");
                }

                // 6.blog表中blog点赞数-1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, doThumbRequest.getBlogId())
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                // 7.thumb表中删除一条点赞记录
                boolean remove = this.lambdaUpdate()
                        .eq(Thumb::getBlogid, doThumbRequest.getBlogId())
                        .eq(Thumb::getUserid, loginUser.getId())
                        .remove();

                return update && remove;
            });
        });
    }
}




