import request from '@/utils/request';
import type { 
  BaseResponse, 
  User, 
  BlogVO, 
  DoThumbRequest, 
  LoginRequest 
} from '@/types';

// 用户相关API
export const userApi = {
  // 用户登录
  login(params: LoginRequest): Promise<BaseResponse<User>> {
    return request.get('/user/login', { params });
  },
  
  // 获取当前登录用户
  getCurrentUser(): Promise<BaseResponse<User>> {
    return request.get('/user/get/login');
  }
};

// 博客相关API
export const blogApi = {
  // 获取博客列表
  getBlogList(): Promise<BaseResponse<BlogVO[]>> {
    return request.get('/blog/list');
  },
  
  // 根据ID获取博客详情
  getBlogById(blogId: number): Promise<BaseResponse<BlogVO>> {
    return request.get('/blog/get', { params: { blogId } });
  }
};

// 点赞相关API
export const thumbApi = {
  // 点赞
  doThumb(data: DoThumbRequest): Promise<BaseResponse<boolean>> {
    return request.post('/thumb/do', data);
  },
  
  // 取消点赞
  undoThumb(data: DoThumbRequest): Promise<BaseResponse<boolean>> {
    return request.post('/thumb/undo', data);
  }
}; 