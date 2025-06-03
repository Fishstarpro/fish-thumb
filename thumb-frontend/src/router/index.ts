import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: {
        requiresAuth: true,
        title: '首页'
      }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: {
        title: '登录'
      }
    },
    {
      path: '/blog/:id',
      name: 'blogDetail',
      component: () => import('@/views/BlogDetailView.vue'),
      meta: {
        requiresAuth: true,
        title: '博客详情'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notFound',
      redirect: '/'
    }
  ]
});

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore();
  
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 博客点赞系统`;
  }
  
  // 检查是否需要登录
  if (to.meta.requiresAuth) {
    // 如果没有登录状态，尝试获取用户信息
    if (!userStore.isLoggedIn) {
      await userStore.initUser();
    }
    
    // 如果仍然没有登录，跳转到登录页
    if (!userStore.isLoggedIn) {
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      });
      return;
    }
  }
  
  // 如果已经登录且访问登录页，跳转到首页
  if (to.path === '/login' && userStore.isLoggedIn) {
    next('/');
    return;
  }
  
  next();
});

export default router; 