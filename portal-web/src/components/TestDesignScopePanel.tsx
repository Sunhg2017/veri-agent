import { translate } from '../platform/i18n';
export function TestDesignScopePanel(props: { selectedRequirementTitles: string[] }) {
  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">{translate('auto.k0196')}</h2>
          <p className="panel-desc">{translate('auto.k1598')}</p>
        </div>
      </div>
      <div className="panel-body compact">
        {props.selectedRequirementTitles.length ? (
          <div className="test-design-scope">
            {props.selectedRequirementTitles.map((title) => <span className="badge badge-info" key={title}>{title}</span>)}
          </div>
        ) : (
          <div className="notice info">{translate('auto.k1599')}</div>
        )}
      </div>
    </section>
  );
}
