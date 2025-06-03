# 博客点赞系统前端

这是一个基于 Vue3 + TypeScript + Element Plus 的博客点赞系统前端应用。

## 技术栈

- **前端框架**: Vue 3 + TypeScript
- **UI 组件库**: Element Plus
- **状态管理**: Pinia
- **路由管理**: Vue Router 4
- **HTTP 客户端**: Axios
- **构建工具**: Vite

## 功能特性

- ✅ 用户登录（基于用户ID）
- ✅ 博客列表展示
- ✅ 博客详情查看
- ✅ 点赞/取消点赞功能
- ✅ 实时点赞数量更新
- ✅ 响应式设计
- ✅ 点赞动画效果
- ✅ 防抖处理
- ✅ 乐观更新UI
- ✅ 错误处理和加载状态

## 项目结构

```
src/
├── api/                 # API接口封装
├── components/          # 可复用组件
│   ├── BlogCard.vue    # 博客卡片组件
│   └── ThumbButton.vue # 点赞按钮组件
├── stores/             # Pinia状态管理
│   ├── user.ts         # 用户状态
│   └── blog.ts         # 博客状态
├── types/              # TypeScript类型定义
├── utils/              # 工具函数
│   └── request.ts      # HTTP请求封装
├── views/              # 页面组件
│   ├── LoginView.vue   # 登录页
│   ├── HomeView.vue    # 首页
│   └── BlogDetailView.vue # 博客详情页
├── router/             # 路由配置
└── main.ts             # 应用入口
```

## 开发指南

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

### 构建生产版本

```bash
npm run build
```

### 类型检查

```bash
npm run type-check
```

## 配置说明

### 后端API地址

在 `src/utils/request.ts` 中配置后端API地址：

```typescript
const request = axios.create({
  baseURL: 'http://localhost:8113/api', // 后端API基础地址（包含/api前缀）
  timeout: 10000,
  withCredentials: true,
});
```

### 环境变量

可以创建 `.env.local` 文件来配置环境变量：

```
VITE_API_BASE_URL=http://localhost:8113/api
```

## API接口

### 用户相关
- `GET /api/user/login?userId={id}` - 用户登录
- `GET /api/user/get/login` - 获取当前用户

### 博客相关
- `GET /api/blog/list` - 获取博客列表
- `GET /api/blog/get?blogId={id}` - 获取博客详情

### 点赞相关
- `POST /api/thumb/do` - 点赞
- `POST /api/thumb/undo` - 取消点赞

## 使用说明

1. **登录**: 输入有效的用户ID（如：1, 2, 3...）进行登录
2. **浏览博客**: 在首页查看博客列表，点击卡片查看详情
3. **点赞操作**: 点击心形按钮进行点赞/取消点赞
4. **响应式**: 支持移动端和桌面端访问

## 注意事项

- 确保后端服务已启动并运行在正确端口
- 登录使用的是简化的用户ID方式，实际项目中应使用更安全的认证方式
- 点赞功能包含防抖处理，避免重复点击
- 使用Session进行会话管理，需要后端支持

## 浏览器支持

- Chrome >= 87
- Firefox >= 78
- Safari >= 14
- Edge >= 88
