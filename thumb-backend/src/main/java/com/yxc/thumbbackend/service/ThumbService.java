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
}
