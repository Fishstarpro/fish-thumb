<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
// 这里可以添加全局逻辑
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  line-height: 1.6;
  color: #333;
  background-color: #f5f7fa;
}

#app {
  min-height: 100vh;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* Element Plus 样式覆盖 */
.el-message {
  min-width: 300px;
}

.el-loading-mask {
  background-color: rgba(255, 255, 255, 0.8);
}
</style>
