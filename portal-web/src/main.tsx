import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { AppErrorBoundary } from './components/AppErrorBoundary';
import { AppProviders } from './platform/Providers';
import './platform/i18n';
import 'antd/dist/reset.css';
import './theme/global.css';
// 旧版工作台样式，P7 阶段完成面板迁移后移除
import './styles.css';

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <AppProviders>
      <AppErrorBoundary>
        <App />
      </AppErrorBoundary>
    </AppProviders>
  </StrictMode>
);
