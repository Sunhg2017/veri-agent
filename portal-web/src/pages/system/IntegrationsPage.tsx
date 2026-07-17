import { Link2, Power } from 'lucide-react';
import {
  changeIntegrationStatus,
  fetchIntegration,
  updateIntegration,
  type IntegrationView
} from '../../api/management';
import { hasPermission } from '../../permissions';
import { translate } from '../../platform/i18n';
import {
  DataSection,
  ResourceLifecyclePanel,
  StatusBadge,
  type ManagementPageProps,
  type StatusOption
} from '../../components/management/shared';

/** 集成管理页：集成列表 + 生命周期侧栏 */
export function IntegrationsPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  return (
    <DataSection
      eyebrow={translate('auto.k0307')}
      title={translate('auto.k0033')}
      icon={Link2}
      action={translate('auto.k0308')}
      createResource="integrations"
      columns={[translate('auto.k0309'), translate('auto.k0310'), translate('auto.k0196'), translate('auto.k0182')]}
      rows={data.integrations.map((item: IntegrationView) => [item.name, item.category, item.scope, <StatusBadge key={item.name} status={item.status} />])}
      loadState={loadState}
      signedIn={signedIn}
      canCreate={hasPermission(currentUser, 'config:edit')}
      onCreate={props.onCreate}
      onRefresh={props.onRefresh}
      sidePanel={
        <ResourceLifecyclePanel<IntegrationView>
          title={translate('auto.k0311')}
          resourceLabel={translate('auto.k0309')}
          emptyLabel={translate('auto.k0312')}
          resources={data.integrations.map((i) => i.name)}
          fields={[
            { key: 'name', label: translate('auto.k0313'), placeholder: translate('auto.k0314') },
            { key: 'category', label: translate('auto.k0310'), placeholder: translate('auto.k0315') },
            { key: 'scope', label: translate('auto.k0196'), placeholder: translate('auto.k0316') }
          ]}
          signedIn={signedIn}
          canEdit={hasPermission(currentUser, 'config:edit')}
          statusOptions={[
            hasPermission(currentUser, 'config:edit') ? { value: 'ENABLED', label: translate('auto.k0317'), icon: Power } as StatusOption : undefined,
            hasPermission(currentUser, 'config:edit') ? { value: 'DISABLED', label: translate('auto.k0318'), icon: Power } as StatusOption : undefined,
          ].filter(Boolean) as StatusOption[]}
          fetchDetail={fetchIntegration}
          updateDetail={(key, draft) => updateIntegration(key, {
            name: draft.name, category: draft.category, scope: draft.scope
          })}
          changeStatus={changeIntegrationStatus}
          detailTitle={(d) => d.name}
          draftFromDetail={(d) => ({ name: d.name, category: d.category, scope: d.scope })}
          detailRows={(d) => [[translate('auto.k0310'), d.category], [translate('auto.k0196'), d.scope], [translate('auto.k0182'), d.status]]}
          onChanged={props.onRefresh}
        />
      }
    />
  );
}
