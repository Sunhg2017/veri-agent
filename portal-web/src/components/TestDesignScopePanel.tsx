export function TestDesignScopePanel(props: { selectedRequirementTitles: string[] }) {
  return (
    <section className="panel">
      <div className="panel-header compact">
        <div>
          <h2 className="panel-title">范围</h2>
          <p className="panel-desc">本次生成输入。</p>
        </div>
      </div>
      <div className="panel-body compact">
        {props.selectedRequirementTitles.length ? (
          <div className="test-design-scope">
            {props.selectedRequirementTitles.map((title) => <span className="badge badge-info" key={title}>{title}</span>)}
          </div>
        ) : (
          <div className="notice info">尚未选择需求</div>
        )}
      </div>
    </section>
  );
}
