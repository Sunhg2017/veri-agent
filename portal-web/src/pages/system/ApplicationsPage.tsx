import { AppWindow, Power } from 'lucide-react';
import {
  addApplicationOwner,
  changeApplicationStatus,
  fetchApplication,
  fetchApplicationOwners,
  removeApplicationOwner,
  updateApplication,
  type ApplicationView
} from '../../api/management';
import { hasPermission } from '../../permissions';
import { dictionaryLabel, fieldLabel } from '../../platform/dictionaries';
import { translate } from '../../platform/i18n';
import {
  DataSection,
  ResourceLifecyclePanel,
  ScopedRolePanel,
  StatusBadge,
  optionalBoolean,
  type ManagementPageProps,
  type StatusOption
} from '../../components/management/shared';

/** 应用管理页：应用列表 + 生命周期 + 负责人侧栏 */
export function ApplicationsPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  const sensitivityOptions = [
    { value: '', label: translate('auto.k0265') }, { value: 'PUBLIC', label: dictionaryLabel('PUBLIC') },
    { value: 'INTERNAL', label: dictionaryLabel('INTERNAL') }, { value: 'CONFIDENTIAL', label: dictionaryLabel('CONFIDENTIAL') },
    { value: 'STRICT', label: dictionaryLabel('STRICT') }
  ];
  const publicModelOptions = [
    { value: '', label: translate('auto.k0265') }, { value: 'true', label: translate('auto.k0266') }, { value: 'false', label: translate('auto.k0267') }
  ];
  const appTypeOptions = [
    { value: '', label: translate('auto.k0265') }, { value: 'Web', label: dictionaryLabel('Web') }, { value: 'Backend', label: dictionaryLabel('Backend') },
    { value: 'Frontend', label: dictionaryLabel('Frontend') }, { value: 'Mobile', label: dictionaryLabel('Mobile') }, { value: 'Service', label: dictionaryLabel('Service') }, { value: 'API', label: dictionaryLabel('API') }
  ];
  return (
    <DataSection
      eyebrow={translate('auto.k0029')}
      title={translate('auto.k0283')}
      icon={AppWindow}
      action={translate('auto.k0284')}
      createResource="applications"
      columns={[translate('auto.k0285'), translate('auto.k0286'), translate('auto.k0287'), translate('auto.k0178'), translate('auto.k0182')]}
      rows={data.applications.map((item: ApplicationView) => [item.name, item.type, item.owner, item.version, <StatusBadge key={item.name} status={item.status} />])}
      loadState={loadState}
      signedIn={signedIn}
      canCreate={hasPermission(currentUser, 'application:create')}
      onCreate={props.onCreate}
      onRefresh={props.onRefresh}
      sidePanel={
        <>
          <ResourceLifecyclePanel<ApplicationView>
            title={translate('auto.k0288')}
            resourceLabel={translate('auto.k0285')}
            emptyLabel={translate('auto.k0289')}
            resources={data.applications.map((a) => a.name)}
            fields={[
              { key: 'name', label: translate('auto.k0290'), placeholder: translate('auto.k0291') },
              { key: 'app_type', label: translate('auto.k0292'), kind: 'select' as const, options: appTypeOptions },
              { key: 'default_web_url', label: fieldLabel('defaultWebUrl'), placeholder: 'https://web.example.test' },
              { key: 'default_api_base_url', label: fieldLabel('defaultApiBaseUrl'), placeholder: 'https://api.example.test' },
              { key: 'sensitivity_level', label: translate('auto.k0276'), kind: 'select' as const, options: sensitivityOptions },
              { key: 'allow_public_model', label: translate('auto.k0277'), kind: 'public-model' as const, options: publicModelOptions }
            ]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'application:edit')}
            statusOptions={[
              hasPermission(currentUser, 'application:edit') ? { value: 'ENABLED', label: translate('auto.k0293'), icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'application:disable') ? { value: 'DISABLED', label: translate('auto.k0294'), icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchApplication}
            updateDetail={(key, draft) => updateApplication(key, {
              name: draft.name, app_type: draft.app_type, default_web_url: draft.default_web_url,
              default_api_base_url: draft.default_api_base_url, sensitivity_level: draft.sensitivity_level,
              allow_public_model: optionalBoolean(draft.allow_public_model)
            })}
            changeStatus={changeApplicationStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ name: d.name, app_type: d.type })}
            detailRows={(d) => [[translate('auto.k0286'), d.type], [translate('auto.k0287'), d.owner], [translate('auto.k0178'), d.version], [translate('auto.k0182'), d.status]]}
            onChanged={props.onRefresh}
          />
          <ScopedRolePanel
            title={translate('auto.k0295')}
            resourceLabel={translate('auto.k0285')}
            emptyLabel={translate('auto.k0289')}
            resources={data.applications.map((a) => a.name)}
            roles={['AppOwner']}
            signedIn={signedIn}
            canManage={hasPermission(currentUser, 'application:owner_manage')}
            fetchMembers={fetchApplicationOwners}
            addMember={(rk, un) => addApplicationOwner(rk, un)}
            removeMember={removeApplicationOwner}
            onChanged={props.onRefresh}
          />
        </>
      }
    />
  );
}
