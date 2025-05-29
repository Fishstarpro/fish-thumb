package com.yxc.thumbbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yxc.thumbbackend.model.dto.DoThumbRequest;
import com.yxc.thumbbackend.model.entity.Thumb;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author fishstar
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-05-21 00:50:35
*/
public interface ThumbService extends IService<Thumb> {

    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 是否点赞
     * @param blogId
     * @param userId
     * @return {@link Boolean }
     */
    Boolean hasThumb(Long blogId, Long userId);
    
    /**
     * 初始化布隆过滤器
     * 将数据库中已有的点赞记录加载到布隆过滤器中
     */
    void initBloomFilter();
    
    /**
     * 判断博客是否为热数据
     * @param blogId 博客ID
     * @return true-热数据，false-冷数据
     */
    Boolean isHotData(Long blogId);

}
