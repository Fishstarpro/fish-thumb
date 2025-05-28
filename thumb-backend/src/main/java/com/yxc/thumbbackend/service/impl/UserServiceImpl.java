package com.yxc.thumbbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxc.thumbbackend.constant.UserConstant;
import com.yxc.thumbbackend.exception.BusinessException;
import com.yxc.thumbbackend.exception.ErrorCode;
import com.yxc.thumbbackend.model.entity.User;
import com.yxc.thumbbackend.mapper.UserMapper;
import com.yxc.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author fishstar
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-05-21 00:52:44
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);

        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return user;
    }

}




