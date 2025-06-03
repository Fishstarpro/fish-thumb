import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { userApi } from '@/api';
import type { User } from '@/types';

export const useUserStore = defineStore('user', () => {
  // 状态
  const currentUser = ref<User | null>(null);
  const isLoading = ref(false);
  
  // 计算属性
  const isLoggedIn = computed(() => currentUser.value !== null);
  const userId = computed(() => currentUser.value?.id || null);
  const username = computed(() => currentUser.value?.username || '');
  
  // 方法
  const login = async (userId: number) => {
    try {
      isLoading.value = true;
      const response = await userApi.login({ userId });
      currentUser.value = response.data;
      return response.data;
    } catch (error) {
      console.error('登录失败:', error);
      throw error;
    } finally {
      isLoading.value = false;
    }
  };
  
  const getCurrentUser = async () => {
    try {
      isLoading.value = true;
      const response = await userApi.getCurrentUser();
      currentUser.value = response.data;
      return response.data;
    } catch (error) {
      console.error('获取用户信息失败:', error);
      currentUser.value = null;
      return null;
    } finally {
      isLoading.value = false;
    }
  };
  
  const logout = () => {
    currentUser.value = null;
  };
  
  // 初始化用户信息
  const initUser = async () => {
    await getCurrentUser();
  };
  
  return {
    // 状态
    currentUser,
    isLoading,
    
    // 计算属性
    isLoggedIn,
    userId,
    username,
    
    // 方法
    login,
    getCurrentUser,
    logout,
    initUser
  };
}); 