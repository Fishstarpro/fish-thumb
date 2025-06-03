import { defineStore } from 'pinia';
import { ref } from 'vue';
import { blogApi, thumbApi } from '@/api';
import { useUserStore } from './user';
import type { BlogVO, DoThumbRequest } from '@/types';
import { ElMessage } from 'element-plus';

export const useBlogStore = defineStore('blog', () => {
  // 状态
  const blogList = ref<BlogVO[]>([]);
  const currentBlog = ref<BlogVO | null>(null);
  const isLoading = ref(false);
  const isThumbLoading = ref<Record<number, boolean>>({});
  
  // 方法
  const getBlogList = async () => {
    try {
      isLoading.value = true;
      const response = await blogApi.getBlogList();
      blogList.value = response.data || [];
      return response.data;
    } catch (error) {
      console.error('获取博客列表失败:', error);
      ElMessage.error('获取博客列表失败');
      return [];
    } finally {
      isLoading.value = false;
    }
  };
  
  const getBlogById = async (blogId: number) => {
    try {
      isLoading.value = true;
      const response = await blogApi.getBlogById(blogId);
      currentBlog.value = response.data;
      return response.data;
    } catch (error) {
      console.error('获取博客详情失败:', error);
      ElMessage.error('获取博客详情失败');
      return null;
    } finally {
      isLoading.value = false;
    }
  };
  
  // 点赞防抖处理
  const thumbDebounceMap = new Map<number, number>();
  
  const doThumb = async (blogId: number) => {
    const userStore = useUserStore();
    
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录');
      return false;
    }
    
    // 防抖处理
    if (thumbDebounceMap.has(blogId)) {
      clearTimeout(thumbDebounceMap.get(blogId)!);
    }
    
    return new Promise<boolean>((resolve) => {
      const timer = setTimeout(async () => {
        try {
          isThumbLoading.value[blogId] = true;
          
          // 找到对应的博客
          const blog = blogList.value.find(b => b.id === blogId) || currentBlog.value;
          if (!blog) {
            ElMessage.error('博客不存在');
            resolve(false);
            return;
          }
          
          // 乐观更新UI
          const wasThumbedBefore = blog.hasThumb;
          blog.hasThumb = !blog.hasThumb;
          blog.thumbCount += blog.hasThumb ? 1 : -1;
          
          // 调用API
          const request: DoThumbRequest = { blogId };
          const response = blog.hasThumb 
            ? await thumbApi.doThumb(request)
            : await thumbApi.undoThumb(request);
          
          if (response.data) {
            ElMessage.success(blog.hasThumb ? '点赞成功' : '取消点赞成功');
            resolve(true);
          } else {
            // 回滚UI更新
            blog.hasThumb = wasThumbedBefore;
            blog.thumbCount += wasThumbedBefore ? 1 : -1;
            ElMessage.error(blog.hasThumb ? '点赞失败' : '取消点赞失败');
            resolve(false);
          }
        } catch (error) {
          console.error('点赞操作失败:', error);
          
          // 回滚UI更新
          const blog = blogList.value.find(b => b.id === blogId) || currentBlog.value;
          if (blog) {
            blog.hasThumb = !blog.hasThumb;
            blog.thumbCount += blog.hasThumb ? 1 : -1;
          }
          
          ElMessage.error('操作失败，请重试');
          resolve(false);
        } finally {
          isThumbLoading.value[blogId] = false;
          thumbDebounceMap.delete(blogId);
        }
      }, 300); // 300ms防抖
      
      thumbDebounceMap.set(blogId, timer);
    });
  };
  
  return {
    // 状态
    blogList,
    currentBlog,
    isLoading,
    isThumbLoading,
    
    // 方法
    getBlogList,
    getBlogById,
    doThumb
  };
}); 