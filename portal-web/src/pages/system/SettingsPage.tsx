import { Power, Settings } from 'lucide-react';
import {
  changeSettingStatus,
  fetchSetting,
  updateSetting,
  type SettingView
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

/** 系统设置页：配置项列表 + 编辑侧栏 */
export function SettingsPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  return (
    <DataSection
      eyebrow={translate('auto.k0319')}
      title={translate('auto.k0037')}
      icon={Settings}
      action={translate('auto.k0170')}
      columns={[translate('auto.k0320'), translate('auto.k0321'), translate('auto.k0322'), translate('auto.k0182')]}
      rows={data.settings.map((item: SettingView) => [item.name, item.key, item.value, <StatusBadge key={item.key} status={item.status} />])}
      loadState={loadState}
      signedIn={signedIn}
      onRefresh={props.onRefresh}
      sidePanel={
        <ResourceLifecyclePanel<SettingView>
          title={translate('auto.k0323')}
          resourceLabel={translate('auto.k0324')}
          emptyLabel={translate('auto.k0325')}
          resources={data.settings.map((s) => s.key)}
          fields={[{ key: 'value', label: translate('auto.k0322'), placeholder: translate('auto.k0326') }]}
          signedIn={signedIn}
          canEdit={hasPermission(currentUser, 'config:edit')}
          statusOptions={[
            hasPermission(currentUser, 'config:edit') ? { value: 'ENABLED', label: translate('auto.k0251'), icon: Power } as StatusOption : undefined,
            hasPermission(currentUser, 'config:edit') ? { value: 'DISABLED', label: translate('auto.k0253'), icon: Power } as StatusOption : undefined,
          ].filter(Boolean) as StatusOption[]}
          fetchDetail={fetchSetting}
          updateDetail={(key, draft) => updateSetting(key, { value: draft.value })}
          changeStatus={changeSettingStatus}
          detailTitle={(d) => d.name}
          draftFromDetail={(d) => ({ value: d.value })}
          detailRows={(d) => [[translate('auto.k0321'), d.key], [translate('auto.k0322'), d.value], [translate('auto.k0182'), d.status]]}
          onChanged={props.onRefresh}
        />
      }
    />
  );
}
