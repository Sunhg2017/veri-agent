-- Align WP10 report export manifest constraint with the actual platform-supported export formats.

alter table report_export_manifest
    drop constraint if exists ck_report_export_manifest_type;

alter table report_export_manifest
    add constraint ck_report_export_manifest_type
        check (export_type in ('JSON','MARKDOWN','HTML','PDF','WORD','EXCEL'));

comment on column report_export_manifest.export_type is
    'Export type JSON, MARKDOWN, HTML, PDF, WORD or EXCEL.';
