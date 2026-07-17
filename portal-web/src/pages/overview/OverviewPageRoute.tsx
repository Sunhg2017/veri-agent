import { useQuery } from '@tanstack/react-query';
import { fetchHealth } from '../../api/health';
import { OverviewPage } from '../../components/AppOverviewPage';
import { useManagementConsole } from '../../layouts/managementConsoleContext';

/** 系统概览页：健康状态自查 + 管理域统计数据来自 ConsoleLayout 上下文 */
export function OverviewPageRoute() {
  const management = useManagementConsole();
  const healthQuery = useQuery({
    queryFn: async () => {
      const response = await fetchHealth();
      return response.data;
    },
    queryKey: ['platform-health'],
    retry: 0
  });

  return (
    <OverviewPage
      health={{
        data: healthQuery.data,
        error: healthQuery.error instanceof Error ? healthQuery.error.message : undefined,
        loading: healthQuery.isLoading
      }}
      managementData={management.data}
    />
  );
}
