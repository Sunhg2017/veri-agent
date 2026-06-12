-- WP6 runner admission error-code extension.

alter table api_automation_run
    drop constraint if exists ck_api_automation_run_error_code;

alter table api_automation_run
    add constraint ck_api_automation_run_error_code check (
        error_code is null
        or error_code in (
            'RUNNER_DISABLED',
            'RUNNER_TARGET_BLOCKED',
            'RUNNER_CASE_LIMIT_EXCEEDED',
            'RUNNER_BUNDLE_NOT_APPROVED',
            'RUNNER_CASE_NOT_FOUND',
            'SCRIPT_STATIC_CHECK_FAILED',
            'RUNNER_FAILED',
            'RUNNER_TIMEOUT',
            'RUNNER_CANCELED',
            'RUNNER_ARTIFACT_TOO_LARGE'
        )
    );
