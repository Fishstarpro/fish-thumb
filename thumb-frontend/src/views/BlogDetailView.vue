<template>
  <div class="blog-detail-container">
    <!-- 顶部导航 -->
    <div class="header">
      <div class="header-content">
        <el-button 
          type="text" 
          :icon="ArrowLeft" 
          @click="goBack"
          class="back-btn"
        >
          返回
        </el-button>
        <h1 class="site-title">博客详情</h1>
        <div class="user-info" v-if="userStore.isLoggedIn">
          <span class="welcome-text">{{ userStore.username }}</span>
        </div>
      </div>
    </div>

    <!-- 主要内容 -->
    <div class="main-content">
      <div class="content-wrapper">
        <!-- 加载状态 -->
        <div v-if="blogStore.isLoading" class="loading-container">
          <el-skeleton :rows="8" animated />
        </div>

        <!-- 博客内容 -->
        <div v-else-if="blogStore.currentBlog" class="blog-content">
          <article class="blog-article">
            <!-- 博客头部 -->
            <header class="blog-header">
              <h1 class="blog-title">{{ blogStore.currentBlog.title }}</h1>
              <div class="blog-meta">
                <span class="blog-time">
                  <el-icon><Clock /></el-icon>
                  {{ formatTime(blogStore.currentBlog.createTime) }}
                </span>
                <div class="blog-actions">
                  <ThumbButton 
                    :blog-id="blogStore.currentBlog.id"
                    :thumb-count="blogStore.currentBlog.thumbCount"
                    :has-thumb="blogStore.currentBlog.hasThumb"
                    @thumb-change="handleThumbChange"
                  />
                </div>
              </div>
            </header>

            <!-- 封面图片 -->
            <div v-if="blogStore.currentBlog.coverImg" class="blog-cover">
              <el-image 
                :src="blogStore.currentBlog.coverImg" 
                :alt="blogStore.currentBlog.title"
                fit="cover"
                class="cover-image"
                :preview-src-list="[blogStore.currentBlog.coverImg]"
              />
            </div>

            <!-- 博客正文 -->
            <div class="blog-body">
              <div 
                class="content-text" 
                v-html="formatContent(blogStore.currentBlog.content)"
              ></div>
            </div>

            <!-- 博客底部 -->
            <footer class="blog-footer">
              <div class="footer-actions">
                <ThumbButton 
                  :blog-id="blogStore.currentBlog.id"
                  :thumb-count="blogStore.currentBlog.thumbCount"
                  :has-thumb="blogStore.currentBlog.hasThumb"
                  @thumb-change="handleThumbChange"
                />
              </div>
            </footer>
          </article>
        </div>

        <!-- 错误状态 -->
        <div v-else class="error-state">
          <el-empty description="博客不存在或加载失败">
            <el-button type="primary" @click="goBack">
              返回首页
            </el-button>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- 返回顶部 -->
    <el-backtop :right="40" :bottom="40" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { 
  ElButton, 
  ElSkeleton, 
  ElEmpty, 
  ElBacktop, 
  ElImage, 
  ElIcon, 
  ElMessage 
} from 'element-plus';
import { ArrowLeft, Clock } from '@element-plus/icons-vue';
import ThumbButton from '@/components/ThumbButton.vue';
import { useUserStore } from '@/stores/user';
import { useBlogStore } from '@/stores/blog';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const blogStore = useBlogStore();

// 页面初始化
onMounted(async () => {
  // 检查登录状态
  if (!userStore.isLoggedIn) {
    await userStore.initUser();
    
    if (!userStore.isLoggedIn) {
      router.push('/login');
      return;
    }
  }
  
  // 加载博客详情
  await loadBlogDetail();
});

// 监听路由变化
watch(() => route.params.id, async (newId) => {
  if (newId) {
    await loadBlogDetail();
  }
});

// 加载博客详情
const loadBlogDetail = async () => {
  const blogId = Number(route.params.id);
  
  if (!blogId || isNaN(blogId)) {
    ElMessage.error('无效的博客ID');
    router.push('/');
    return;
  }
  
  try {
    await blogStore.getBlogById(blogId);
    
    if (!blogStore.currentBlog) {
      ElMessage.error('博客不存在');
      router.push('/');
    }
  } catch (error) {
    console.error('加载博客详情失败:', error);
    ElMessage.error('加载博客详情失败');
  }
};

// 格式化时间
const formatTime = (timeStr: string) => {
  const date = new Date(timeStr);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 格式化内容
const formatContent = (content: string) => {
  if (!content) return '暂无内容';
  
  // 简单的内容格式化，将换行符转换为 <br>
  return content
    .replace(/\n/g, '<br>')
    .replace(/\r/g, '');
};

// 返回上一页
const goBack = () => {
  router.back();
};

// 处理点赞变化
const handleThumbChange = (blogId: number, hasThumb: boolean, thumbCount: number) => {
  console.log(`博客 ${blogId} 点赞状态变化:`, { hasThumb, thumbCount });
};
</script>

<style scoped>
.blog-detail-container {
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

.back-btn {
  font-size: 14px;
  padding: 8px 0;
}

.site-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
  flex: 1;
  text-align: center;
}

.user-info {
  display: flex;
  align-items: center;
}

.welcome-text {
  font-size: 14px;
  color: #606266;
}

.main-content {
  padding: 24px 20px;
}

.content-wrapper {
  max-width: 800px;
  margin: 0 auto;
}

.loading-container {
  background: white;
  border-radius: 12px;
  padding: 24px;
}

.blog-content {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.blog-article {
  padding: 32px;
}

.blog-header {
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.blog-title {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  line-height: 1.3;
  margin: 0 0 16px 0;
}

.blog-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.blog-time {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #909399;
}

.blog-actions {
  display: flex;
  align-items: center;
}

.blog-cover {
  margin-bottom: 32px;
}

.cover-image {
  width: 100%;
  max-height: 400px;
  border-radius: 8px;
  object-fit: cover;
}

.blog-body {
  margin-bottom: 32px;
}

.content-text {
  font-size: 16px;
  line-height: 1.8;
  color: #303133;
  text-align: justify;
}

.content-text :deep(p) {
  margin-bottom: 16px;
}

.content-text :deep(h1),
.content-text :deep(h2),
.content-text :deep(h3) {
  margin: 24px 0 16px 0;
  font-weight: 600;
}

.content-text :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 16px 0;
}

.blog-footer {
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

.footer-actions {
  display: flex;
  justify-content: center;
}

.error-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    padding: 12px 16px;
  }

  .site-title {
    font-size: 18px;
  }

  .main-content {
    padding: 16px;
  }

  .blog-article {
    padding: 20px;
  }

  .blog-title {
    font-size: 24px;
  }

  .blog-meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .content-text {
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  .header-content {
    padding: 12px;
  }

  .main-content {
    padding: 12px;
  }

  .blog-article {
    padding: 16px;
  }

  .blog-title {
    font-size: 20px;
  }

  .content-text {
    font-size: 14px;
  }
}
</style> 