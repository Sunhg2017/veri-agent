import { GitBranch, Power } from 'lucide-react';
import {
  changeDepartmentStatus,
  fetchDepartment,
  updateDepartment,
  type DepartmentView
} from '../../api/management';
import { hasPermission } from '../../permissions';
import { translate } from '../../platform/i18n';
import {
  DataSection,
  ResourceLifecyclePanel,
  type ManagementPageProps,
  type StatusOption
} from '../../components/management/shared';

/** 组织管理页：部门列表 + 生命周期侧栏 */
export function OrganizationsPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  return (
    <DataSection
      eyebrow={translate('auto.k0229')}
      title={translate('auto.k0230')}
      icon={GitBranch}
      action={translate('auto.k0231')}
      createResource="departments"
      columns={[translate('auto.k0232'), translate('auto.k0233'), translate('auto.k0234'), translate('auto.k0235'), translate('auto.k0182')]}
      rows={data.departments.map((d: DepartmentView) => [d.name, d.parent, d.lead, d.members, d.status])}
      loadState={loadState}
      signedIn={signedIn}
      canCreate={hasPermission(currentUser, 'department:create')}
      onCreate={props.onCreate}
      onRefresh={props.onRefresh}
      sidePanel={
        <ResourceLifecyclePanel<DepartmentView>
          title={translate('auto.k0236')}
          resourceLabel={translate('auto.k0232')}
          emptyLabel={translate('auto.k0237')}
          resources={data.departments.map((d) => d.name)}
          fields={[{ key: 'name', label: translate('auto.k0238'), placeholder: translate('auto.k0239') }]}
          signedIn={signedIn}
          canEdit={hasPermission(currentUser, 'department:edit')}
          statusOptions={[
            hasPermission(currentUser, 'department:enable') ? { value: 'ENABLED', label: translate('auto.k0240'), icon: Power } as StatusOption : undefined,
            hasPermission(currentUser, 'department:disable') ? { value: 'DISABLED', label: translate('auto.k0241'), icon: Power } as StatusOption : undefined,
          ].filter(Boolean) as StatusOption[]}
          fetchDetail={fetchDepartment}
          updateDetail={(key, draft) => updateDepartment(key, { name: draft.name })}
          changeStatus={changeDepartmentStatus}
          detailTitle={(d) => d.name}
          draftFromDetail={(d) => ({ name: d.name })}
          detailRows={(d) => [[translate('auto.k0233'), d.parent], [translate('auto.k0234'), d.lead], [translate('auto.k0242'), d.members], [translate('auto.k0182'), d.status]]}
          onChanged={props.onRefresh}
        />
      }
    />
  );
}
