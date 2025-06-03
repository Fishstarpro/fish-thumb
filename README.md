# Fish-Thumb 点赞系统

一个基于 Vue 3 + Spring Boot 3 的现代化点赞系统，支持博客内容的点赞和取消点赞功能。

## 📋 项目简介

Fish-Thumb 是一个全栈点赞系统，提供了完整的用户认证、博客展示和点赞功能。项目采用前后端分离架构，具有高性能、高可用的特点。

### ✨ 主要功能

- 🔐 用户登录认证
- 📝 博客内容展示
- 👍 点赞/取消点赞
- 📊 点赞数统计
- 🔄 实时数据更新
- 📈 性能监控（Prometheus + Grafana）

## 🏗️ 技术架构

### 前端技术栈

- **Vue 3** - 渐进式JavaScript框架
- **TypeScript** - 类型安全的JavaScript超集
- **Vite** - 现代化构建工具
- **Element Plus** - Vue 3 UI组件库
- **Axios** - HTTP客户端
- **Pinia** - Vue 3状态管理
- **Vue Router** - 路由管理

### 后端技术栈

- **Spring Boot 3.4.5** - Java企业级应用框架
- **Java 21** - 最新LTS版本
- **MyBatis-Plus** - 增强版MyBatis持久层框架
- **MySQL** - 关系型数据库
- **Redis** - 内存数据库（缓存/分布式锁）
- **Redisson** - Redis分布式锁实现
- **Spring Session** - 分布式会话管理
- **Apache Pulsar** - 消息队列
- **Caffeine** - 本地缓存
- **Knife4j** - API文档工具
- **Prometheus** - 性能监控
- **Hutool** - Java工具库

## 📁 项目结构

```
fish-thumb/
├── thumb-frontend/          # Vue 3 前端项目
│   ├── src/
│   │   ├── api/            # API接口
│   │   ├── components/     # 公共组件
│   │   ├── router/         # 路由配置
│   │   ├── stores/         # Pinia状态管理
│   │   ├── types/          # TypeScript类型定义
│   │   ├── utils/          # 工具函数
│   │   └── views/          # 页面组件
│   ├── package.json
│   └── vite.config.ts
├── thumb-backend/           # Spring Boot 后端项目
│   ├── src/main/java/com/yxc/thumbbackend/
│   │   ├── annotation/     # 自定义注解
│   │   ├── aspect/         # AOP切面
│   │   ├── common/         # 公共类
│   │   ├── config/         # 配置类
│   │   ├── constant/       # 常量定义
│   │   ├── controller/     # 控制器
│   │   ├── exception/      # 异常处理
│   │   ├── job/            # 定时任务
│   │   ├── listener/       # 消息监听器
│   │   ├── manager/        # 业务管理层
│   │   ├── mapper/         # 数据访问层
│   │   ├── model/          # 数据模型
│   │   ├── service/        # 业务逻辑层
│   │   ├── task/           # 异步任务
│   │   └── utils/          # 工具类
│   ├── sql/                # 数据库脚本
│   └── pom.xml
├── .gitignore
├── LICENSE
└── README.md
```

## 🚀 快速开始

### 环境要求

- **Java**: 21+
- **Node.js**: 18+
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **Maven**: 3.8+

### 数据库初始化

1. 创建MySQL数据库：
```sql
CREATE DATABASE thumb_db;
```

2. 执行数据库脚本：
```bash
# 导入表结构
mysql -u root -p thumb_db < thumb-backend/sql/create.sql

# 导入测试数据（可选）
mysql -u root -p thumb_db < thumb-backend/sql/test_data.sql
```

### 后端启动

1. 进入后端目录：
```bash
cd thumb-backend
```

2. 配置数据库连接（修改 `application.yml`）：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/thumb_db
    username: your_username
    password: your_password
  redis:
    host: localhost
    port: 6379
```

3. 启动后端服务：
```bash
# 使用Maven启动
./mvnw spring-boot:run

# 或者先编译再运行
./mvnw clean package
java -jar target/thumb-backend-0.0.1-SNAPSHOT.jar
```

后端服务将在 `http://localhost:8080` 启动

### 前端启动

1. 进入前端目录：
```bash
cd thumb-frontend
```

2. 安装依赖：
```bash
npm install
```

3. 启动开发服务器：
```bash
npm run dev
```

前端应用将在 `http://localhost:5173` 启动

## 🔧 API文档

启动后端服务后，可以通过以下地址访问API文档：

- **Knife4j文档**: http://localhost:8080/doc.html
- **OpenAPI规范**: http://localhost:8080/v3/api-docs

### 主要API接口

- `POST /thumb/do` - 点赞
- `POST /thumb/undo` - 取消点赞
- `GET /blog/list` - 获取博客列表
- `GET /blog/get` - 获取博客详情
- `POST /user/login` - 用户登录

## 📊 监控面板

项目集成了Prometheus监控，可以通过以下地址访问：

- **Actuator端点**: http://localhost:8080/actuator
- **Prometheus指标**: http://localhost:8080/actuator/prometheus

配置Grafana可视化监控面板来展示系统性能指标。

## 🛠️ 开发指南

### 前端开发

```bash
# 开发模式
npm run dev

# 类型检查
npm run type-check

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

### 后端开发

```bash
# 运行测试
./mvnw test

# 打包应用
./mvnw clean package

# 跳过测试打包
./mvnw clean package -DskipTests
```

## 🔒 核心特性

### 高性能设计

- **多级缓存**: Redis + Caffeine本地缓存
- **分布式锁**: Redisson防止重复点赞
- **布隆过滤器**: Google Guava防止缓存穿透
- **异步处理**: Spring异步任务处理

### 高可用保障

- **分布式会话**: Spring Session + Redis
- **消息队列**: Apache Pulsar解耦业务
- **监控告警**: Prometheus + Grafana
- **优雅降级**: 多重异常处理机制

## 📈 性能优化

1. **数据库优化**
   - 索引优化
   - 连接池配置
   - 读写分离（可扩展）

2. **缓存策略** 
   - L1缓存（Caffeine）
   - L2缓存（Redis）
   - 缓存预热

3. **并发控制**
   - 分布式锁
   - 乐观锁
   - 限流熔断

## 🤝 贡献指南

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开Pull Request

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源协议。

## 📞 联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交 [Issues](../../issues)
- 发起 [Pull Request](../../pulls)

---

⭐ 如果这个项目对你有帮助，请给它一个星标！ 