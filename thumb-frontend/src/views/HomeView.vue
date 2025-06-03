<template>
  <div class="home-container">
    <!-- 顶部导航栏 -->
    <div class="header">
      <div class="header-content">
        <h1 class="site-title">博客点赞系统</h1>
        <div class="user-info" v-if="userStore.isLoggedIn">
          <span class="welcome-text">欢迎，{{ userStore.username }}</span>
          <el-button type="text" @click="handleLogout" class="logout-btn">
            退出登录
          </el-button>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <div class="content-wrapper">
        <!-- 加载状态 -->
        <div v-if="blogStore.isLoading" class="loading-container">
          <el-skeleton :rows="3" animated />
          <el-skeleton :rows="3" animated />
          <el-skeleton :rows="3" animated />
        </div>

        <!-- 博客列表 -->
        <div v-else-if="blogStore.blogList.length > 0" class="blog-list">
          <BlogCard
            v-for="blog in blogStore.blogList"
            :key="blog.id"
            :blog="blog"
            @card-click="handleBlogClick"
            @thumb-change="handleThumbChange"
          />
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <el-empty description="暂无博客内容">
            <el-button type="primary" @click="refreshBlogList">
              刷新页面
            </el-button>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- 返回顶部按钮 -->
    <el-backtop :right="40" :bottom="40" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElButton, ElSkeleton, ElEmpty, ElBacktop, ElMessage } from 'element-plus';
import BlogCard from '@/components/BlogCard.vue';
import { useUserStore } from '@/stores/user';
import { useBlogStore } from '@/stores/blog';

const router = useRouter();
const userStore = useUserStore();
const blogStore = useBlogStore();

// 页面初始化
onMounted(async () => {
  // 检查登录状态
  if (!userStore.isLoggedIn) {
    await userStore.initUser();
    
    // 如果仍未登录，跳转到登录页
    if (!userStore.isLoggedIn) {
      router.push('/login');
      return;
    }
  }
  
  // 加载博客列表
  await refreshBlogList();
});

// 刷新博客列表
const refreshBlogList = async () => {
  try {
    await blogStore.getBlogList();
  } catch (error) {
    console.error('加载博客列表失败:', error);
    ElMessage.error('加载博客列表失败');
  }
};

// 处理博客卡片点击
const handleBlogClick = (blogId: number) => {
  router.push(`/blog/${blogId}`);
};

// 处理点赞状态变化
const handleThumbChange = (blogId: number, hasThumb: boolean, thumbCount: number) => {
  // 这里可以添加额外的处理逻辑，比如统计等
  console.log(`博客 ${blogId} 点赞状态变化:`, { hasThumb, thumbCount });
};

// 处理退出登录
const handleLogout = () => {
  userStore.logout();
  router.push('/login');
  ElMessage.success('已退出登录');
};
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.header {
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.site-title {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome-text {
  font-size: 14px;
  color: #606266;
}

.logout-btn {
  font-size: 14px;
  padding: 0;
}

.main-content {
  padding: 24px 20px;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
}

.loading-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.blog-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 24px;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    padding: 12px 16px;
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .site-title {
    font-size: 20px;
  }

  .user-info {
    align-self: flex-end;
  }

  .main-content {
    padding: 16px;
  }

  .blog-list {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

@media (max-width: 480px) {
  .header-content {
    padding: 12px;
  }

  .main-content {
    padding: 12px;
  }

  .site-title {
    font-size: 18px;
  }

  .welcome-text {
    font-size: 12px;
  }
}
</style> 