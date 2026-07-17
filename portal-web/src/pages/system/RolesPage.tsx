import { ShieldCheck } from 'lucide-react';
import { type RoleView } from '../../api/management';
import { translate } from '../../platform/i18n';
import {
  DataSection,
  RoleDefinitionPanel,
  StatusBadge,
  roleDescription,
  roleScope,
  type ManagementPageProps
} from '../../components/management/shared';

/** 角色管理页：RBAC 角色列表 + 角色权限定义侧栏 */
export function RolesPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  return (
    <DataSection
      eyebrow="RBAC"
      title={translate('auto.k0262')}
      icon={ShieldCheck}
      action={translate('auto.k0170')}
      columns={[translate('auto.k0247'), translate('auto.k0263'), translate('auto.k0182'), translate('auto.k0264')]}
      rows={data.roles.map((item: RoleView) => [
        <span key={item.code}>
          <div className="management-primary-text">{item.name}</div>
          <div className="text-tertiary text-xs">{item.code}</div>
        </span>,
        roleScope(item),
        <StatusBadge key={`${item.code}-st`} status={item.status} />,
        roleDescription(item) || '-'
      ])}
      loadState={loadState}
      signedIn={signedIn}
      onRefresh={props.onRefresh}
      sidePanel={
        <RoleDefinitionPanel
          roles={data.roles}
          permissions={data.permissions}
          signedIn={signedIn}
          currentUser={currentUser}
          onChanged={props.onRefresh}
        />
      }
    />
  );
}
