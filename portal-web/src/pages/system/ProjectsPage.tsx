import { Archive, DatabaseZap, Pencil, Power } from 'lucide-react';
import {
  addProjectMember,
  changeProjectStatus,
  fetchProject,
  fetchProjectMembers,
  removeProjectMember,
  updateProject,
  type ProjectView
} from '../../api/management';
import { hasPermission } from '../../permissions';
import { dictionaryLabel } from '../../platform/dictionaries';
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

/** 项目管理页：项目列表 + 生命周期 + 成员角色侧栏 */
export function ProjectsPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser } = props;
  const sensitivityOptions = [
    { value: '', label: translate('auto.k0265') }, { value: 'PUBLIC', label: dictionaryLabel('PUBLIC') },
    { value: 'INTERNAL', label: dictionaryLabel('INTERNAL') }, { value: 'CONFIDENTIAL', label: dictionaryLabel('CONFIDENTIAL') },
    { value: 'STRICT', label: dictionaryLabel('STRICT') }
  ];
  const publicModelOptions = [
    { value: '', label: translate('auto.k0265') }, { value: 'true', label: translate('auto.k0266') }, { value: 'false', label: translate('auto.k0267') }
  ];
  return (
    <DataSection
      eyebrow={translate('auto.k0268')}
      title={translate('auto.k0027')}
      icon={DatabaseZap}
      action={translate('auto.k0269')}
      createResource="projects"
      columns={[translate('auto.k0176'), translate('auto.k0270'), translate('auto.k0234'), translate('auto.k0271'), translate('auto.k0182')]}
      rows={data.projects.map((item: ProjectView) => [item.name, item.department, item.owner, item.apps, <StatusBadge key={item.name} status={item.status} />])}
      loadState={loadState}
      signedIn={signedIn}
      canCreate={hasPermission(currentUser, 'project:create')}
      onCreate={props.onCreate}
      onRefresh={props.onRefresh}
      sidePanel={
        <>
          <ResourceLifecyclePanel<ProjectView>
            title={translate('auto.k0272')}
            resourceLabel={translate('auto.k0176')}
            emptyLabel={translate('auto.k0273')}
            resources={data.projects.map((p) => p.name)}
            fields={[
              { key: 'name', label: translate('auto.k0274'), placeholder: translate('auto.k0275') },
              { key: 'sensitivity_level', label: translate('auto.k0276'), kind: 'select' as const, options: sensitivityOptions },
              { key: 'allow_public_model', label: translate('auto.k0277'), kind: 'public-model' as const, options: publicModelOptions }
            ]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'project:edit')}
            statusOptions={[
              hasPermission(currentUser, 'project:edit') ? { value: 'ACTIVE', label: translate('auto.k0278'), icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'project:edit') ? { value: 'PREPARING', label: translate('auto.k0279'), icon: Pencil } as StatusOption : undefined,
              hasPermission(currentUser, 'project:archive') ? { value: 'ARCHIVED', label: translate('auto.k0280'), icon: Archive } as StatusOption : undefined,
              hasPermission(currentUser, 'project:disable') ? { value: 'DISABLED', label: translate('auto.k0281'), icon: Power } as StatusOption : undefined
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchProject}
            updateDetail={(key, draft) => updateProject(key, {
              name: draft.name,
              sensitivity_level: draft.sensitivity_level,
              allow_public_model: optionalBoolean(draft.allow_public_model)
            })}
            changeStatus={changeProjectStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ name: d.name })}
            detailRows={(d) => [[translate('auto.k0270'), d.department], [translate('auto.k0234'), d.owner], [translate('auto.k0271'), d.apps], [translate('auto.k0182'), d.status]]}
            onChanged={props.onRefresh}
          />
          <ScopedRolePanel
            title={translate('auto.k0282')}
            resourceLabel={translate('auto.k0176')}
            emptyLabel={translate('auto.k0273')}
            resources={data.projects.map((p) => p.name)}
            roles={['ProjectOwner', 'Tester', 'Developer', 'Auditor']}
            signedIn={signedIn}
            canManage={hasPermission(currentUser, 'project:member_manage')}
            fetchMembers={fetchProjectMembers}
            addMember={addProjectMember}
            removeMember={removeProjectMember}
            onChanged={props.onRefresh}
          />
        </>
      }
    />
  );
}
