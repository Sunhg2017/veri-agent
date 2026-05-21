-- WP3 page prototype source version.
-- Keeps external prototype version mapping separate from the page asset's internal version column.

alter table asset_page
    add column if not exists source_version varchar(128);

comment on column asset_page.source_version is 'External prototype source version, node version, or import batch version for Figma/Lanhu/Axure/manual page assets.';
