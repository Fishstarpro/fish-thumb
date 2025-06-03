import axios, { type AxiosResponse, type AxiosError } from 'axios';
import { ElMessage } from 'element-plus';
import type { BaseResponse } from '@/types';

// 创建axios实例
const request = axios.create({
  baseURL: 'http://localhost:8113/api', // 后端API地址
  timeout: 10000, // 请求超时时间
  withCredentials: true, // 支持Session
});

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 可以在这里添加token等认证信息
    return config;
  },
  (error) => {
    console.error('请求错误:', error);
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse<BaseResponse>) => {
    const { code, message, data } = response.data;
    
    // 后端返回的成功状态码通常是 0
    if (code === 0) {
      return response.data as any;
    } else {
      // 业务错误
      ElMessage.error(message || '请求失败');
      return Promise.reject(new Error(message || '请求失败'));
    }
  },
  (error: AxiosError) => {
    console.error('响应错误:', error);
    
    // 网络错误或服务器错误
    if (error.response) {
      const status = error.response.status;
      switch (status) {
        case 401:
          ElMessage.error('未授权，请重新登录');
          break;
        case 403:
          ElMessage.error('拒绝访问');
          break;
        case 404:
          ElMessage.error('请求地址不存在');
          break;
        case 500:
          ElMessage.error('服务器内部错误');
          break;
        default:
          ElMessage.error(`请求错误: ${status}`);
      }
    } else if (error.request) {
      ElMessage.error('网络连接错误');
    } else {
      ElMessage.error('请求配置错误');
    }
    
    return Promise.reject(error);
  }
);

export default request; 