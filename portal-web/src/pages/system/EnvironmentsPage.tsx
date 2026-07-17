import { Power, ServerCog } from 'lucide-react';
import {
  addEnvironmentUser,
  changeEnvironmentStatus,
  fetchEnvironment,
  fetchEnvironmentUsers,
  removeEnvironmentUser,
  updateEnvironment,
  type EnvironmentView
} from '../../api/management';
import { hasPermission } from '../../permissions';
import { dictionaryLabel, fieldLabel } from '../../platform/dictionaries';
import { translate } from '../../platform/i18n';
import {
  DataSection,
  EnvironmentConnectivityPanel,
  ResourceLifecyclePanel,
  ScopedRolePanel,
  StatusBadge,
  type ManagementPageProps,
  type StatusOption
} from '../../components/management/shared';

/** 环境管理页：环境列表 + 生命周期 + 连通性检查 + 成员侧栏 */
export function EnvironmentsPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  const envTypeOptions = [
    { value: '', label: translate('auto.k0265') }, { value: 'DEV', label: dictionaryLabel('DEV') }, { value: 'TEST', label: dictionaryLabel('TEST') },
    { value: 'STAGING', label: dictionaryLabel('STAGING') }, { value: 'PREPROD', label: dictionaryLabel('PREPROD') }, { value: 'PROD', label: dictionaryLabel('PROD') }
  ];
  return (
    <DataSection
      eyebrow={translate('auto.k0031')}
      title={translate('auto.k0296')}
      icon={ServerCog}
      action={translate('auto.k0297')}
      createResource="environments"
      columns={[translate('auto.k0215'), translate('auto.k0298'), fieldLabel('endpoint'), translate('auto.k0182')]}
      rows={data.environments.map((item: EnvironmentView) => [item.name, item.cluster, item.endpoint, <StatusBadge key={item.name} status={item.status} />])}
      loadState={loadState}
      signedIn={signedIn}
      canCreate={hasPermission(currentUser, 'environment:create')}
      onCreate={props.onCreate}
      onRefresh={props.onRefresh}
      sidePanel={
        <>
          <ResourceLifecyclePanel<EnvironmentView>
            title={translate('auto.k0299')}
            resourceLabel={translate('auto.k0215')}
            emptyLabel={translate('auto.k0300')}
            resources={data.environments.map((e) => e.name)}
            fields={[
              { key: 'name', label: translate('auto.k0301'), placeholder: translate('auto.k0302') },
              { key: 'env_type', label: translate('auto.k0303'), kind: 'select' as const, options: envTypeOptions },
              { key: 'web_url', label: fieldLabel('webUrl'), placeholder: 'https://web.env.test' },
              { key: 'api_base_url', label: fieldLabel('apiBaseUrl'), placeholder: 'https://api.env.test' }
            ]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'environment:edit')}
            statusOptions={[
              hasPermission(currentUser, 'environment:edit') ? { value: 'ENABLED', label: translate('auto.k0304'), icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'environment:disable') ? { value: 'DISABLED', label: translate('auto.k0305'), icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchEnvironment}
            updateDetail={(key, draft) => updateEnvironment(key, {
              name: draft.name, env_type: draft.env_type, web_url: draft.web_url, api_base_url: draft.api_base_url
            })}
            changeStatus={changeEnvironmentStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ name: d.name, api_base_url: d.endpoint })}
            detailRows={(d) => [[translate('auto.k0298'), d.cluster], [fieldLabel('endpoint'), d.endpoint], [translate('auto.k0182'), d.status]]}
            onChanged={props.onRefresh}
          />
          <EnvironmentConnectivityPanel
            resources={data.environments.map((e) => e.name)}
            signedIn={signedIn}
            canRun={hasPermission(currentUser, 'environment:edit')}
            onChanged={props.onRefresh}
          />
          <ScopedRolePanel
            title={translate('auto.k0306')}
            resourceLabel={translate('auto.k0215')}
            emptyLabel={translate('auto.k0300')}
            resources={data.environments.map((e) => e.name)}
            roles={['Developer', 'Tester']}
            signedIn={signedIn}
            canManage={hasPermission(currentUser, 'environment:user_manage')}
            fetchMembers={fetchEnvironmentUsers}
            addMember={addEnvironmentUser}
            removeMember={removeEnvironmentUser}
            onChanged={props.onRefresh}
          />
        </>
      }
    />
  );
}
