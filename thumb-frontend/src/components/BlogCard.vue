<template>
  <el-card class="blog-card" shadow="hover" @click="handleCardClick">
    <template #header>
      <div class="card-header">
        <h3 class="blog-title">{{ blog.title }}</h3>
        <span class="blog-time">{{ formatTime(blog.createTime) }}</span>
      </div>
    </template>
    
    <div class="blog-cover" v-if="blog.coverImg">
      <el-image 
        :src="blog.coverImg" 
        :alt="blog.title"
        fit="cover"
        class="cover-image"
        :preview-src-list="[blog.coverImg]"
        @click.stop
      />
    </div>
    
    <div class="blog-content">
      <p class="content-preview">{{ getContentPreview(blog.content) }}</p>
    </div>
    
    <div class="blog-footer">
      <div class="blog-stats">
        <el-icon><View /></el-icon>
        <span>阅读</span>
      </div>
      
      <div class="thumb-section" @click.stop>
        <ThumbButton 
          :blog-id="blog.id"
          :thumb-count="blog.thumbCount"
          :has-thumb="blog.hasThumb"
          @thumb-change="handleThumbChange"
        />
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ElCard, ElImage, ElIcon } from 'element-plus';
import { View } from '@element-plus/icons-vue';
import ThumbButton from './ThumbButton.vue';
import type { BlogVO } from '@/types';

interface Props {
  blog: BlogVO;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  cardClick: [blogId: number];
  thumbChange: [blogId: number, hasThumb: boolean, thumbCount: number];
}>();

// 格式化时间
const formatTime = (timeStr: string) => {
  const date = new Date(timeStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  
  if (diff < hour) {
    return `${Math.floor(diff / minute)}分钟前`;
  } else if (diff < day) {
    return `${Math.floor(diff / hour)}小时前`;
  } else if (diff < 7 * day) {
    return `${Math.floor(diff / day)}天前`;
  } else {
    return date.toLocaleDateString('zh-CN');
  }
};

// 获取内容预览
const getContentPreview = (content: string) => {
  if (!content) return '暂无内容';
  
  // 移除HTML标签
  const textContent = content.replace(/<[^>]*>/g, '');
  
  // 限制长度
  return textContent.length > 150 
    ? textContent.substring(0, 150) + '...'
    : textContent;
};

// 处理卡片点击
const handleCardClick = () => {
  emit('cardClick', props.blog.id);
};

// 处理点赞变化
const handleThumbChange = (blogId: number, hasThumb: boolean, thumbCount: number) => {
  emit('thumbChange', blogId, hasThumb, thumbCount);
};
</script>

<style scoped>
.blog-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 12px;
  overflow: hidden;
}

.blog-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.blog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
  flex: 1;
}

.blog-time {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  margin-top: 2px;
}

.blog-cover {
  margin-bottom: 16px;
}

.cover-image {
  width: 100%;
  height: 200px;
  border-radius: 8px;
  object-fit: cover;
}

.blog-content {
  margin-bottom: 16px;
}

.content-preview {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
  text-align: justify;
}

.blog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.blog-stats {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.thumb-section {
  display: flex;
  align-items: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .blog-title {
    font-size: 16px;
  }
  
  .cover-image {
    height: 150px;
  }
  
  .content-preview {
    font-size: 13px;
  }
  
  .card-header {
    flex-direction: column;
    gap: 8px;
  }
  
  .blog-time {
    align-self: flex-start;
  }
}
</style> 