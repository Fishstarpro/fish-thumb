package com.yxc.thumbbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxc.thumbbackend.mapper.BlogMapper;
import com.yxc.thumbbackend.model.entity.Blog;
import com.yxc.thumbbackend.model.entity.Thumb;
import com.yxc.thumbbackend.model.entity.User;
import com.yxc.thumbbackend.model.vo.BlogVO;
import com.yxc.thumbbackend.service.BlogService;
import com.yxc.thumbbackend.service.ThumbService;
import com.yxc.thumbbackend.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        //1.blog表中查询blog
        Blog blog = this.getById(blogId);

        if (blog == null) {
            return null;
        }
        //2.从thumb表中查询当前用户是否点赞过该博客
        User loginUser = userService.getLoginUser(request);

        Thumb thumb = thumbService.lambdaQuery()
                .eq(Thumb::getBlogid, blogId)
                .eq(Thumb::getUserid, loginUser.getId())
                .one();
        //3.封装并返回blogVO
        BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);

        blogVO.setHasThumb(thumb != null);

        return blogVO;
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        //1.从thumb表中中查询blogList中被当前登录用户点赞过的thumb
        User loginUser = userService.getLoginUser(request);

        Set<Long> blogIdSet = blogList.stream()
                .map(Blog::getId)
                .collect(Collectors.toSet());

        List<Thumb> thumbList = thumbService.lambdaQuery()
                .eq(Thumb::getUserid, loginUser.getId())
                .in(Thumb::getBlogid, blogIdSet)
                .list();
        //2.将查询到的thumbList放到一个Map中，key为blogId，value为true
        Map<Long, Boolean> blogIdToHasThumbMap = thumbList.stream()
               .collect(Collectors.toMap(Thumb::getBlogid, thumb -> true));
        //3.遍历blogList，将thumbMap中的值设置到blogVO中
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




