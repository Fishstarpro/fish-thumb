// API 基础响应类型
export interface BaseResponse<T = any> {
  code: number;
  data: T;
  message: string;
}

// 用户相关类型
export interface User {
  id: number;
  username: string;
}

// 博客相关类型
export interface Blog {
  id: number;
  userid: number;
  title: string;
  coverimg: string;
  content: string;
  thumbcount: number;
  createtime: string;
  updatetime: string;
}

// 博客视图对象类型
export interface BlogVO {
  id: number;
  title: string;
  coverImg: string;
  content: string;
  thumbCount: number;
  createTime: string;
  hasThumb: boolean;
}

// 点赞相关类型
export interface Thumb {
  id: number;
  userid: number;
  blogid: number;
  createtime: string;
}

// 点赞请求类型
export interface DoThumbRequest {
  blogId: number;
}

// 登录请求类型
export interface LoginRequest {
  userId: number;
} 