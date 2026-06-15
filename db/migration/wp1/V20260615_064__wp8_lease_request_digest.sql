alter table test_account_lease
    add column if not exists request_digest varchar(64);

alter table test_account_lease
    drop constraint if exists ck_test_account_lease_request_digest;

alter table test_account_lease
    add constraint ck_test_account_lease_request_digest
        check (request_digest is null or request_digest ~ '^[0-9a-f]{64}$');

comment on column test_account_lease.request_digest is 'SHA-256 digest of the sanitized lease acquisition request for idempotent replay validation.';
