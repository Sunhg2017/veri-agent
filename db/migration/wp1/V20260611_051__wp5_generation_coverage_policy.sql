-- WP5 productized generation and coverage policy configuration.
-- Extends generation templates with controlled strategy fields used by non-development operators.

alter table test_design_template
    add column if not exists generation_strategy varchar(32) not null default 'BALANCED',
    add column if not exists coverage_strategy varchar(32) not null default 'DEFAULT_ORDER';

alter table test_design_template
    drop constraint if exists ck_test_design_template_generation_strategy,
    add constraint ck_test_design_template_generation_strategy
        check (generation_strategy in ('BALANCED', 'RISK_FIRST', 'COMPLIANCE', 'EXPLORATORY'));

alter table test_design_template
    drop constraint if exists ck_test_design_template_coverage_strategy,
    add constraint ck_test_design_template_coverage_strategy
        check (coverage_strategy in ('DEFAULT_ORDER', 'SMOKE_FIRST', 'RISK_FIRST', 'REGRESSION_HEAVY', 'SECURITY_PERMISSION'));

create index if not exists idx_test_design_template_strategy
    on test_design_template (generation_strategy, coverage_strategy, enabled, updated_at desc);

comment on column test_design_template.generation_strategy is 'Productized generation strategy: BALANCED, RISK_FIRST, COMPLIANCE or EXPLORATORY.';
comment on column test_design_template.coverage_strategy is 'Productized coverage ordering strategy used when resolving template coverage defaults.';
