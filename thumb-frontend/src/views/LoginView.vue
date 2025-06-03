<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">点赞系统</h1>
        <p class="login-subtitle">欢迎使用博客点赞系统</p>
      </div>
      
      <el-form 
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="userId">
          <el-input
            v-model.number="loginForm.userId"
            type="number"
            placeholder="请输入用户ID"
            size="large"
            clearable
            :prefix-icon="User"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button 
            type="primary" 
            size="large" 
            :loading="isLoading"
            @click="handleLogin"
            class="login-button"
          >
            {{ isLoading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>
      
      <div class="login-tips">
        <p>提示：请输入有效的用户ID进行登录</p>
        <p>示例：1, 2, 3...</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElForm, ElFormItem, ElInput, ElButton, ElMessage } from 'element-plus';
import { User } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import type { FormInstance, FormRules } from 'element-plus';

const router = useRouter();
const userStore = useUserStore();

const loginFormRef = ref<FormInstance>();
const isLoading = ref(false);

// 表单数据
const loginForm = reactive({
  userId: null as number | null
});

// 表单验证规则
const loginRules: FormRules = {
  userId: [
    { required: true, message: '请输入用户ID', trigger: 'blur' },
    { type: 'number', message: '用户ID必须是数字', trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        if (value && value <= 0) {
          callback(new Error('用户ID必须大于0'));
        } else {
          callback();
        }
      }, 
      trigger: 'blur' 
    }
  ]
};

// 处理登录
const handleLogin = async () => {
  if (!loginFormRef.value) return;
  
  try {
    // 表单验证
    await loginFormRef.value.validate();
    
    if (!loginForm.userId) {
      ElMessage.error('请输入用户ID');
      return;
    }
    
    isLoading.value = true;
    
    // 调用登录接口
    await userStore.login(loginForm.userId);
    
    ElMessage.success('登录成功');
    
    // 跳转到首页
    router.push('/');
    
  } catch (error) {
    console.error('登录失败:', error);
    ElMessage.error('登录失败，请检查用户ID是否正确');
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  background: white;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 8px 0;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.login-form {
  margin-bottom: 24px;
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

.login-tips {
  text-align: center;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.login-tips p {
  margin: 4px 0;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .login-card {
    padding: 24px;
    margin: 0 16px;
  }
  
  .login-title {
    font-size: 24px;
  }
}
</style> 