create index if not exists idx_execution_node_run_plan_node
    on execution_node_run (plan_node_id);

comment on index idx_execution_node_run_plan_node is 'WP9 node run lookup index for plan node joins and retry aggregation.';
