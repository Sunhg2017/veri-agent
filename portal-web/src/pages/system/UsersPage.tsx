import { CheckCircle2, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-react';
import {
  fetchUser,
  updateUser,
  type UserView
} from '../../api/management';
import { hasPermission } from '../../permissions';
import { translate } from '../../platform/i18n';
import {
  DataSection,
  ResourceLifecyclePanel,
  RoleBindingControls,
  StatusBadge,
  roleDisplayLabel,
  type ManagementPageProps
} from '../../components/management/shared';

/** 用户管理页：用户列表 + 生命周期操作 + 角色绑定 */
export function UsersPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  const canEnable = hasPermission(currentUser, 'user:enable');
  const canDisable = hasPermission(currentUser, 'user:disable');
  const canLock = hasPermission(currentUser, 'user:lock');
  const canEdit = hasPermission(currentUser, 'user:edit');
  const canResetPassword = hasPermission(currentUser, 'user:reset_password');
  const canAssignRole = hasPermission(currentUser, 'user:assign_role') && hasPermission(currentUser, 'role:bind');
  const canUnassignRole = hasPermission(currentUser, 'user:assign_role') && hasPermission(currentUser, 'role:unbind');
  const canMutateUsers = canEnable || canDisable || canLock || canResetPassword || canAssignRole || canUnassignRole;
  return (
    <DataSection
      eyebrow={translate('auto.k0243')}
      title={translate('auto.k0244')}
      icon={ShieldCheck}
      action={translate('auto.k0245')}
      createResource="users"
      columns={canMutateUsers ? [translate('auto.k0246'), translate('auto.k0247'), translate('auto.k0232'), translate('auto.k0182'), translate('auto.k0248'), translate('auto.k0249')] : [translate('auto.k0246'), translate('auto.k0247'), translate('auto.k0232'), translate('auto.k0182'), translate('auto.k0248')]}
      rows={data.users.map((item: UserView) => [
        item.username,
        roleDisplayLabel(item.role),
        item.department,
        <span key={`${item.username}-status`}>
          <StatusBadge status={item.status} />
        </span>,
        item.last_seen,
        ...(canMutateUsers ? [(
          <div className="row-actions row-actions-start" key={`${item.username}-actions`}>
            <RoleBindingControls
              username={item.username}
              roles={data.roles}
              loading={loadState.loading}
              signedIn={signedIn}
              canAssign={canAssignRole}
              canUnassign={canUnassignRole}
              onAction={props.onUserLifecycleAction}
            />
            {canEnable && (
              <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading}
                onClick={() => props.onUserLifecycleAction(item.username, item.status === translate('auto.k0068') ? 'unlock' : 'enable')}>
                <CheckCircle2 size={13} />{item.status === translate('auto.k0068') ? translate('auto.k0250') : translate('auto.k0251')}
              </button>
            )}
            {canLock && (
              <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading || item.username === currentUser?.username || item.status === translate('auto.k0068')}
                onClick={() => props.onUserLifecycleAction(item.username, 'lock')}>
                <LockKeyhole size={13} />{translate('auto.k0252')}</button>
            )}
            {canDisable && (
              <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading || item.username === currentUser?.username}
                onClick={() => props.onUserLifecycleAction(item.username, 'disable')}>
                <LockKeyhole size={13} />{translate('auto.k0253')}</button>
            )}
            {canResetPassword && (
              <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading}
                onClick={() => props.onResetPassword(item.username)}>
                <KeyRound size={13} />{translate('auto.k0254')}</button>
            )}
          </div>
        )] : [])
      ])}
      loadState={loadState}
      signedIn={signedIn}
      canCreate={hasPermission(currentUser, 'user:create')}
      onCreate={props.onCreate}
      onRefresh={props.onRefresh}
      sidePanel={
        <ResourceLifecyclePanel<UserView>
          title={translate('auto.k0255')}
          resourceLabel={translate('auto.k0256')}
          emptyLabel={translate('auto.k0257')}
          resources={data.users.map((u) => u.username)}
          fields={[{ key: 'display_name', label: translate('auto.k0258'), placeholder: translate('auto.k0259') }, { key: 'email', label: translate('auto.k0260'), placeholder: 'user@example.com' }]}
          signedIn={signedIn}
          canEdit={canEdit}
          statusOptions={[]}
          fetchDetail={fetchUser}
          updateDetail={(key, draft) => updateUser(key, draft)}
          changeStatus={() => Promise.resolve()}
          detailTitle={(d) => d.display_name || d.username}
          draftFromDetail={(d) => ({ display_name: d.display_name, email: d.email })}
          detailRows={(d) => [[translate('auto.k0261'), d.username], [translate('auto.k0260'), d.email || '-'], [translate('auto.k0247'), roleDisplayLabel(d.role)], [translate('auto.k0232'), d.department], [translate('auto.k0182'), d.status], [translate('auto.k0248'), d.last_seen]]}
          onChanged={props.onRefresh}
        />
      }
    />
  );
}
