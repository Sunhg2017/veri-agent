-- Add explicit cleanup indexes for session expiration and revocation scans.

create index if not exists idx_iam_session_revoked_at
    on iam_session (revoked_at)
    where revoked_at is not null;

create index if not exists idx_iam_session_cleanup
    on iam_session (expires_at, revoked_at);
