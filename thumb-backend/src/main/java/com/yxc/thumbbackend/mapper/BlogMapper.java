package com.yxc.thumbbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yxc.thumbbackend.model.entity.Blog;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author fishstar
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2025-05-21 00:46:37
* @Entity .domain.Blog
*/
public interface BlogMapper extends BaseMapper<Blog> {

    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}




