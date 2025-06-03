<template>
  <div class="thumb-button" @click="handleClick">
    <el-button 
      :type="hasThumb ? 'danger' : 'default'"
      :loading="isLoading"
      :disabled="isLoading"
      circle
      size="small"
      class="thumb-btn"
      :class="{ 'is-thumbed': hasThumb }"
    >
      <el-icon :class="{ 'thumb-icon-animate': isAnimating }">
        <StarFilled v-if="hasThumb" />
        <Star v-else />
      </el-icon>
    </el-button>
    <span class="thumb-count">{{ thumbCount }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { ElButton, ElIcon } from 'element-plus';
import { Star, StarFilled } from '@element-plus/icons-vue';
import { useBlogStore } from '@/stores/blog';

// 点赞按钮组件逻辑

interface Props {
  blogId: number;
  thumbCount: number;
  hasThumb: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  thumbChange: [blogId: number, hasThumb: boolean, thumbCount: number];
}>();

const blogStore = useBlogStore();
const isAnimating = ref(false);

const isLoading = computed(() => blogStore.isThumbLoading[props.blogId] || false);

const handleClick = async () => {
  // 触发动画
  isAnimating.value = true;
  setTimeout(() => {
    isAnimating.value = false;
  }, 300);

  // 执行点赞操作
  const success = await blogStore.doThumb(props.blogId);
  if (success) {
    // 通知父组件状态变化
    emit('thumbChange', props.blogId, !props.hasThumb, props.thumbCount + (props.hasThumb ? -1 : 1));
  }
};
</script>

<style scoped>
.thumb-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.thumb-btn {
  transition: all 0.3s ease;
  border: 1px solid #dcdfe6;
}

.thumb-btn.is-thumbed {
  background-color: #f56565;
  border-color: #f56565;
  color: white;
}

.thumb-btn:hover {
  transform: scale(1.1);
}

.thumb-icon-animate {
  animation: thumbPulse 0.3s ease;
}

@keyframes thumbPulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.3);
  }
  100% {
    transform: scale(1);
  }
}

.thumb-count {
  font-size: 14px;
  color: #666;
  font-weight: 500;
  min-width: 20px;
  text-align: left;
}
</style> 