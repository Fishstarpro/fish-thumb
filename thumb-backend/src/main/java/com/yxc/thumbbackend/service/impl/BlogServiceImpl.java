package com.yxc.thumbbackend.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxc.thumbbackend.mapper.BlogMapper;
import com.yxc.thumbbackend.model.entity.Blog;
import com.yxc.thumbbackend.model.entity.User;
import com.yxc.thumbbackend.model.vo.BlogVO;
import com.yxc.thumbbackend.service.BlogService;
import com.yxc.thumbbackend.service.ThumbService;
import com.yxc.thumbbackend.service.UserService;

import cn.hutool.core.bean.BeanUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author fishstar
 * @description 针对表【blog】的数据库操作Service实现
 * @createDate 2025-05-21 00:46:37
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
        implements BlogService {

    @Resource
    private UserService userService;

    @Lazy
    @Resource
    private ThumbService thumbService;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        //1.blog表中查询blog
        Blog blog = this.getById(blogId);

        if (blog == null) {
            return null;
        }
        //2.从thumb表中查询当前用户是否点赞过该博客
        User loginUser = userService.getLoginUser(request);

//        Thumb thumb = thumbService.lambdaQuery()
//                .eq(Thumb::getBlogid, blogId)
//                .eq(Thumb::getUserid, loginUser.getId())
//                .one();
        //优化改为从redis中查询
        Boolean exists = thumbService.hasThumb(blogId, loginUser.getId());
        //3.封装并返回blogVO
        BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);

        blogVO.setHasThumb(exists);

        return blogVO;
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 使用优化后的hasThumb方法逐个查询（利用布隆过滤器和缓存优化）
        Map<Long, Boolean> blogIdToHasThumbMap = new HashMap<>();
        
        for (Blog blog : blogList) {
            Boolean hasThumb = thumbService.hasThumb(blog.getId(), loginUser.getId());
            blogIdToHasThumbMap.put(blog.getId(), hasThumb);
        }
        
        // 封装并返回blogVO列表
        List<BlogVO> blogVOList = blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdToHasThumbMap.getOrDefault(blog.getId(), false));
                    return blogVO;
                })
                .collect(Collectors.toList());

        return blogVOList;
    }
}




