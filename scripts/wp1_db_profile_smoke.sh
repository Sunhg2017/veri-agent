#!/usr/bin/env bash
set -euo pipefail

API_BASE="${WP1_SMOKE_API_BASE:-http://127.0.0.1:8080/api/v1}"
BOOTSTRAP_TOKEN="${WP1_BOOTSTRAP_TOKEN:-local-init-token}"
ADMIN_USERNAME="${WP1_SMOKE_ADMIN_USERNAME:-admin_user}"
ADMIN_PASSWORD="${WP1_SMOKE_ADMIN_PASSWORD:-PlainPassword123}"
ADMIN_DISPLAY_NAME="${WP1_SMOKE_ADMIN_DISPLAY_NAME:-平台管理员}"
ADMIN_EMAIL="${WP1_SMOKE_ADMIN_EMAIL:-admin@example.com}"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP1 db profile smoke test" >&2
    exit 127
  fi
}

post_json() {
  local path="$1"
  local body="$2"
  shift 2
  curl -fsS -X POST "$API_BASE$path" \
    -H 'Content-Type: application/json' \
    "$@" \
    -d "$body"
}

patch_json() {
  local path="$1"
  local body="$2"
  shift 2
  curl -fsS -X PATCH "$API_BASE$path" \
    -H 'Content-Type: application/json' \
    "$@" \
    -d "$body"
}

get_json() {
  local path="$1"
  shift
  curl -fsS "$API_BASE$path" "$@"
}

http_status() {
  local method="$1"
  local path="$2"
  shift 2
  curl -sS -o /tmp/wp1-smoke-response.json -w '%{http_code}' \
    -X "$method" "$API_BASE$path" "$@"
}

bootstrap_admin_if_needed() {
  local status
  status="$(http_status POST /auth/login \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
  if [[ "$status" == "200" ]]; then
    return 0
  fi

  local bootstrap_status
  bootstrap_status="$(http_status POST /bootstrap/super-admin \
    -H 'Content-Type: application/json' \
    -d "{
      \"bootstrap_token\":\"$BOOTSTRAP_TOKEN\",
      \"username\":\"$ADMIN_USERNAME\",
      \"password\":\"$ADMIN_PASSWORD\",
      \"display_name\":\"$ADMIN_DISPLAY_NAME\",
      \"email\":\"$ADMIN_EMAIL\"
    }")"

  if [[ "$bootstrap_status" != "200" && "$bootstrap_status" != "409" ]]; then
    echo "SuperAdmin bootstrap failed with HTTP $bootstrap_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi
}

extract_data_field() {
  local json="$1"
  local field="$2"
  printf '%s' "$json" | jq -r ".data.$field"
}

assert_token_field() {
  local value="$1"
  local field="$2"
  if [[ -z "$value" || "$value" == "null" ]]; then
    echo "missing token field: $field" >&2
    exit 1
  fi
}

assert_page_contains_name() {
  local page_json="$1"
  local expected_name="$2"
  local label="$3"
  if ! printf '%s' "$page_json" | jq -e \
    --arg expected_name "$expected_name" \
    '.data.items[] | select(.name == $expected_name)' >/dev/null; then
    echo "$label page did not include $expected_name" >&2
    echo "$page_json" >&2
    exit 1
  fi
}

assert_audit_event() {
  local access_token="$1"
  local search="$2"
  local action="$3"
  local resource_type="$4"
  local target="$5"
  local audit_page
  audit_page="$(curl -fsS -G "$API_BASE/management/audit-logs" \
    -H "Authorization: Bearer $access_token" \
    --data-urlencode "search=$search" \
    --data-urlencode "action=$action" \
    --data-urlencode "resource_type=$resource_type" \
    --data-urlencode "result=SUCCESS" \
    --data-urlencode "start_time=2000-01-01T00:00:00Z" \
    --data-urlencode "end_time=2999-01-01T00:00:00Z")"
  if ! printf '%s' "$audit_page" | jq -e \
    --arg action "$action" \
    --arg target "$target" \
    '.data.items[] | select(.action == $action and .target == $target and .result == "成功")' >/dev/null; then
    echo "audit log was not found for $resource_type/$target" >&2
    echo "$audit_page" >&2
    exit 1
  fi
}

