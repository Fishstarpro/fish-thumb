package com.yxc.thumbbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yxc.thumbbackend.model.entity.Blog;
import com.yxc.thumbbackend.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author fishstar
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-05-21 00:46:37
*/
public interface BlogService extends IService<Blog> {

    /**
     * 获取博客包装信息
     *
     * @param blogId
     * @param request
     * @return
     */
    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    /**
     * 获取博客包装信息列表
     *
     * @param blogList
     * @param request
     * @return
     */
    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
