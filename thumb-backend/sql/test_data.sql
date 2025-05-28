-- 测试数据SQL文件
-- 用于测试BlogServiceImpl.getBlogVOList方法

use thumb_db;

-- 清理现有测试数据（可选）
-- DELETE FROM thumb WHERE userId = 1;
-- DELETE FROM blog WHERE userId IN (1, 2, 3);
-- DELETE FROM user WHERE id IN (1, 2, 3);

-- 插入测试用户数据
INSERT INTO user (id, username) VALUES 
(1, 'testUser1'),
(2, 'testUser2'),
(3, 'testUser3')
ON DUPLICATE KEY UPDATE username = VALUES(username);

-- 插入测试博客数据
INSERT INTO blog (id, userId, title, coverImg, content, thumbCount, createTime, updateTime) VALUES 
(1, 1, 'Java基础知识总结', 'https://example.com/cover1.jpg', 'Java是一门面向对象的编程语言，具有跨平台、安全性高等特点...', 5, '2024-01-01 10:00:00', '2024-01-01 10:00:00'),
(2, 2, 'Spring Boot入门教程', 'https://example.com/cover2.jpg', 'Spring Boot是Spring框架的一个子项目，旨在简化Spring应用的创建...', 8, '2024-01-02 11:00:00', '2024-01-02 11:00:00'),
(3, 1, 'MySQL数据库优化技巧', 'https://example.com/cover3.jpg', 'MySQL是目前最流行的关系型数据库之一，本文介绍一些优化技巧...', 12, '2024-01-03 12:00:00', '2024-01-03 12:00:00'),
(4, 3, 'Redis缓存实战', 'https://example.com/cover4.jpg', 'Redis是一个开源的内存数据结构存储系统，可以用作数据库、缓存...', 3, '2024-01-04 13:00:00', '2024-01-04 13:00:00'),
(5, 2, 'Docker容器化部署', 'https://example.com/cover5.jpg', 'Docker是一个开源的容器化平台，可以帮助开发者更好地打包和部署应用...', 7, '2024-01-05 14:00:00', '2024-01-05 14:00:00'),
(6, 1, 'Vue.js前端开发指南', 'https://example.com/cover6.jpg', 'Vue.js是一个渐进式JavaScript框架，用于构建用户界面...', 15, '2024-01-06 15:00:00', '2024-01-06 15:00:00'),
(7, 3, 'Python数据分析入门', 'https://example.com/cover7.jpg', 'Python在数据分析领域有着广泛的应用，本文介绍基础知识...', 9, '2024-01-07 16:00:00', '2024-01-07 16:00:00'),
(8, 2, 'Git版本控制最佳实践', 'https://example.com/cover8.jpg', 'Git是目前最流行的分布式版本控制系统，掌握Git对开发者很重要...', 6, '2024-01-08 17:00:00', '2024-01-08 17:00:00')
ON DUPLICATE KEY UPDATE 
    title = VALUES(title),
    coverImg = VALUES(coverImg),
    content = VALUES(content),
    thumbCount = VALUES(thumbCount);

-- 插入点赞记录数据（userId固定为1）
-- 用户1点赞了博客1, 3, 5, 6（包括自己的博客1, 3, 6和其他用户的博客5）
INSERT INTO thumb (userId, blogId, createTime) VALUES 
(1, 1, '2024-01-01 10:30:00'),  -- 用户1点赞自己的博客1
(1, 3, '2024-01-03 12:30:00'),  -- 用户1点赞自己的博客3
(1, 5, '2024-01-05 14:30:00'),  -- 用户1点赞用户2的博客5
(1, 6, '2024-01-06 15:30:00')   -- 用户1点赞自己的博客6
ON DUPLICATE KEY UPDATE createTime = VALUES(createTime);

-- 插入其他用户的点赞记录（用于增加博客的点赞数）
INSERT INTO thumb (userId, blogId, createTime) VALUES 
(2, 1, '2024-01-01 11:00:00'),  -- 用户2点赞博客1
(2, 3, '2024-01-03 13:00:00'),  -- 用户2点赞博客3
(2, 6, '2024-01-06 16:00:00'),  -- 用户2点赞博客6
(3, 1, '2024-01-01 12:00:00'),  -- 用户3点赞博客1
(3, 2, '2024-01-02 12:00:00'),  -- 用户3点赞博客2
(3, 5, '2024-01-05 15:00:00'),  -- 用户3点赞博客5
(3, 6, '2024-01-06 17:00:00')   -- 用户3点赞博客6
ON DUPLICATE KEY UPDATE createTime = VALUES(createTime);

-- 查询验证数据
SELECT '=== 用户数据 ===' as info;
SELECT * FROM user WHERE id IN (1, 2, 3);

SELECT '=== 博客数据 ===' as info;
SELECT id, userId, title, thumbCount, createTime FROM blog ORDER BY id;

SELECT '=== 点赞记录数据 ===' as info;
SELECT * FROM thumb ORDER BY userId, blogId;

SELECT '=== 用户1的点赞情况 ===' as info;
SELECT t.userId, t.blogId, b.title, t.createTime 
FROM thumb t 
JOIN blog b ON t.blogId = b.id 
WHERE t.userId = 1 
ORDER BY t.blogId; 