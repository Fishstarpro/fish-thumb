package com.yxc.thumbbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yxc.thumbbackend.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author fishstar
* @description 针对表【user】的数据库操作Service
* @createDate 2025-05-21 00:52:44
*/
public interface UserService extends IService<User> {

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);
}
