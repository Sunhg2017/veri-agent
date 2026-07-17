import { createContext, useContext } from 'react';
import type {
  AuditOutboxFilters,
  CreatableManagementResource,
  ManagementData
} from '../api/management';
import type { UserLifecycleAction } from '../permissions';

/**
 * 管理域数据上下文：由 ConsoleLayout 统一加载并分发，
 * 系统管理各子页面（用户/角色/项目/审计等）通过 useManagementConsole 消费。
 */
export type ManagementConsoleValue = {
  data: ManagementData;
  loadState: { loading: boolean; error?: string };
  auditExportState: { loading: boolean; error?: string };
  auditOutboxFilters: AuditOutboxFilters;
  auditOutboxLoad: { loading: boolean; error?: string };
  onCreate: (resource: CreatableManagementResource, label: string, name: string) => Promise<void>;
  onUserLifecycleAction: (username: string, action: UserLifecycleAction, roleCode?: string) => Promise<void>;
  onResetPassword: (username: string) => void;
  onAuditExport: () => Promise<void>;
  onAuditOutboxFiltersChange: (filters: AuditOutboxFilters) => void;
  onAuditOutboxRefresh: (filters?: AuditOutboxFilters) => Promise<void>;
  onRefresh: () => void;
};

export const ManagementConsoleContext = createContext<ManagementConsoleValue | null>(null);

export function useManagementConsole(): ManagementConsoleValue {
  const value = useContext(ManagementConsoleContext);
  if (!value) {
    throw new Error('useManagementConsole must be used within ConsoleLayout');
  }
  return value;
}
