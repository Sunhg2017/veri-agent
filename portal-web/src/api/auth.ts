import { requestJson, setAuthToken, setRefreshToken, setSessionId } from './client';

export interface LoginPayload {
  username: string;
  password: string;
}

export interface ChangePasswordPayload {
  old_password: string;
  new_password: string;
}

export interface CurrentUser {
  user_id: string;
  username: string;
  display_name: string;
  email?: string;
  must_change_password: boolean;
  roles: string[];
  permissions?: string[];
}

export interface LoginResult extends CurrentUser {
  access_token: string;
  refresh_token: string;
  session_id: string;
  token_type: string;
  expires_at: string;
}

export async function login(payload: LoginPayload) {
  const response = await requestJson<LoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  setAuthToken(response.data.access_token);
  setRefreshToken(response.data.refresh_token);
  setSessionId(response.data.session_id);
  return response;
}

export function fetchCurrentUser() {
  return requestJson<CurrentUser>('/api/v1/auth/me');
}

export function logout() {
  return requestJson<{ revoked: boolean; session_id: string }>('/api/v1/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ reason: 'user logout' })
  });
}

export function changePassword(payload: ChangePasswordPayload) {
  return requestJson<{ password_changed: boolean; session_invalidated: boolean; user_id: string }>('/api/v1/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}
