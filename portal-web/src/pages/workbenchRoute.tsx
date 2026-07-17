import type { ComponentType } from 'react';
import { useAppSessionStore } from '../platform/appStore';
import type { CurrentUser } from '../api/auth';

type WorkbenchProps = {
  signedIn: boolean;
  currentUser: CurrentUser | null;
};

/**
 * 工作台路由包装：从会话 store 注入 currentUser，
 * 使各工作台组件可作为路由页面直接渲染（后续阶段再逐个子页拆分）。
 */
export function withWorkbenchRoute(Component: ComponentType<WorkbenchProps>) {
  return function WorkbenchRoute() {
    const currentUser = useAppSessionStore((state) => state.currentUser);
    return <Component signedIn currentUser={currentUser} />;
  };
}