main() {
  require_tool curl
  require_tool jq

  bootstrap_admin_if_needed

  local login access refresh session
  login="$(post_json /auth/login "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
  access="$(extract_data_field "$login" access_token)"
  refresh="$(extract_data_field "$login" refresh_token)"
  session="$(extract_data_field "$login" session_id)"
  assert_token_field "$access" access_token
  assert_token_field "$refresh" refresh_token
  assert_token_field "$session" session_id

  local me_status
  me_status="$(http_status GET /auth/me -H "Authorization: Bearer $access")"
  if [[ "$me_status" != "200" ]]; then
    echo "auth/me failed before refresh with HTTP $me_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi

  local refreshed access2 refresh2 session2
  refreshed="$(post_json /auth/refresh "{\"refresh_token\":\"$refresh\"}")"
  access2="$(extract_data_field "$refreshed" access_token)"
  refresh2="$(extract_data_field "$refreshed" refresh_token)"
  session2="$(extract_data_field "$refreshed" session_id)"
  assert_token_field "$access2" refreshed_access_token
  assert_token_field "$refresh2" refreshed_refresh_token
  assert_token_field "$session2" refreshed_session_id

  local old_access_status new_access_status
  old_access_status="$(http_status GET /auth/me -H "Authorization: Bearer $access")"
  new_access_status="$(http_status GET /auth/me -H "Authorization: Bearer $access2")"
  if [[ "$old_access_status" != "401" && "$old_access_status" != "403" ]]; then
    echo "old access token still accepted after refresh: HTTP $old_access_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi
  if [[ "$new_access_status" != "200" ]]; then
    echo "new access token rejected after refresh: HTTP $new_access_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi

  local suffix username department_name project_code project_name application_code application_name environment_code environment_name integration_code integration_name
  suffix="$(date +%s)_$RANDOM"
  username="tester_$suffix"
  post_json /management/users "{\"name\":\"$username\"}" -H "Authorization: Bearer $access2" >/tmp/wp1-smoke-create-user.json
  local user_display_name user_email user_update user_detail
  user_display_name="Smoke User $suffix"
  user_email="$username@example.test"
  user_update="$(patch_json "/management/users/$username" \
    "$(jq -nc --arg displayName "$user_display_name" --arg email "$user_email" '{display_name:$displayName,email:$email}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$user_update" | jq -e --arg displayName "$user_display_name" --arg email "$user_email" \
    '.data.display_name == $displayName and .data.email == $email' >/dev/null; then
    echo "user update response was not expected" >&2
    echo "$user_update" >&2
    exit 1
  fi
  user_detail="$(get_json "/management/users/$username" -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$user_detail" | jq -e --arg displayName "$user_display_name" --arg email "$user_email" \
    '.data.display_name == $displayName and .data.email == $email' >/dev/null; then
    echo "user detail response was not expected after update" >&2
    echo "$user_detail" >&2
    exit 1
  fi
  local users_page roles_page
  users_page="$(curl -fsS "$API_BASE/management/users?page=1&page_size=5&search=$username" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$users_page" | jq -e '.data.items[0].username == "'"$username"'" and .data.total >= 1' >/dev/null; then
    echo "management users page did not include created user" >&2
    echo "$users_page" >&2
    exit 1
  fi

  roles_page="$(curl -fsS "$API_BASE/management/roles?page=1&page_size=20" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$roles_page" | jq -e '.data.items[] | select(.code == "PlatformAdmin")' >/dev/null; then
    echo "management roles page did not include PlatformAdmin" >&2
    echo "$roles_page" >&2
    exit 1
  fi

  department_name="WP1 Smoke Dept $suffix"
  project_code="prj_$suffix"
  project_name="WP1 Smoke Project $suffix"
  application_code="app_$suffix"
  application_name="WP1 Smoke App $suffix"
  environment_code="env_$suffix"
  environment_name="WP1 Smoke Env $suffix"
  integration_code="integration_$suffix"
  integration_name="WP1 Smoke Integration $suffix"

  post_json /management/departments "{\"name\":\"$department_name\"}" \
    -H "Authorization: Bearer $access2" >/tmp/wp1-smoke-create-department.json
  if ! jq -e --arg name "$department_name" '.data.name == $name and .data.status == "同步正常"' \
    /tmp/wp1-smoke-create-department.json >/dev/null; then
    echo "department create response was not expected" >&2
    cat /tmp/wp1-smoke-create-department.json >&2
    exit 1
  fi

  local department_detail department_updated_name department_updated department_disabled department_enabled
  department_detail="$(get_json "/management/departments/$department_name" -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$department_detail" | jq -e --arg name "$department_name" \
    '.data.name == $name and .data.lead != null' >/dev/null; then
    echo "department detail response was not expected" >&2
    echo "$department_detail" >&2
    exit 1
  fi

  department_updated_name="$department_name Updated"
  department_updated="$(patch_json "/management/departments/$department_name" \
    "$(jq -nc --arg name "$department_updated_name" '{name:$name}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$department_updated" | jq -e --arg name "$department_updated_name" \
    '.data.name == $name' >/dev/null; then
    echo "department update response was not expected" >&2
    echo "$department_updated" >&2
    exit 1
  fi
  department_name="$department_updated_name"

  department_disabled="$(patch_json "/management/departments/$department_name/status" '{"status":"DISABLED"}' -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$department_disabled" | jq -e '.data.status == "已停用"' >/dev/null; then
    echo "department disabled status response was not expected" >&2
    echo "$department_disabled" >&2
    exit 1
  fi

  department_enabled="$(patch_json "/management/departments/$department_name/status" '{"status":"ENABLED"}' -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$department_enabled" | jq -e '.data.status == "同步正常"' >/dev/null; then
    echo "department enabled status response was not expected" >&2
    echo "$department_enabled" >&2
    exit 1
  fi

  local project_payload application_payload environment_payload
  project_payload="$(jq -nc \
    --arg code "$project_code" \
    --arg name "$project_name" \
    '{code:$code,name:$name,sensitivity_level:"CONFIDENTIAL",allow_public_model:false}')"
  application_payload="$(jq -nc \
    --arg code "$application_code" \
    --arg name "$application_name" \
    --arg project "$project_code" \
    --arg apiBaseUrl "https://api.$application_code.example.test" \
    '{code:$code,name:$name,project:$project,app_type:"Web",default_api_base_url:$apiBaseUrl,sensitivity_level:"STRICT",allow_public_model:false}')"
  environment_payload="$(jq -nc \
    --arg code "$environment_code" \
    --arg name "$environment_name" \
    --arg project "$project_code" \
    --arg application "$application_code" \
    --arg webUrl "https://web.$environment_code.example.test" \
    --arg apiBaseUrl "https://api.$environment_code.example.test" \
    '{code:$code,name:$name,project:$project,application:$application,scope_type:"APPLICATION",env_type:"STAGING",web_url:$webUrl,api_base_url:$apiBaseUrl}')"

  local project_response application_response environment_response
  project_response="$(post_json /management/projects "$project_payload" -H "Authorization: Bearer $access2")"
  printf '%s' "$project_response" >/tmp/wp1-smoke-create-project.json
  if ! printf '%s' "$project_response" | jq -e \
    --arg name "$project_name" \
    '.data.name == $name and .data.status == "规划中"' >/dev/null; then
    echo "formal project create response was not expected" >&2
    echo "$project_response" >&2
    exit 1
  fi

  application_response="$(post_json /management/applications "$application_payload" -H "Authorization: Bearer $access2")"
  printf '%s' "$application_response" >/tmp/wp1-smoke-create-application.json
  if ! printf '%s' "$application_response" | jq -e \
    --arg name "$application_name" \
    --arg owner "$project_name" \
    '.data.name == $name and .data.type == "Web" and .data.owner == $owner' >/dev/null; then
    echo "formal application create response was not expected" >&2
    echo "$application_response" >&2
    exit 1
  fi

  environment_response="$(post_json /management/environments "$environment_payload" -H "Authorization: Bearer $access2")"
  printf '%s' "$environment_response" >/tmp/wp1-smoke-create-environment.json
  if ! printf '%s' "$environment_response" | jq -e \
    --arg name "$environment_name" \
    --arg cluster "$project_name" \
    --arg endpoint "https://api.$environment_code.example.test" \
    '.data.name == $name and .data.cluster == $cluster and .data.endpoint == $endpoint' >/dev/null; then
    echo "formal environment create response was not expected" >&2
    echo "$environment_response" >&2
    exit 1
  fi

  local departments_page projects_page applications_page environments_page
  departments_page="$(curl -fsS -G "$API_BASE/management/departments" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "page=1" \
    --data-urlencode "page_size=5" \
    --data-urlencode "search=$department_name")"
  projects_page="$(curl -fsS -G "$API_BASE/management/projects" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "page=1" \
    --data-urlencode "page_size=5" \
    --data-urlencode "search=$project_name")"
  applications_page="$(curl -fsS -G "$API_BASE/management/applications" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "page=1" \
    --data-urlencode "page_size=5" \
    --data-urlencode "search=$application_name")"
  environments_page="$(curl -fsS -G "$API_BASE/management/environments" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "page=1" \
    --data-urlencode "page_size=5" \
    --data-urlencode "search=$environment_name")"
  assert_page_contains_name "$departments_page" "$department_name" "department"
  assert_page_contains_name "$projects_page" "$project_name" "project"
  assert_page_contains_name "$applications_page" "$application_name" "application"
  assert_page_contains_name "$environments_page" "$environment_name" "environment"

  local integration_response integration_detail integration_update integration_disabled integrations_page integration_renamed
  integration_response="$(post_json /management/integrations \
    "$(jq -nc --arg code "$integration_code" --arg name "$integration_name" '{code:$code,name:$name,category:"通知/审批",scope:"平台级"}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$integration_response" | jq -e \
    --arg key "$integration_code" \
    --arg name "$integration_name" \
    '.data.key == $key and .data.name == $name and .data.status == "已启用"' >/dev/null; then
    echo "integration create response was not expected" >&2
    echo "$integration_response" >&2
    exit 1
  fi
  integration_detail="$(get_json "/management/integrations/$integration_code" -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$integration_detail" | jq -e --arg name "$integration_name" '.data.name == $name' >/dev/null; then
    echo "integration detail response was not expected" >&2
    echo "$integration_detail" >&2
    exit 1
  fi
  integration_renamed="$integration_name Updated"
  integration_update="$(patch_json "/management/integrations/$integration_code" \
    "$(jq -nc --arg name "$integration_renamed" '{name:$name,category:"缺陷系统",scope:"项目级"}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$integration_update" | jq -e \
    --arg name "$integration_renamed" \
    '.data.name == $name and .data.category == "缺陷系统" and .data.scope == "项目级"' >/dev/null; then
    echo "integration update response was not expected" >&2
    echo "$integration_update" >&2
    exit 1
  fi
  integration_disabled="$(patch_json "/management/integrations/$integration_code/status" '{"status":"DISABLED"}' -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$integration_disabled" | jq -e '.data.status == "已停用"' >/dev/null; then
    echo "integration disabled status response was not expected" >&2
    echo "$integration_disabled" >&2
    exit 1
  fi
  integrations_page="$(curl -fsS -G "$API_BASE/management/integrations" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "search=$integration_renamed")"
  assert_page_contains_name "$integrations_page" "$integration_renamed" "integration"

  local setting_code setting_name setting_updated setting_response setting_detail setting_update setting_disabled settings_page sensitive_setting_status
  setting_code="account.failed_login_limit_$suffix"
  setting_name="WP1 Smoke Failed Login Limit $suffix"
  setting_updated="$setting_name Updated"
  setting_response="$(post_json /management/settings \
    "$(jq -nc --arg key "$setting_code" --arg name "$setting_name" '{key:$key,name:$name,value:"5",scope_type:"SYSTEM"}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$setting_response" | jq -e \
    --arg key "$setting_code" \
    --arg name "$setting_name" \
    '.data.key == $key and .data.name == $name and .data.value == "5" and .data.status == "已启用"' >/dev/null; then
    echo "setting create response was not expected" >&2
    echo "$setting_response" >&2
    exit 1
  fi
  setting_detail="$(get_json "/management/settings/$setting_code" -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$setting_detail" | jq -e --arg name "$setting_name" '.data.name == $name' >/dev/null; then
    echo "setting detail response was not expected" >&2
    echo "$setting_detail" >&2
    exit 1
  fi
  setting_update="$(patch_json "/management/settings/$setting_code" \
    "$(jq -nc --arg name "$setting_updated" '{name:$name,value:"6"}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$setting_update" | jq -e --arg name "$setting_updated" '.data.name == $name and .data.value == "6"' >/dev/null; then
    echo "setting update response was not expected" >&2
    echo "$setting_update" >&2
    exit 1
  fi
  sensitive_setting_status="$(http_status POST /management/settings \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $access2" \
    -d "{\"key\":\"integration.api_token_$suffix\",\"name\":\"Sensitive Token\",\"value\":\"real-token-value\",\"scope_type\":\"SYSTEM\"}")"
  if [[ "$sensitive_setting_status" != "400" ]]; then
    echo "sensitive plaintext setting was not rejected: HTTP $sensitive_setting_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi
  setting_disabled="$(patch_json "/management/settings/$setting_code/status" '{"status":"DISABLED"}' -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$setting_disabled" | jq -e '.data.status == "已停用"' >/dev/null; then
    echo "setting disabled response was not expected" >&2
    echo "$setting_disabled" >&2
    exit 1
  fi
  settings_page="$(curl -fsS -G "$API_BASE/management/settings" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "search=$setting_updated")"
  if ! printf '%s' "$settings_page" | jq -e '.data.total == 0' >/dev/null; then
    echo "disabled setting still appeared in enabled settings page" >&2
    echo "$settings_page" >&2
    exit 1
  fi

  local project_renamed application_renamed environment_renamed
  project_renamed="$project_name Updated"
  application_renamed="$application_name Updated"
  environment_renamed="$environment_name Updated"

  local project_detail project_update project_active application_detail application_update application_disabled environment_detail environment_update environment_disabled
  project_detail="$(curl -fsS "$API_BASE/management/projects/$project_code" -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$project_detail" | jq -e --arg name "$project_name" '.data.name == $name' >/dev/null; then
    echo "project detail did not return created project" >&2
    echo "$project_detail" >&2
    exit 1
  fi
  project_update="$(patch_json "/management/projects/$project_code" \
    "$(jq -nc --arg name "$project_renamed" '{name:$name,sensitivity_level:"STRICT"}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$project_update" | jq -e --arg name "$project_renamed" '.data.name == $name' >/dev/null; then
    echo "project update response was not expected" >&2
    echo "$project_update" >&2
    exit 1
  fi
  project_active="$(patch_json "/management/projects/$project_code/status" '{"status":"ACTIVE"}' -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$project_active" | jq -e '.data.status == "进行中"' >/dev/null; then
    echo "project active status response was not expected" >&2
    echo "$project_active" >&2
    exit 1
  fi

  local project_member project_members project_member_removed
  project_member="$(post_json "/management/projects/$project_code/members" \
    "$(jq -nc --arg username "$username" '{username:$username,role_code:"Tester"}')" \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$project_member" | jq -e \
    --arg username "$username" \
    '.data.username == $username and .data.role == "Tester" and .data.member_type == "MEMBER" and .data.status == "启用"' >/dev/null; then
    echo "project member add response was not expected" >&2
    echo "$project_member" >&2
    exit 1
  fi
  project_members="$(curl -fsS -G "$API_BASE/management/projects/$project_code/members" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "search=$username")"
  if ! printf '%s' "$project_members" | jq -e \
    --arg username "$username" \
    '.data.items[] | select(.username == $username and .role == "Tester")' >/dev/null; then
    echo "project members page did not include bound user" >&2
    echo "$project_members" >&2
    exit 1
  fi
  project_member_removed="$(post_json "/management/projects/$project_code/members/$username/remove" '{}' -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$project_member_removed" | jq -e \
    --arg username "$username" \
    '.data.username == $username and .data.status == "已移除"' >/dev/null; then
    echo "project member remove response was not expected" >&2
    echo "$project_member_removed" >&2
    exit 1
  fi

  application_detail="$(curl -fsS "$API_BASE/management/applications/$application_code" -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$application_detail" | jq -e --arg name "$application_name" '.data.name == $name' >/dev/null; then
    echo "application detail did not return created application" >&2
    echo "$application_detail" >&2
    exit 1
  fi
  application_update="$(patch_json "/management/applications/$application_code" \
    "$(jq -nc --arg name "$application_renamed" '{name:$name,app_type:"API"}')" \
    -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$application_update" | jq -e --arg name "$application_renamed" '.data.name == $name and .data.type == "API"' >/dev/null; then
	    echo "application update response was not expected" >&2
	    echo "$application_update" >&2
	    exit 1
	  fi

	  local application_owner application_owners application_owner_removed
	  application_owner="$(post_json "/management/applications/$application_code/owners" \
	    "$(jq -nc --arg username "$username" '{username:$username,role_code:"AppOwner"}')" \
	    -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$application_owner" | jq -e \
	    --arg username "$username" \
	    '.data.username == $username and .data.role == "AppOwner" and .data.scope_type == "APPLICATION" and .data.status == "启用"' >/dev/null; then
	    echo "application owner add response was not expected" >&2
	    echo "$application_owner" >&2
	    exit 1
	  fi
	  application_owners="$(curl -fsS -G "$API_BASE/management/applications/$application_code/owners" \
	    -H "Authorization: Bearer $access2" \
	    --data-urlencode "search=$username")"
	  if ! printf '%s' "$application_owners" | jq -e \
	    --arg username "$username" \
	    '.data.items[] | select(.username == $username and .role == "AppOwner")' >/dev/null; then
	    echo "application owners page did not include bound user" >&2
	    echo "$application_owners" >&2
	    exit 1
	  fi
	  application_owner_removed="$(post_json "/management/applications/$application_code/owners/$username/remove" '{}' -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$application_owner_removed" | jq -e \
	    --arg username "$username" \
	    '.data.username == $username and .data.status == "已移除"' >/dev/null; then
	    echo "application owner remove response was not expected" >&2
	    echo "$application_owner_removed" >&2
	    exit 1
	  fi

	  application_disabled="$(patch_json "/management/applications/$application_code/status" '{"status":"DISABLED"}' -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$application_disabled" | jq -e '.data.status == "已停用"' >/dev/null; then
	    echo "application disabled status response was not expected" >&2
    echo "$application_disabled" >&2
    exit 1
  fi

  environment_detail="$(curl -fsS "$API_BASE/management/environments/$environment_code" -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$environment_detail" | jq -e --arg name "$environment_name" '.data.name == $name' >/dev/null; then
    echo "environment detail did not return created environment" >&2
    echo "$environment_detail" >&2
    exit 1
  fi
  environment_update="$(patch_json "/management/environments/$environment_code" \
    "$(jq -nc --arg name "$environment_renamed" --arg apiBaseUrl "https://api.$environment_code.updated.example.test" '{name:$name,api_base_url:$apiBaseUrl}')" \
    -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$environment_update" | jq -e \
	    --arg name "$environment_renamed" \
	    --arg endpoint "https://api.$environment_code.updated.example.test" \
	    '.data.name == $name and .data.endpoint == $endpoint' >/dev/null; then
	    echo "environment update response was not expected" >&2
	    echo "$environment_update" >&2
	    exit 1
	  fi

	  local environment_user environment_users environment_user_removed
	  environment_user="$(post_json "/management/environments/$environment_code/users" \
	    "$(jq -nc --arg username "$username" '{username:$username,role_code:"Tester"}')" \
	    -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$environment_user" | jq -e \
	    --arg username "$username" \
	    '.data.username == $username and .data.role == "Tester" and .data.scope_type == "ENVIRONMENT" and .data.status == "启用"' >/dev/null; then
	    echo "environment user add response was not expected" >&2
	    echo "$environment_user" >&2
	    exit 1
	  fi
	  environment_users="$(curl -fsS -G "$API_BASE/management/environments/$environment_code/users" \
	    -H "Authorization: Bearer $access2" \
	    --data-urlencode "search=$username")"
	  if ! printf '%s' "$environment_users" | jq -e \
	    --arg username "$username" \
	    '.data.items[] | select(.username == $username and .role == "Tester")' >/dev/null; then
	    echo "environment users page did not include bound user" >&2
	    echo "$environment_users" >&2
	    exit 1
	  fi
	  environment_user_removed="$(post_json "/management/environments/$environment_code/users/$username/remove" '{}' -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$environment_user_removed" | jq -e \
	    --arg username "$username" \
	    '.data.username == $username and .data.status == "已移除"' >/dev/null; then
	    echo "environment user remove response was not expected" >&2
	    echo "$environment_user_removed" >&2
	    exit 1
	  fi

	  environment_disabled="$(patch_json "/management/environments/$environment_code/status" '{"status":"DISABLED"}' -H "Authorization: Bearer $access2")"
	  if ! printf '%s' "$environment_disabled" | jq -e '.data.status == "已停用"' >/dev/null; then
    echo "environment disabled status response was not expected" >&2
    echo "$environment_disabled" >&2
    exit 1
  fi

  assert_audit_event "$access2" "$department_name" "创建部门" "department" "$department_name"
  assert_audit_event "$access2" "$project_renamed" "更新项目" "project" "$project_renamed"
  assert_audit_event "$access2" "$project_renamed" "恢复项目" "project" "$project_renamed"
	  assert_audit_event "$access2" "$username" "添加项目成员" "project_member" "$project_renamed:$username"
	  assert_audit_event "$access2" "$username" "移除项目成员" "project_member" "$project_renamed:$username"
	  assert_audit_event "$access2" "$application_renamed" "更新应用" "application" "$application_renamed"
	  assert_audit_event "$access2" "$username" "添加应用负责人" "application_owner" "$application_renamed:$username"
	  assert_audit_event "$access2" "$username" "移除应用负责人" "application_owner" "$application_renamed:$username"
	  assert_audit_event "$access2" "$application_renamed" "停用应用" "application" "$application_renamed"
  assert_audit_event "$access2" "$environment_renamed" "更新环境" "environment" "$environment_renamed"
	  assert_audit_event "$access2" "$username" "添加环境授权" "environment_user" "$environment_renamed:$username"
	  assert_audit_event "$access2" "$username" "移除环境授权" "environment_user" "$environment_renamed:$username"
	  assert_audit_event "$access2" "$environment_renamed" "停用环境" "environment" "$environment_renamed"
  assert_audit_event "$access2" "$integration_renamed" "更新集成" "integration" "$integration_renamed"
  assert_audit_event "$access2" "$integration_renamed" "停用集成" "integration" "$integration_renamed"

  local assign_role unassign_role
  assign_role="$(post_json "/management/users/$username/roles" '{"role_code":"PlatformAdmin"}' \
    -H "Authorization: Bearer $access2")"
  if ! printf '%s' "$assign_role" | jq -e '.data.role | contains("PlatformAdmin")' >/dev/null; then
    echo "role assignment did not return PlatformAdmin binding" >&2
    echo "$assign_role" >&2
    exit 1
  fi
  unassign_role="$(post_json "/management/users/$username/roles/unassign" '{"role_code":"PlatformAdmin"}' \
    -H "Authorization: Bearer $access2")"
  if printf '%s' "$unassign_role" | jq -e '.data.role | contains("PlatformAdmin")' >/dev/null; then
    echo "role unassignment still returned PlatformAdmin binding" >&2
    echo "$unassign_role" >&2
    exit 1
  fi

  post_json "/management/users/$username/enable" '{}' -H "Authorization: Bearer $access2" >/tmp/wp1-smoke-enable-user.json
  post_json "/management/users/$username/disable" '{}' -H "Authorization: Bearer $access2" >/tmp/wp1-smoke-disable-user.json
  post_json "/management/users/$username/reset-password" '{"new_password":"NewPassword123"}' -H "Authorization: Bearer $access2" >/tmp/wp1-smoke-reset-user.json

  local user_login user_access must_change_password
  user_login="$(post_json /auth/login "{\"username\":\"$username\",\"password\":\"NewPassword123\"}")"
  user_access="$(extract_data_field "$user_login" access_token)"
  must_change_password="$(extract_data_field "$user_login" must_change_password)"
  if [[ "$must_change_password" != "true" ]]; then
    echo "reset-password login did not require password change" >&2
    echo "$user_login" >&2
    exit 1
  fi

  post_json "/management/users/$username/lock" '{}' -H "Authorization: Bearer $access2" >/tmp/wp1-smoke-lock-user.json
  local locked_user_access_status locked_user_login_status
  locked_user_access_status="$(http_status GET /auth/me -H "Authorization: Bearer $user_access")"
  if [[ "$locked_user_access_status" != "401" && "$locked_user_access_status" != "403" ]]; then
    echo "user access token still accepted after account lock: HTTP $locked_user_access_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi
  locked_user_login_status="$(http_status POST /auth/login \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"NewPassword123\"}")"
  if [[ "$locked_user_login_status" != "401" ]]; then
    echo "locked user login was not rejected: HTTP $locked_user_login_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi
  local failed_login_audit
  failed_login_audit="$(curl -fsS -G "$API_BASE/management/audit-logs" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "search=$username" \
    --data-urlencode "action=登录失败" \
    --data-urlencode "result=FAILED" \
    --data-urlencode "start_time=2000-01-01T00:00:00Z" \
    --data-urlencode "end_time=2999-01-01T00:00:00Z")"
  if ! printf '%s' "$failed_login_audit" | jq -e '.data.items[] | select(.action == "登录失败" and .target == "'"$username"'" and .result == "失败")' >/dev/null; then
    echo "failed login audit log was not found" >&2
    echo "$failed_login_audit" >&2
    exit 1
  fi
  post_json "/management/users/$username/unlock" '{}' -H "Authorization: Bearer $access2" >/tmp/wp1-smoke-unlock-user.json
  user_login="$(post_json /auth/login "{\"username\":\"$username\",\"password\":\"NewPassword123\"}")"
  user_access="$(extract_data_field "$user_login" access_token)"
  must_change_password="$(extract_data_field "$user_login" must_change_password)"
  if [[ "$must_change_password" != "true" ]]; then
    echo "unlocked reset-password login did not require password change" >&2
    echo "$user_login" >&2
    exit 1
  fi

  post_json /auth/change-password '{"old_password":"NewPassword123","new_password":"ChangedPassword123"}' \
    -H "Authorization: Bearer $user_access" >/tmp/wp1-smoke-change-password.json
  local old_user_access_status changed_user_login changed_user_access changed_must_change_password
  old_user_access_status="$(http_status GET /auth/me -H "Authorization: Bearer $user_access")"
  if [[ "$old_user_access_status" != "401" && "$old_user_access_status" != "403" ]]; then
    echo "user access token still accepted after password change: HTTP $old_user_access_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi
  changed_user_login="$(post_json /auth/login "{\"username\":\"$username\",\"password\":\"ChangedPassword123\"}")"
  changed_user_access="$(extract_data_field "$changed_user_login" access_token)"
  changed_must_change_password="$(extract_data_field "$changed_user_login" must_change_password)"
  if [[ "$changed_must_change_password" != "false" ]]; then
    echo "changed password login still requires password change" >&2
    echo "$changed_user_login" >&2
    exit 1
  fi

  local denied_status denied_audit
  denied_status="$(http_status GET /management/users -H "Authorization: Bearer $changed_user_access")"
  if [[ "$denied_status" != "403" ]]; then
    echo "low-privilege user was not denied from management users: HTTP $denied_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi

  denied_audit="$(curl -fsS -G "$API_BASE/management/audit-logs" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "search=user:read" \
    --data-urlencode "action=权限校验" \
    --data-urlencode "result=DENIED" \
    --data-urlencode "start_time=2000-01-01T00:00:00Z" \
    --data-urlencode "end_time=2999-01-01T00:00:00Z")"
  if ! printf '%s' "$denied_audit" | jq -e '.data.items[] | select(.action == "权限校验" and .target == "user:read" and .result == "拒绝")' >/dev/null; then
    echo "permission denied audit log was not found" >&2
    echo "$denied_audit" >&2
    exit 1
  fi

  local password_audit
  password_audit="$(curl -fsS -G "$API_BASE/management/audit-logs" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "search=修改密码" \
    --data-urlencode "actor=$username" \
    --data-urlencode "action=修改密码" \
    --data-urlencode "resource_type=user" \
    --data-urlencode "result=SUCCESS" \
    --data-urlencode "start_time=2000-01-01T00:00:00Z" \
    --data-urlencode "end_time=2999-01-01T00:00:00Z")"
  if ! printf '%s' "$password_audit" | jq -e '.data.items[] | select(.action == "修改密码" and .target == "'"$username"'")' >/dev/null; then
    echo "password change audit log was not found" >&2
    echo "$password_audit" >&2
    exit 1
  fi

  assert_audit_event "$access2" "$username" "锁定用户" "user" "$username"
  assert_audit_event "$access2" "$username" "解锁用户" "user" "$username"

  local impossible_password_audit
  impossible_password_audit="$(curl -fsS -G "$API_BASE/management/audit-logs" \
    -H "Authorization: Bearer $access2" \
    --data-urlencode "action=修改密码" \
    --data-urlencode "resource_type=user" \
    --data-urlencode "result=DENIED" \
    --data-urlencode "end_time=2000-01-01T00:00:00Z")"
  if ! printf '%s' "$impossible_password_audit" | jq -e '.data.total == 0' >/dev/null; then
    echo "audit log structured filters did not exclude mismatched result" >&2
    echo "$impossible_password_audit" >&2
    exit 1
  fi

  local logout_status after_logout_status
  logout_status="$(http_status POST /auth/logout \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $access2" \
    -d '{"reason":"wp1-db-profile-smoke"}')"
  after_logout_status="$(http_status GET /auth/me -H "Authorization: Bearer $access2")"
  if [[ "$logout_status" != "200" ]]; then
    echo "logout failed with HTTP $logout_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi
  if [[ "$after_logout_status" != "401" && "$after_logout_status" != "403" ]]; then
    echo "access token still accepted after logout: HTTP $after_logout_status" >&2
    cat /tmp/wp1-smoke-response.json >&2
    exit 1
  fi

  echo "WP1 db profile smoke passed: management objects, audit filters, session rotation, and account lifecycle verified for $username."
}

main "$@"
