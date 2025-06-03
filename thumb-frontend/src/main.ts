import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';

import App from './App.vue';
import router from './router';

const app = createApp(App);

// 使用 Pinia
app.use(createPinia());

// 使用 Vue Router
app.use(router);

// 使用 Element Plus
app.use(ElementPlus);

app.mount('#app');
